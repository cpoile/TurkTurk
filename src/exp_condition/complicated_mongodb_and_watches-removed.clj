;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; these were removed because it was over-engineered and confusing.
;; This had read initial conditions from a file, but then overwrote them from the mongodb
;; and then added watches to log changes in the clojure structure back to the mongodb
;;
;; at the end there are the replaced core functions that used to work, but now don't

;; watches -- was used to update back to mongodb
;; instead, just use mongo, or just use STM and write-persist to a flat file every little while.
;;
(defn list-watches
  "For each ref in seq list its watches."
  [smap]
  (->> (for [[k v] smap
             :when (instance? clojure.lang.Ref v)
             wk (keys (.getWatches v))]
         [k wk])
       (group-by first)
       (map-map #(map second %))))

(defn remove-watches
  "For each ref in seq remove its watches."
  [smap]
  (for [[k v] smap
        :when (instance? clojure.lang.Ref v)
        wk (keys (.getWatches v))]
    (remove-watch v wk)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MongoDB functions

(defn mongo-to-map
  "Take smap, return map of :cond condmap. Problem is, mongo turns set
  into a vector, and turns keys into _ids."
  [mongo-smap]
  (let [rfn (fn [m-target m-orig]
              (as-> m-orig m
                    (assoc m :participants (set (:participants m)))
                    (dissoc m :_id)
                    (hash-map (keyword (:_id m-orig)) m)
                    (deep-merge m-target m)))]
    (reduce rfn {} mongo-smap)))

(defn merge-conditions
  "take the initial conditions, the keys coming from Mongo will
  overwrite the initial conditions. Turn participants vector (mongo)
  into a set, then deep merge the two."
  [old-init-conds new-mongo-conds]
  (let [corr-mongo (mongo-to-map new-mongo-conds)]
    (deep-merge old-init-conds corr-mongo))
  ;; (let [all-ids (into (set (keys old-init-conds)) (keys new-mongo-conds))]
  ;;   (vec
  ;;    (for [id all-ids]
  ;;      (let [old-cond (get-by old-init-conds :_id id)
  ;;            new-cond (get-by new-mongo-conds :_id id)
  ;;            merged (merge old-cond new-cond)]
  ;;        (assoc merged :participants
  ;;               (into (:participants old-cond #{}) ;; default to an empty set
  ;;                     (:participants new-cond)))))))
  )

(defn merge-smap
  "m2 takes precendence over m1"
  [m1 m2 id-key]
  (let [rfn (fn [m-target m-orig]
              (as-> m-orig m
                    (dissoc m :_id)
                    (hash-map (id-key m-orig) m)
                    (deep-merge m-target m)))]
    (as-> (reduce rfn {} m1) m
          (reduce rfn m m2)
          (vals m)
          (if m m []))))
;; old merge-map, just in case:
;;
;; (let [m1-no-_id (map remove-_id m1)
;;       m2-no-_id (map remove-_id m2)]
;;   (->> (into m2-no-_id m1-no-_id)
;;        (group-by :part-id)
;;        vals
;;        (map (partial apply deep-merge))))

(defn write-to-db-conditions
  [^com.mongodb.DBApiLayer db conds]
  (mg/set-db! db)
  (doseq [oid (keys conds)]
    (mc/update "conditions" {:_id oid} (assoc (oid conds) :_id oid) :upsert true))
  ;; finally, return the db (agent's DBApiLayer) so it will be put back into the agent.
  db)

(defn write-to-db-coll
  [^com.mongodb.DBApiLayer db coll-name old new id-key]
  ;; TODO: this is poorly designed on their part, we should pass in the db we want to use.
  ;; what if another thread calls set-db! ??
  (mg/set-db! db)
  (let [new-ids (into #{} (map id-key new))
        old-ids (into #{} (map id-key old))
        to-remove (remove new-ids old-ids)
        to-add (remove old-ids new-ids)]
    (doseq [oid to-remove]
      (mc/remove coll-name {:_id oid}))
    (doseq [oid to-add]
      (let [m (get-by new id-key oid)]
        (mc/update coll-name {:_id oid} (assoc m :_id oid) :upsert true))))
  ;; finally, return the db (agent's DBApiLayer) so it will be put back into the agent.
  db)

(defn write-to-db-log
  [^com.mongodb.DBApiLayer db log-name entry]
  (mg/set-db! db)
  (mc/insert log-name {:time (tl/local-now) :entry entry})
  db)

(defn write
  [^java.io.Writer w & content]
  (doseq [x (interpose " " content)]
    (.write w (str x)))
  (doto w
    (.write "\n")
    .flush))

(defn log-error
  [entry]
  (send-off mongo-agent write-to-db-log "accessErrorLog" entry)
  ;;(send-off access-error-log write entry)
  ;; (binding [*out* myout]
  ;;   (pprint entry))
  )

(defn log-reference
  [reference db-agent coll-name id-key]
  (add-watch reference :db-log
             (fn [_ reference old new]
               (send-off db-agent write-to-db-coll coll-name old new id-key))))

(defn setup-mongodb
  "Logs into mongo. Grabs conditions, sets up current conditions,
  reads old pending, expired and finished."
  [{:keys [db-name registered conditions pending expired finished]}]
  (mg/connect!)
  (def mongo-agent (agent (mg/get-db db-name)))
  (mg/set-db! (mg/get-db db-name))
  (dosync
   (let [mongo-conds (mc/find-maps "conditions")]
     (alter conditions merge-conditions mongo-conds)))

  (let [refs-to-watch
        [[registered "registered"] [pending "pending"] [expired "expired"] [finished "finished"]]]
    (doseq [ref-name refs-to-watch]
      (let [r (first ref-name)
            name (second ref-name)]
        (dosync
         (let [mongo (mc/find-maps name)
               mongo-fixed (map #(assoc % :cond-id (keyword (:cond-id %))) mongo)]
           (alter r merge-smap mongo-fixed :pk)))))
    ;;
    ;; now set the watches to log changes
    (add-watch conditions :db-log
               (fn [_ reference old new]
                 (send-off mongo-agent write-to-db-conditions new)))
    (doseq [ref-name refs-to-watch]
      (log-reference (first ref-name) mongo-agent (second ref-name) :pk))))

(defn dump-into-mongo
  "dump this ref into mongo, using coll collection and k as the unique
  key. Obviously we shouldn't do this, but it's quick and dirty for
  now."
  [db coll-name coll k]
  (doseq [rcd coll]
    (mg/set-db! (mg/get-db db))
    (mc/update coll-name {k (k rcd)} rcd :upsert true)))

(defn make-keyword
  "given a coll of maps, turn k's value into a keyword"
  [coll k]
  (map #(assoc % k (keyword (k %))) coll))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; replaced core functions that used to work but now don't
(declare global-state)
(declare mongo-agent)
;;(def access-error-log (agent (io/writer "access-error.log" :append true)))

(defn gen-clean-state
  "Create a base global state. Must set a db-name to prevent
  clobbering one by accident in testing."
  [db-name]
  {:conditions (ref initial-conditions)
   :active-conds active-cond-list
   :pk-to-id-perm (ref [])
   :registered (ref [])
   :pending (ref [])
   :expired (ref [])
   :finished (ref [])
   :blacklist (ref [])
   :db-name db-name
   })

(defn get-next-cond-and-inc
  "Returns next condition. SIDE-EFFECT: the conditions ref passed in
  is incremented."
  [{:keys [conditions active-conds]}]
  (dosync
   (let [avail-conds (select-keys @conditions
                                  (for [[k v] @conditions
                                        :when (active-conds k)] k))
         k (get-lowest-key avail-conds :count)]
     (alter conditions update-in [k :count] inc)
     k)))

(defn part-state
  "return a description of where the participant is in the experiment process."
  [id]
  (cond (some #{k} (map keyfn @expired)) :expired
        (some #{k} (map keyfn @finished)) :finished
        (some #{k} (map keyfn @pending)) :pending
        (some #{k} (map keyfn @registered)) :registered
        :else :new))

;; can be done with a "some"
(defn auth-pk?
  [pk smap]
  (first (filter #(= {:pk pk}
                     (select-keys % [:pk]))
                 smap)))

(defn finish-participant
  "participant has finished, record their info in the finished coll so
  we can pay them. Return a finished code they will use."
  [pk {:keys [registered pending finished pk-to-id-perm]}]
  ;; don't need the urlkey anymore
  (let [part-id (:part-id (get-by @pk-to-id-perm :pk pk))
        fin-code (str (ObjectId.))
        fin (-> (get-by @pending :pk pk)
                (dissoc :urlkey)
                (assoc :part-id part-id)
                (assoc :fin-code fin-code))]
    (dosync
     ;; remove all pendings/registered assoc with that id
     (alter pending (partial remove #(= pk (:pk %))))
     (alter registered (partial remove #(= pk (:pk %))))
     (alter finished conj fin))
    {:code fin-code}))

;; from routes:
  ;; old method, using json -- not needed, if we just redirect to the next page.
  ;; (GET "/exp-condition/" [pk jsonp :as z]
  ;;      (if (and pk
  ;;               (or (auth-pk? pk @(:pending global-state))
  ;;                   (auth-pk? pk @(:registered global-state))))
  ;;        (-> (condp = (part-state pk :pk global-state)
  ;;              :registered (assign-condition pk global-state)
  ;;              :pending (get-existing-cond pk global-state)
  ;;              "false")
  ;;            (wrap-map-to-jsonp jsonp))
  ;;        (not-found-resp z jsonp)))
;;
;; (GET "/exp-condition/" [pk :as z]
;;        ;; if they are pending, send them back to their condition page.
;;        ;; if only registered, assign them to their condition.
;;        (if (and pk
;;                 (or (auth-pk? pk @(:pending global-state))
;;                     (auth-pk? pk @(:registered global-state))))
;;          (-> (condp = (part-state pk :pk global-state)
;;                :registered (assign-condition pk global-state)
;;                :pending (get-existing-cond pk global-state))
;;              :site
;;              redirect)
;;          (not-found-resp z)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; removed page functions

(defn wrap-cross-origin-json
  [resp]
  (-> resp
      (generate-string)
      (response)
      (wrap-cross-origin)
      (content-type "application/json")))
