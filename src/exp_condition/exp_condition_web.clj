;; start in production mode:
;; nohup lein trampoline with-profile user,production run &
;; start in dev mode:
;; nohup lein trampoline with-profile user,dev run &
;;
;; nohup prevents the process from exiting when the shell stops.
;; if you forget to use it, use:
;; jobs -l, then disown -h jobnumber
;;
;; to see the parent java process (if you exited and relogged in) use:
;; pstree -sg
;;
;; for iptables: (first is the http, next is repl) --- do we need this??
;;sudo iptables -A TCP -p tcp -m tcp --dport 5000 -j ACCEPT
;;sudo iptables -A TCP -p tcp -m tcp --dport 50505 -j ACCEPT
;;
;; I don't think we need these...
;;sudo iptables -A OUTPUT -p tcp -m tcp --sport 5000 -m state --state RELATED,ESTABLISHED -j ACCEPT
;;sudo iptables -A OUTPUT -p tcp -m tcp --sport 50505 -m state --state RELATED,ESTABLISHED -j ACCEPT
;;
;; need to put AWS access key and everything else in profiles.
;;
;; to get HIT data from aws, code is in aws_rest_data.clj

(ns exp-condition.exp-condition-web
  (:require [clojure.pprint :refer [pprint]]
            [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.handler :refer [site api]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [ring.util.response :refer [response content-type redirect]]
            [ring.middleware.stacktrace :as stacktrace]
            [ring.middleware.session :as session]
            [ring.middleware.session.cookie :as cookie]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.basic-authentication :as basic]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [environ.core :refer [env]]
            [monger [core :as mg] [collection :as mc] [db :as db] [operators :refer :all]]
            [monger.joda-time]
            [monger.json]
            [inspector.middleware :refer [wrap-inspect]]
            [cheshire.core :refer :all]
            [clj-time [core :as t] [local :as tl] [coerce :as tco] [format :as tf]]
            [clojure.edn :as edn]
            [taoensso.timbre :as tlog])
  (:use [clojure.tools.nrepl.server :only (start-server stop-server default-handler)]
        [monger.operators]
        [clojure.tools.trace])
  (:import [com.mongodb MongoOptions ServerAddress DB WriteConcern]
           [org.bson.types ObjectId]
           [org.apache.commons.codec.binary Base64]
           [java.util UUID]))

(def repl-print
  (constantly nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Settings:
;;
(def intro-url "https://fluidsurveys.usask.ca/s/f2015-intro?pk=")
;; in s2014_sess4, we used this to make sure MTurks didn't do the exp twice.
;;(def past-finished-colls ["s2014_sess1_finished"])
(def past-finished-colls [])
(tlog/set-config! [:appenders :spit :enabled?] true)
(tlog/set-config! [:shared-appender-config :spit-filename] "error.log")


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Globals (for this namespace):

;; the mongodb connection -- will be initialized in the
;; setup function, called by -main.
(declare conn)

;; the mongodb db
(declare db)

;; this will hold the session-name which is read from the intial-conditions.edn file.
(declare session-name)

;; this will hold the active conditions, which is read from the active-cond-list.edn file.
(declare active-cond-list)
(def db-name "f2015_powresp")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; helper fns.
;;
(defn str-to-b64-str
  ;; not used.
  "Need to url encode + / =  into - _ ."
  [original]
  (String. (Base64/encodeBase64URLSafeString (.getBytes original))))

(defn b64-str-to-str
  ;; not used.
  [x]
  (String. (Base64/decodeBase64 (.getBytes x))))

(defn get-lowest-key
  [maps key]
  (apply min-key #(key (% maps)) (keys maps)))

(defn get-by
  [smap k v]
  (first (filter #(= v (k %)) smap)))

(defn contains-key?
  [smap k v]
  (get-by smap k v))

(defn map-map
  [f m]
  (into (empty m)
        (for [[k v] m]
          [k (f v)])))

(defn deep-merge
  "Deep merge two maps / sets. If values are not all maps, if values are
  sets then union them, else the last one wins."
  [& values]
  (cond (every? map? values) (apply merge-with deep-merge values)
        (every? set? values) (apply clojure.set/union values)
        :else (last values)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; state
;; TODO: turn this into an initialized state map

(defn load-conditions-into-db
  [init-conds sess]
  (mc/insert-batch @db sess (:conditions init-conds))
  (mc/find-maps @db sess))

(defn load-session-name
  []
  (def session-name
    (-> "initial-conditions.edn" io/resource slurp edn/read-string :experiment-session))
  session-name)

(defn load-conditions-if-absent
  "TODO: if we want to add a condition in the middle, change this
  function to go condition-by-condition through each and check if that
  condition is in the db. If not, add it with the record from the .edn
  file."
  []
  (let [init-conds (-> "initial-conditions.edn" io/resource slurp edn/read-string)
        coll (str (load-session-name) "_conditions")]
    (if (mc/exists? @db coll)
      (mc/find-maps @db coll)
      (load-conditions-into-db init-conds coll))))

(defn conditions
  []
  (let [coll (str session-name "_conditions")]
    (mc/find-maps @db coll)))


(defn load-active-cond-list
  "Change the active-cond-list.edn file and call this fn again if you
  want to change which conditions participants are sent to."
  []
  (def active-cond-list
    (-> "active-cond-list.edn" io/resource slurp edn/read-string)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; page utilities

(defn wrap-error-page [handler]
  (fn [req]
    (try (handler req)
         (catch Exception e
           {:status  500
            :headers {"Content-Type" "text/html"}
            :body    (slurp (io/resource "500.html"))}))))

(defn wrap-pprint
  [resp]
  (pprint resp)
  resp)

(defn wrap-cross-origin [resp]
  (assoc-in resp [:headers "Access-Control-Allow-Origin"] "http://fluidsurveys.usask.ca https://fluidsurveys.usask.ca http://black-sea.usask.ca"))

(defn- wrap-map-to-jsonp [resp callback]
  (-> resp
      (generate-string)
      (response)
      (wrap-cross-origin)
      (content-type "application/javascript")
      (update-in [:body] #(str callback "(" % ");"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; logic fns
;;

;; we need to keep track of:
;;  - req a condition,
;;  - inc that condition (provisionally, actually)
;;  - record that condition, requestor's MTurk id, time of request in a Pending coll
;;  - every ten minutes check the pending coll and remove anyone who's taken 2 hours or more
;;    - record their id in a blacklist
;;  - receive finished confirmation
;;    - move id into finished list, not-payed yet
;;  - check if id has blacklisted or played already, send to "sorry" page

(defn- auth-valid?
  [pass]
  (= pass "iTXljNzg1VG04Zk9Wc1JsanpQQTlwOkl5T3ZTNzhFWVdpRmR3S0c0eHpHT"))

(defn- auth-valid-fin?
  [pass]
  (= pass "X2Q1BNXOFXCsi7RsDOYeHH44kLbiTDf6Wo8d"))

(defn get-next-cond-and-inc
  "Returns next condition. SIDE-EFFECT: the database is incremented"
  []
  (let [poss-conds (filter #(active-cond-list (:condition %)) (conditions))
        cond (apply min-key :count poss-conds)
        coll (str session-name "_conditions")]
    (mc/update-by-id @db coll (:_id cond) {$inc {:count 1}})
    cond))

(defn get-pk
  "Return the player-key assoc with this id. If there isn't one, returns nil."
  [id]
  (let [coll-perm (str session-name "_id_to_pk_permanent")]
    (:pk (mc/find-one-as-map @db coll-perm {:part-id id}))))

(defn get-id
  "Return the player-id assoc with this pk. If there isn't one, returns nil."
  [pk]
  (let [coll-perm (str session-name "_id_to_pk_permanent")]
    (:part-id (mc/find-one-as-map @db coll-perm {:pk pk}))))

(defn record-pending
  "Record that condition, requestor's MTurk id, time the request
  expires in a Pending coll"
  [pk cond expires-in]
  (let [rcd {:condition cond :started (t/now) :pk pk}
        coll (str session-name "_pending")]
    (mc/save @db coll rcd)
    rcd))

(defn pending?
  "Is this pk pending in some condition?"
  [pk]
  (let [coll (str session-name "_pending")]
    (mc/any? @db coll {:pk pk})))

(defn finished?
  "Is this pk finished this CURRENT session? If so, return the {:code code}. If not, nil."
  [pk]
  (let [coll (str session-name "_finished")]
    (if-let [fin-rcd (mc/find-one-as-map @db coll {:pk pk})]
      (select-keys fin-rcd [:code])
      nil)))

(defn finished-old-session?
  "go through list of old sessions (in settings section at start of
  file) and if this id has played before, return something. nil
  otherwise."
  [id]
  (let [check-if-fin (fn [res collname]
                       (if-let [fin-rcd (mc/find-one-as-map @db collname {:part-id id})]
                         (conj res true)
                         res))]
    (not-empty (reduce check-if-fin [] past-finished-colls))))

(defn assign-condition
  [pk]
  (let [cond (get-next-cond-and-inc)
        cond-name (:condition cond)
        part-id (get-id pk)
        coll (str session-name "_conditions")]
    (mc/update @db coll {:condition cond-name} {$push {:participants pk}})
    (record-pending pk cond-name (t/hours 1))
    cond))

(defn get-existing-cond
  "If the player is in a condition (they are :pending), return the
  first condition record. If not, nil."
  [pk]
  (let [coll (str session-name "_pending")]
    (if-let [pend (mc/find-one-as-map @db coll {:pk pk})]
      (let [cond-name (:condition pend)
            cond-rcds (filter #(= cond-name (:condition %)) (conditions))]
        (first cond-rcds))
      nil)))

(defn part-in-condition-set?
  "a different way of doing the above, without relying on pending.
  returns condition id if they are in it."
  [pk]
  (-> (mc/find-one-as-map @db (str session-name "_conditions")
                          {:participants {$in [pk]}})
      :condition))

(defn parts-completed
  "return the participants who have actually completed the condition"
  [cond]
  (mc/find-maps @db (str session-name "_finished") {:condition cond}))

(defn parts-started-not-completed []
  (mc/find-maps @db (str session-name "_pending") {}))

(defn report-conditions
  "What are the finished and pending counts for each condition?"
  []
  (map (fn [x] {:condition (:condition x)
                :started   (:count x)
                :completed (count (parts-completed (:condition x)))})
       (mc/find-maps @db (str session-name "_conditions"))))

(defn unfinished-older-than-mins [mins]
  (for [rec (filter #(t/after? (tl/local-now) (t/plus (:started %) (t/minutes mins)))
                    (parts-started-not-completed))]
    (assoc rec :part-id (get-id (:pk rec)))))

(defn remove-participant-from-everything
  "Careful -- This will remove all trace of the id/pk from all collections."
  [pk]
  (let [coll-pending (str session-name "_pending")
        coll-state (str session-name "_state")
        coll-fin (str session-name "_finished")
        coll-conds (str session-name "_conditions")
        ;;coll-id-to-pk (str session-name "_id_to_pk_permanent")
        cond (part-in-condition-set? pk)]
    (mc/update @db coll-state {:state "registered"}
               {$pull {:list pk}})
    (mc/update @db coll-conds {:condition cond}
               {$pull {:participants pk}})
    (mc/update @db coll-conds {:condition cond}
               {$inc {:count -1}})
    (mc/remove @db coll-pending {:pk pk})
    ;; don't remove permanent id-to-pk -- it's permanent, just incase
    ;;(mc/remove @db coll-id-to-pk {:pk pk})
    (mc/remove @db coll-fin {:pk pk})
    (str "Removed " pk)))

(defn remove-older-than-mins
  "Careful -- This will remove all trace of the id/pk from all collections."
  [mins]
  (let [expired (unfinished-older-than-mins mins)
        num-exp (count expired)]
    (for [pk (map :pk expired)]
      (remove-participant-from-everything pk))))

(defn remove-these-part-ids-from-everything
  "CAREFUL -- This is for removing test ids. Give it a list of part-ids."
  [part-ids]
  (for [pk (map get-pk part-ids)]
    (remove-participant-from-everything pk)))

(defn finish-participant
  "participant has finished, record their info in the finished coll so
  we can pay them. Return a finished code they will use."
  [pk]
  ;; don't need the urlkey anymore
  (if-let [part-id (get-id pk)]
    (let [code (second (re-find #"([A-Za-z0-9]*)-" (str (UUID/randomUUID))))
          coll-pending (str session-name "_pending")
          coll-state (str session-name "_state")
          coll-fin (str session-name "_finished")
          pend (mc/find-one-as-map @db coll-pending {:pk pk})
          fin-rcd {:pk        pk
                   :part-id   part-id
                   :code      code
                   :condition (:condition pend)}]
      (mc/remove @db coll-pending {:pk pk})
      (mc/update @db coll-state {:state "registered"} {$pull {:list pk}})
      (mc/save @db coll-fin fin-rcd)
      {:code code})
    nil))

(defn part-state
  "Return a description of where the participant is in the experiment process."
  [pk-or-id]
  (let [pkid (or (get-pk pk-or-id) pk-or-id)
        coll (str session-name "_state")]
    ;; TODO: make these like pending?
    (cond (mc/any? @db coll {:state "expired" :list {$in [pkid]}}) :expired
          (finished? pkid) :finished
          (finished-old-session? pkid) :finished
          (pending? pkid) :pending
          (mc/any? @db coll {:state "registered" :list {$in [pkid]}}) :registered
          :else :new)))

(defn make-finished-already-page
  []
  (str "<HTML> <BODY> <h1>It looks like you've taken this survey and simulation already,
  or the earlier one in June.</h1> Sorry, the experiment will be invalidated if we allow
  participants to play more than once. Thank you for your understanding! </BODY></HTML>"))

(defn register-and-get-intro-url
  "Assign the user a player key (pk) and assoc that pk with their
  original id, then use the pk from now on. We are doing this just in
  case the id is easily identifiable (they'll see it in the url and
  that might affect their behavior), or we want to give them a new pk
  and let them play again."
  [id]
  (let [pk (str (ObjectId.))
        coll-perm (str session-name "_id_to_pk_permanent")
        coll-state (str session-name "_state")]
    (mc/save @db coll-perm {:part-id id :pk pk})
    (mc/update @db coll-state {:state "registered"} {$push {:list pk}} {:upsert true})
    (str intro-url pk)))

(defn register
  "Assign the user a player key (pk) and assoc that pk with their
  original id, then use the pk from now on. We are doing this just in
  case the id is easily identifiable (they'll see it in the url and
  that might affect their behavior), or we want to give them a new pk
  and let them play again."
  [id]
  (let [pk (str (ObjectId.))
        coll-perm (str session-name "_id_to_pk_permanent")
        coll-state (str session-name "_state")]
    (mc/save @db coll-perm {:part-id id :pk pk})
    (mc/update @db coll-state {:state "registered"} {$push {:list pk}} {:upsert true})
    pk))

(defn make-site
  [pend-m {:keys [conditions]}]
  (let [site-m ((:cond-id pend-m) @conditions)]
    {:site (str (:site site-m) "?pk=" (:pk pend-m))}))

(defn not-found-resp
  [& z]
  (tlog/debug "404 Not Found. Params: " z)
  (route/not-found
    (slurp (io/resource "404.html"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Conditions server
;;

(defroutes routes
           (GET "/" []
             {:status  200
              :headers {"Content-Type" "text/plain"}
              :body    "Hello."})
           ;; valid-worker will see if this person is valid (hasn't played
           ;; yet), and sends them to the condition they belong in
           ;; was used in mechanical turk experiments, s2014
           ;(GET "/valid-worker/" [basic id :as z]
           ;  (if (and (auth-valid? basic) id)
           ;    (-> (case (part-state id)
           ;          :new (-> (register-and-get-intro-url id)
           ;                   redirect)
           ;          :registered (-> (str intro-url (get-pk id))
           ;                          redirect)
           ;          :pending (-> (get-existing-cond (get-pk id))
           ;                       :site
           ;                       (str "?pk=" (get-pk id))
           ;                       redirect)
           ;          :finished (-> (make-finished-already-page)
           ;                        response)
           ;          "false"))
           ;    (not-found-resp z)))

           ;; was used in MTurk experiments, s2014
           ;(GET "/exp-condition/" [basic pk :as z]
           ;  ;; if they are pending, send them back to their condition page.
           ;  ;; if only registered, assign them to their condition.
           ;  (if (and (auth-valid? basic) pk)
           ;    (if-let [cond-rcd (case (part-state pk)
           ;                        :registered (assign-condition pk)
           ;                        :pending (get-existing-cond pk)
           ;                        nil)]
           ;      (redirect (str (:site cond-rcd) "?pk=" pk))
           ;      (not-found-resp z))
           ;    (not-found-resp z)))

           (GET "/exp-condition/" [basic id :as z]
             ;; new, assign them to their condition and redirect
             (if (and (auth-valid? basic) id)
               (if-let [[cond-rcd pk] (case (part-state id)
                                        :new [(assign-condition (register id)) (get-pk id)]
                                        :pending [(get-existing-cond (get-pk id)) (get-pk id)]
                                        nil)]
                 (redirect (str (:site cond-rcd) "?pk=" pk))
                 (not-found-resp z))
               (not-found-resp z)))

           ;; TODO: this might be spoofable if they guess the finished-exp url.
           ;; not worth the time to guard against it, considering effort/risk
           ;;
           ;; this is also used for f2015 to record the finished state, but we don't need
           ;; the completion code
           (GET "/finished-exp/" [basic pk jsonp :as z]
             (if (and (auth-valid-fin? basic)
                      pk)
               (case (part-state pk)
                 :pending (-> (finish-participant pk)
                              (wrap-map-to-jsonp jsonp))
                 :finished (-> (finished? pk)
                               (wrap-map-to-jsonp jsonp)))
               (not-found-resp z)))

           (GET "/test/" [jsonp & params :as z]
             (-> {:message "Hi there." :params params}
                 (generate-string)
                 (response)
                 ;;(wrap-cross-origin)
                 ;;(content-type "application/javascript")
                 (update-in [:body] #(str jsonp "(" % ");"))))
           (ANY "*" [:as z]
             (route/not-found
               (slurp (io/resource "404.html")))))



(def app (-> #'routes
             ((if (env :production)
                wrap-error-page
                stacktrace/wrap-stacktrace))))

(defn server-startup
  "do our initial house-keeping. Initialize global state, start db, etc."
  []
  ;; load up the database
  (def conn (atom (mg/connect)))
  (def db (atom (mg/get-db @conn db-name)))

  ;; load up the conditions into the db
  (load-conditions-if-absent)

  ;; load active condition list into the def: active-cond-list
  ;;
  ;; CHANGE the active-cond-list.edn file if you want to change which
  ;; conditions they are sent to.
  (load-active-cond-list)


  ;; removed complexity
  ;;(def global-state (gen-clean-state "testing"))
  ;;(setup-mongodb global-state)
  )

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 8082))]
    (jetty/run-jetty (api #'app)
                     {:port port :join? false}))

  ;; STARTUP FOR THE CONDITIONS LOGIC.
  (server-startup))

;; For interactive development:
;; (.stop server)
;; (def server (-main))
;; For repl:

(defonce repl-server (start-server :port 60606
                                   :handler (default-handler #'wrap-inspect)))
