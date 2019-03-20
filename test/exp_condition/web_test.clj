(ns exp-condition.web-test
  (:require [clojure.test :refer :all]
            [exp-condition.web :refer :all]
            [monger.collection :as mc]
            [monger.core :as mg]
            [monger.db :as db]
            [monger.joda-time]
            [clj-time [core :as t] [local :as tl] [coerce :as tc] [format :as tf]]))

(declare global-state)

(defn state-fixture
  [f]
  (try
    (println "setting up mongo")
    (mg/connect!)
    (mg/set-db! (mg/get-db "test1"))
    (f)
    (finally
      (mc/remove "test1"))))

(use-fixtures :each state-fixture)

(deftest merge-conditions-test
  (let [init {:mt-dg-pre {:count 0
                          :participants #{"zeg"}
                          :site "http://fluidsurveys.usask.ca/s/w2014mtdgpr1"}}
        db-cond [{:_id :mt-dg-pre
                  :count 3
                  :participants ["hgs" "gew"]
                  :site "http://fluidsurveys.usask.ca/s/w2014mtdgpr1"}]]
    (is (= (merge-conditions init db-cond)
           {:mt-dg-pre {:count 3
                        :participants #{"hgs" "gew" "zeg"}
                        :site "http://fluidsurveys.usask.ca/s/w2014mtdgpr1"}}))))

(deftest test-merge-conditions2
  (let [init {:mt-dg-post {:count 0
                           :participants #{"ho"}
                           :site "http://fluidsurveys.usask.ca/s/w2014mtdgpo1"}
              :mt-dg-pre {:count 0
                          :participants #{}
                          :site "http://fluidsurveys.usask.ca/s/w2014mtdgpr1"}
              :mt-td-pre {:count 3
                          :participants #{"ho"}
                          :site "http://fluidsurveys.usask.ca/s/w2014mttdpr1"}}
        db [{:_id :mt-dg-post
             :count 2
             :participants #{"hi" "there"}
             :site "http://fluidsurveys.usask.ca/s/w2014mtdgpo1"}
            {:_id :mt-dg-pre
             :participants #{"mew"}
             :count 0
             :site "http://fluidsurveys.usask.ca/s/w2014mtdgpr1"}]
        conds (merge-conditions init db)]
    (is (= conds
           {:mt-dg-post {:count 2
                         :participants #{"ho" "hi" "there"}
                         :site "http://fluidsurveys.usask.ca/s/w2014mtdgpo1"}
            :mt-dg-pre {:count 0
                        :participants #{"mew"}
                        :site "http://fluidsurveys.usask.ca/s/w2014mtdgpr1"}
            :mt-td-pre {:count 3
                        :participants #{"ho"}
                        :site "http://fluidsurveys.usask.ca/s/w2014mttdpr1"}}))))

(defn sort-_id [coll] (sort-by :_id coll))
(defn contains-all?
  ([c1 c2] (= (sort c1) (sort c2)))
  ([k c1 c2] (= (sort-by k c1) (sort-by k c2))))

(deftest test-db
  (let [conds (ref {:mt-dg-post {:count 2
                                 :participants #{"hi" "there"}
                                 :site "site1"}
                    :mt-dg-pre {:count 0
                                :participants #{"mew"}
                                :site "site2"}})
        db (setup-mongodb {:db-name "test1" :conditions conds})]
    (is (= @conds
           (mongo-to-map (mc/find-maps "test1"))))))


(deftest test-pending
  (let [conds (ref {:mt-dg-post {:count 2
                                 :participants #{}
                                 :site "site1"}
                    :mt-dg-pre {:count 3
                                :participants #{}
                                :site "site2"}})
        pend (ref [])
        expired (ref [])]
    (update-pending pend :mt-dg-pre "id1" (t/minutes -1))
    (let [rcd (get-by @pend :part-id "id1")
          moment (:expires rcd)]
      (is (= rcd
             {:cond-id :mt-dg-pre
              :expires moment
              :part-id "id1"})))

    (clean-pending pend expired)
    (is (empty? @pend))

    (update-pending pend :mt-dg-pre "id3" (t/minutes -1))
    (update-pending pend :mt-dg-pre "id4" (t/minutes 1))
    (is (contains-all? '("id3" "id4") (map :part-id @pend)))

    (clean-pending pend expired)
    (is (contains-all? '("id4") (map :part-id @pend)))
    (is (contains-all? '("id1" "id3") (map :part-id @expired)))))

(deftest test-clean-pending
  (let [conds (ref {:mt-dg-post {:count 2
                                 :participants #{}
                                 :site "site1"}
                    :mt-dg-pre {:count 3
                                :participants #{}
                                :site "site2"}})
        pend (ref [])
        expired (ref [])]
    (update-pending pend :mt-dg-pre "id1" (t/minutes -1))
    (update-pending pend :mt-dg-pre "id3" (t/minutes -1))
    (update-pending pend :mt-dg-pre "id4" (t/minutes 1))
    (is (contains-all? '("id1" "id3" "id4") (map :part-id @pend)))

    (clean-pending pend expired)
    (is (contains-all? '("id4") (map :part-id @pend)))
    (is (contains-all? '("id1" "id3") (map :part-id @expired)))

    (is (= 3 (:count (:mt-dg-pre @conds))))
    (is (zero? (count (filter :cond-corrected @pending))))
    (is (zero? (count (filter :cond-corrected @expired))))

    (correct-conditions-with-expired conds expired)
    (is (= 1 (:count ( :mt-dg-pre @conds))))
    (is (= 2 (count (filter :cond-corrected @expired))))))

(deftest test-finished
  (let [pend (ref [{:cond-corrected true,
                    :cond-id :mt-dg-pre,
                    :part-id "id1"}
                   {:cond-corrected true,
                    :cond-id :mt-dg-pre,
                    :part-id "id3"}])
        finished (ref [])]
    (finished-part pend finished "id1")
    (is (= "id1" (:part-id (first @finished))))
    (is (= 1 (count @finished)))
    (is (= 1 (count @pend)))
    (is (= "id3" (:part-id (first @pend))))))

(deftest test-dump-into-mongo
  (let [pend (ref [{:cond-corrected true,
                    :cond-id :mt-dg-pre,
                    :part-id "id1"}
                   {:cond-corrected true,
                    :cond-id :mt-dg-pre,
                    :part-id "id3"}])
        finished (ref [])
        mdb "testing"]
    (dump-into-mongo mdb "pending" @pend :part-id)
    (let [mcoll (make-keyword (mc/find-maps "pending") :cond-id)
          mcoll2 (map #(select-keys % (keys (first @pend))) mcoll)]
      (is (contains-all? :part-id @pend mcoll2)))
    (mc/remove "pending")))

(deftest test-we-have-participant
  (let [pend (ref [])
        finished (ref [])
        expired (ref [])]
    (update-pending pend :mt-dg-pre "id1" (t/minutes -1))
    (update-pending pend :mt-dg-pre "id3" (t/minutes -1))
    (update-pending pend :mt-dg-pre "id4" (t/minutes 1))
    (clean-pending pend expired)
    (is (contains-all? '("id4") (map :part-id @pend)))
    (is (contains-all? '("id1" "id3") (map :part-id @expired)))
    (finished-part pend finished "id4")

    (is (participant-exists? pend expired finished "id4"))
    (is (participant-exists? pend expired finished "id3"))
    (is (participant-exists? pend expired finished "id1"))
    (is (not (participant-exists? pend expired finished "id2")))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; scratch:
;; #_(def workspace
;;     (mg/connect!)
(mg/set-db! (mg/get-db "test1"))
(type (mg/get-db "test1"))
;;     (mc/remove "test1")
;;     (mc/find-maps "test1")

(def p (ref []))
;; (def f (ref []))
;; (def e (ref []))
(update-pending p :mt-dg-pre "id1" (t/minutes -1))
(p)
(contains-key? @p :part-id "id1")

(def o1 '({:cond-id :mt-dg-pre,
           :part-id "chp202"}))
(def n1 '({:cond-id :mt-dg-post,
           :part-id "chp203"}
          {:cond-id :mt-dg-pre,
           :part-id "chp202"}))

(into {} (for [[k v] @(:conditions global-state)] [k v]))
(into {} [[:hi "ho"] [:hello "hollo"]])

(def m1 '({:cond-id :mt-td-pre,
           :part-id "chp207"}
          {:cond-id :mt-dg-pre,
           :part-id "chp206"}
          {:cond-id :mt-td-pre,
           :part-id "chp205"}
          {:cond-id :mt-td-sp-las-pre,
           :part-id "chp204"}))
(def m2 '({:cond-id :mt-td-pre,
           :part-id "chp207"}
          {:cond-id :mt-dg-pre,
           :part-id "chp206"}
          {:cond-id :mt-td-pre,
           :count 2
           :part-id "chp209"}
          {:cond-id :mt-td-sp-las-pre,
           :part-id "chp204"}))
(def m3 '({:_id "tarn"
           :cond-id :mt-td-pre,
           :site "site1"
           :participants ["ast" "sit"]}
          {:_id "sitan"
           :cond-id :mt-dg-pre,
           :site "site1"
           :participants ["stai"]}))
(sort-by :part-id (merge-smap m1 m2 :part-id))
(= (sort-by :part-id (merge-smap m1 m2 :part-id))
   '({:part-id "chp204", :cond-id :mt-td-sp-las-pre}
     {:part-id "chp205", :cond-id :mt-td-pre}
     {:part-id "chp206", :cond-id :mt-dg-pre}
     {:part-id "chp207", :cond-id :mt-td-pre}
     {:part-id "chp209", :count 2, :cond-id :mt-td-pre}))
(mongo-to-map m3)

;; TODO:
(conj nil 1 2)

(part-pending-or-new? "chp2010" global-state)
(register-and-get-url "chp222" global-state)

(defn mongo-to-map
  "Take smap, return map of :cond condmap. Problem is, mongo turns set
  into a vector, and turns keys into _ids."
  [mongo-smap]
  (let [rfn (fn [smap m]
              (-> (assoc m :participants (set (:participants m)))
                  (dissoc :_id)
                  (partial assoc smap (:_id m))))]
    (reduce rfn {} mongo-smap)))

(->> (into m1 m2)
     (group-by :part-id)
     vals
     (map (partial apply deep-merge)))

(let [new-ids
      old-ids
      to-remove (remove (set (map :part-id n1)) (set (map :part-id o1)))
      to-add (remove (set (map :part-id o1)) (set (map :part-id n1)))]

  (doseq [oid to-remove]
    (mc/remove coll-name {:_id oid}))
  (doseq [oid to-add]
    (let [m (get-by n1 :part-id "chp203")]
      (mc/update coll-name {:_id oid} (assoc m :_id oid) :upsert true))))

;; find the watches on each ref
(.getWatches (:conditions global-state))
(->> (filter #(instance? clojure.lang.Ref (second %)) global-state)
     vals
     (map #(.getWatches %)))
;; remove the watches on each ref




(remove-watches global-state)
(list-watches global-state)

(filter #(instance? clojure.lang.Ref (second %)) global-state)
vals
(#(doseq [r %
          w (keys (.getWatches r))]
    (remove-watch r w)))

(list-watches global-state)


;; (update-pending p :mt-dg-pre "id3" (t/minutes -1))
;; (update-pending p :mt-dg-pre "id4" (t/minutes 1))
;; (clean-pending p e)
;; (is (contains-all? '("id4") (map :part-id @p)))
;; (is (contains-all? '("id1" "id3") (map :part-id @e)))
;; (finished-part p f "id4")

;; (is (participant-exists? p e f "id4"))

;; (or (some #{"id4"} (map :part-id @e))
;;     (some #{"id4"} (map :part-id @p))
;;     (some #{"id4"} (map :part-id @f)))

;; (is (participant-exists? pend expired finished "id3"))
;; (is (participant-exists? pend expired finished "id1"))
;; (is (not (participant-exists? pend expired finished "id2")))
;; (def c (ref {:mt-dg-post {:count 4
;;                           :participants #{"hi" "there"}
;;                           :site "site1"}
;;              :mt-dg-pre {:count 5
;;                          :participants #{"mew"}
;;                          :site "site2"}}))
;; (get-next-cond-and-inc c)

;; (#{"id1" "id2"} #{"id2"})

;;     (mc/update-by-id "test1" "mt-dg-pre" (:mt-dg-pre @c) :upsert true)
;;     (update-mongodb "test1" c)

;;     (def p [{:cond-corrected true,
;;              :cond-id :mt-dg-pre,
;;              :part-id "id1"}
;;             {:cond-corrected true,
;;              :cond-id :mt-dg-pre,
;;              :part-id "id3"}])
;;     (def f [])
;;     (dump-into-mongo "testing" "pending" p :part-id)
;;     (let [mcoll (make-keyword (mc/find-maps "pending") :cond-id)
;;           mcoll2 (map #(select-keys % (keys (first p))) mcoll)]
;;       (is (contains-all? :part-id p mcoll2)))
;;     (mc/remove "pending")

;;     (def t {:mt-dg-post {:count 0
;;                          :participants #{"ho"}
;;                          :site "http://fluidsurveys.usask.ca/s/w2014mtdgpo1"}
;;             :mt-dg-pre {:count 0
;;                         :participants #{}
;;                         :site "http://fluidsurveys.usask.ca/s/w2014mtdgpr1"}
;;             :mt-td-pre {:count 3
;;                         :participants #{"ho"}
;;                         :site "http://fluidsurveys.usask.ca/s/w2014mttdpr1"}})
;;     (mc/insert "test1" t)

;;     (mg/set-db! (mg/get-db "test2"))
;;     (mc/remove "test2")
;;     (mc/find-maps "test2")
;;     (mc/update "test2" {:part-id "id1"} {:part-id "id1" :lots "of" :stuff "with data"} :upsert true)
;;     (mc/update "test2" {:part-id "id1"} {:part-id "id1" :lots "of!" :stuff "with even more data" :more "and more"} :upsert true)
;;     (mc/co)

;;     (defn a? [r] (t/before? (t/now) r))
;;     (defn adj [t] (t/plus (t/now) t))
;;     (def t [(adj (t/hours -1)) (adj (t/hours 1)) (adj (t/hours -2)) (adj (t/hours 2)) (adj (t/hours -3)) (adj (t/hours 3))])
;;     (group-by a? t)

;;     (into ["hi" "hello"] ["arst" "ast" "ast"])
;;     (let [[a b] (vals (group-by even? (range 10)))] [a b])
;;     (even? 4)
;;     (mc/find-maps "test1")

;;     (def pend1 (ref []))
;;     (def expi1 (ref []))
;;     (update-pending pend1 t "id1" (t/minutes -1))
;;     (update-pending pend1 t "id2" (t/minutes 1))
;;     (first @pend1)
;;     (first @expi1)
;;     (clean-pending pend1 expi1)
;;     (get-by @t :mt-dg-pre)


;;     (t/to-time-zone (t/now) (t/time-zone-for-offset -6))
;;     (-> @pend1 first :expires tl/to-local-date-time)
;;     (def t2 (setup-mongodb "test1" t))

;;     (tf/parse (str (t/now)))
;;     (mc/insert "test1" {:time  (t/now)})
;;     (def old (:time (first (filter #(:time %) (mc/find-maps "test1")))))
;;     (t/in-minutes (t/interval (t/plus old (t/hours -1))
;;                               (t/now)))
;;     (t/after? (t/plus old (t/hours +1))
;;               (t/now))


;;     (setup-mongodb "test1" tc)

;;     (mg/set-db! (mg/get-db "test1"))
;;     (mc/find-maps "test1")
;;     (update-mongodb "test1" tc)



;;     (dosync
;;      (alter tc update-conditions-old-to-new '()))
;;     @tc
;;     (use 'clojure.tools.trace)
;;     (trace
;;      (merge {:foo #{"hello"} :site "blah"} {:foo #{"hi" "there"} :site "blah"}))

;;     (into (set (map :foo [{:foo 1} {:foo 2} {:foo 3} {:foo 3}])) (map :foo [{:foo 3} {:foo 4}]))
;;     (let [id :id]
;;       (id {} {:foo 3}))

;;     )
