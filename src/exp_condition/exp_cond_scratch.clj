;; use this as a workspace, but don't eval any of it.

;; set the namespace to the main file so we can C-c C-p to evaluate and pprint.
;; (cider takes the namespace from the ns decl or the filename)
(ns exp-condition.exp-cond-scratch
  (:require [clojure.pprint :refer [pprint]]
            [monger [core :as mg] [collection :as mc] [db :as db] [operators :refer :all]]
            [exp-condition.exp-condition-web
             :refer :all]
            [clj-time [core :as t] [local :as tl] [coerce :as tco] [format :as tf]])
  (:import [java.util UUID]))

;; db for testing
(def testing-db (mg/get-db (mg/connect) "exp_condition"))
;; basic-auth for testing
(def basic-auth "iTXljNzg1VG04Zk9Wc1JsanpQQTlwOkl5T3ZTNzhFWVdpRmR3S0c0eHpHT")
(def basic-auth-fin "X2Q1BNXOFXCsi7RsDOYeHH44kLbiTDf6Wo8d")


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; what's the status?
(report-conditions)
;; howabout total entered?
(mc/count testing-db (str session-name "_id_to_pk_permanent") {})

;; anyone unfinished after an hour??
(unfinished-older-than-mins 120)


(unfinished-older-than-mins 1)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; to remove the unfinished:
;; dangerous:
(remove-older-than-mins 120)



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; get info on someone unfinished:
;; need to fill the queries in yourself.
(unfinished-older-than-mins 1)
(mc/find-maps testing-db (str session-name "_pending") {})
(mc/find-one-as-map testing-db (str session-name "_state") {})
(mc/find-one-as-map testing-db (str session-name "_conditions") {:condition "dg-pre"})
(mc/find-one-as-map testing-db (str session-name "_id_to_pk_permanent") {:pk "53c870772dce599f86ac075b"})

(part-state "A1LAZ3AO0NYBC1")
(mc/find-one-as-map testing-db (str session-name "_id_to_pk_permanent") {:part-id "A1LAZ3AO0NYBC1"})
(part-in-condition-set? "53c947e12dce599f86ac0862")
(finished? "53c947e12dce599f86ac0862")


(part-in-condition-set? "53c87bc62dce599f86ac075f")
(finished-old-session? "104O444JWHST6")

;; dangerous:
(remove-participant-from-everything "53c87bc62dce599f86ac075f")

(-> (mc/find-one-as-map testing-db (str session-name "_conditions") {:participants {$in ["53c87bc62dce599f86ac075f"]}})
    :condition)

(mc/find-one-as-map testing-db (str session-name "_state") {})
(mc/find-one-as-map testing-db (str session-name "_finished") {})
(mc/find-one-as-map testing-db (str session-name "_conditions") {})
(mc/find-one-as-map testing-db (str session-name "_id_to_pk_permanent") {})

(mc/update testing-db (str session-name "_conditions")
           {:condition "dg-pre"} {$inc {:count -1}})

(mc/find-one-as-map testing-db (str session-name "_conditions")
           {:condition "dg-pre"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; REMOVE ALL AFTER TESTING:
;; DANGEROUS!!!!!!
;; (->> (map :pk (mc/find-maps testing-db (str session-name "_id_to_pk_permanent") {}))
;;     (map remove-participant-DANGER-from-everything))



;; insert for testing
(mc/update testing-db (str session-name "_conditions") {:condition "dg-pre"} {$pushAll {:participants ["chp201" "chp202" "chp203"]}})

;; now test matching
(mc/any? testing-db (str session-name "_conditions") {:condition "dg-pre"
                                                      :participants {$in ["chp201" "chp200"]}})

;; remove
(mc/update testing-db (str session-name "_conditions") {:condition "dg-pre"} {$pullAll {:participants ["chp201" "chp202" "chp203"]}})

;; test if we have a pk, it's mapped to their id, and we can find it
;; in one of the state lists.
(:pk (mc/find-one-as-map testing-db (str session-name "_id_to_pk_permanent") {:part-id "chp201"}))

(mc/any? testing-db (str session-name "_state")
         {:state "registered"
          :list {$in [(:pk (mc/find-one-as-map testing-db (str session-name "_id_to_pk_permanent") {:part-id "chp201"}))]}})


;; testing the random get-next-cond-and-inc
(let [poss-conds (filter #(active-cond-list (:condition %)) (conditions))]
  (rand-nth (apply min-key :count poss-conds)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; test the app calls:
;; start the intro:
(def r1 (app {:uri "/valid-worker/"
              :request-method :get
              :params {:id "chp201" :basic basic-auth}}))

;; finish the intro, get a condition: (must have done intro)
(let [pk (:pk (mc/find-one-as-map testing-db
                                  (str session-name "_id_to_pk_permanent")
                                  {:part-id "chp201"}))]
  (def r2 (app {:uri "/exp-condition/" :request-method :get :params {:pk pk :basic basic-auth}})))

(conditions)

;; find in pending
(let [pk (:pk (mc/find-one-as-map testing-db
                                  (str session-name "_id_to_pk_permanent")
                                  {:part-id "id4"}))]
  (mc/find-one-as-map testing-db (str session-name "_pending") {:pk pk}))

;; find in finished
(let [pk (:pk (mc/find-one-as-map testing-db
                                  (str session-name "_id_to_pk_permanent")
                                  {:part-id "id7"}))]
  (if-let [fin-rcd (mc/find-one-as-map testing-db (str session-name "_finished") {:pk pk})]
    (select-keys fin-rcd [:code])
    nil))

;; find what part-state player is in
(part-state "id8")

;; start and finish intro, which condition did we get?
(let [id "id9"
      r1 (app {:uri "/valid-worker/"
              :request-method :get
              :params {:id id :basic basic-auth}})
      pk (:pk (mc/find-one-as-map testing-db
                                  (str session-name "_id_to_pk_permanent")
                                  {:part-id id}))
      r2 (app {:uri "/exp-condition/"
               :request-method :get
               :params {:pk pk :basic basic-auth}})
      r3 (app {:uri "/finished-exp/"
               :request-method :get
               :params {:pk pk :basic basic-auth-fin :jsonp "my_json"}})]
  (pprint r1)
  (pprint r2)
  r3)


(assign-condition "5398d729437899ce331b0a33")
(record-pending "5398d729437899ce331b0a33" "dg-post" (t/hours 2))
(conditions)
(get-next-cond-and-inc)
(pending? "5398d729437899ce331b0a33")
(part-state "5398d729437899ce331b0a33")
(get-existing-cond "5398d729437899ce331b0a33")

(filter #(= "td-pre" (:condition %)) (conditions))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DROP THE STATE AND id-to-pk tables
;; ONLY USE WHEN TESTING!!!!!
                                        ;(mc/drop testing-db (str session-name "_state"))
                                        ;(mc/drop testing-db (str session-name "_id_to_pk_permanent"))





;; to persist the edn file:
(defn write-to-file
  [filename data]
  (spit filename (prn-str data)))
(write-to-file "resources/conditions.edn" @(:conditions global-state))

;; (str "Thank you for your participation! Your completion code is: <strong>" fin-code "</strong>.<p>Enter this into the submission box and we will automatically pay you.<p>If you are picked to win the prize, we will contact you and pay through the Mechanical Turk bonus system.")

;; test map nested destructuring
(let [{{:keys [t3 t4]} :test2 :keys [test1 test2]} {:test1 "hi" :test2 {:t3 "he" :t4 "ha"}}]
  [test1 test2 t3 t4])


(def myout *out*)
(def global-state (assoc global-state :db-name "test"))

(def global-state (assoc-in global-state [:active-conds] #{:mt-dg-pre :mt-td-pre}))
global-state
;; test the routing:
(app {:uri "/exp-condition" :request-method :get :params {:id "chp207" :basic "T0tGYzhxQ0piTXljNzg1VG04Zk9Wc1JsanpQQTlwOkl5T3ZTNzhFWVdpRmR3S0c0eHpHTjBpUXNaQ1hpNU9adzZ0SjdFMkU"}})
(app {:uri "/finished-exp" :request-method :get :params {:id "chp203" :basic "T0tGYzhxQ0piTXljNzg1VG04Zk9Wc1JsanpQQTlwOkl5T3ZTNzhFWVdpRmR3S0c0eHpHTjBpUXNaQ1hpNU9adzZ0SjdFMkU"}})


(defn put-reg-pk
  [new-pk]
  (dosync
   (alter (:registered global-state) conj {:part-id "thisisatest" :pk new-pk})))

;; add a user to the pending list
(dosync
 (alter (:pending global-state) conj {:part-id "thisisatest" :pk "test123"}))
(auth-pk? "test123" @(:pending global-state))

(def finresp (app {:uri "/finished-exp" :request-method :get :params {:pk "test123"}}))

global-state
(def tid "testtest")
(def validresp (app {:uri "/valid-worker" :request-method :get :params {:id tid :basic "iTXljNzg1VG04Zk9Wc1JsanpQQTlwOkl5T3ZTNzhFWVdpRmR3S0c0eHpHT"}}))
(def condurl (let [urlkey (second (re-find #"pk=(.*)" (:body validresp)))]
               (app {:uri "/exp-condition" :request-method :get :params {:pk urlkey}})))

(def finresp (let [urlkey (str (second (re-find #"pk=(.*)" (:body condurl))))]
               (app {:uri "/finished-exp" :request-method :get :params {:pk urlkey}})))

(auth-id-urlkey? "chp321" "5306ac97e4b02cfc84bd8b2fe" @(:pending global-state))
(dosync
 (alter (:registered global-state) (partial remove #(= "chp321" (:part-id %)))))

(def p (:pending global-state))
(def c (:conditions global-state))

(let [pend-m (get-by @p :part-id tid)]
  (-> @c
      ((:cond-id pend-m))))

site (-> @conditions
         (:cond-id pend-m)
         :site)
urlkey (:urlkey pend-m)
(str site "?urlkey=" urlkey "&id=" id)

(def mongo-agent (agent (mg/get-db "w2014mt")))
(part-condition "chp212312" global-state)
(:conditions global-state)
(remove-watch (:conditions global-state) :db-log)
(remove-watch (:pending global-state) :db-log)
(remove-watch (:expired global-state) :db-log)
(remove-watch (:finished global-state) :db-log)

(filter #((:active-conds global-state) (key %)) @(:conditions global-state))
(filter #(active-conds (key %)) @conds)

(select-keys @(:conditions global-state)
             (for [[k v] @(:conditions global-state)
                   :when ((:active-conds global-state) k)] k))
(let [avail-conds (filter #((:active-conds global-state) (key %)) @(:conditions global-state))
      k (get-lowest-key avail-conds :count)]
  (alter conditions update-in [k :count] inc)
  k)

(def a (map (fn [m] (select-keys m (remove #(= :_id %) (keys m)))) @(:pending global-state)))
(def b '({:part-id "chp204",
          :cond-id "mt-td-sp-las-pre"}
         {:part-id "chp203",
          :cond-id "mt-td-post"}
         {:part-id "chp203",
          :cond-id :mt-td-post}
         {:part-id "chp205",
          :cond-id :mt-td-sp-las-pre}))

(mc/find-maps "pending")

;; clean up && WARNING!!!!!
;; (dosync
;;  (ref-set (:pending global-state) [])
;;  (mc/remove "pending"))
;; (dosync
;;  (ref-set (:registered global-state) [])
;;  (mc/remove "registered"))
;; (dosync
;;  (ref-set (:finished global-state) [])
;;  (mc/remove "finished"))
;; (dosync ;; really dangerous, should not do.
;; (ref-set (:pk-to-id-perm global-state) [])
;; (mc/remove "pk-to-id-perm"))

;; (dosync
;;  (ref-set (:conditions global-state) [])
;;  (mc/remove "conditions"))


(dosync
 (alter (:conditions global-state) update-in [:mt-dg-sp-las-post :count] inc))

;; to set repl-print up, need to store the out by evaluating it in the repl (C-c C-p)
(def repl-out *out*)  ;; <-- this
(defn repl-print [msg]
  (binding [*out* repl-out]
    (pprint msg)))

(repl-print "hello")

(.toString (String. "hello") 32)

(def test-vec-sets #{{:name "Chris" :count 0}
                     {:name "Joe" :count 0}
                     {:name "Bob" :count 0}})

(defn get-next-cond []
  (let [next (inc-conds-atomically mapsref)]
    (swap! atom-map update-in [(keyword (:name next))] inc)))

;; test
(do
  (def mapsref (ref test-vec-sets))
  (def probcounter (atom 0))
  (def atom-map (atom {:Chris 0 :Joe 0 :Bob 0}))

  (time (wait-futures 20
                      (dotimes [_ 1000] (get-next-cond))
                      (dotimes [_ 1000] (get-next-cond))
                      (dotimes [_ 1000] (get-next-cond))
                      (dotimes [_ 1000] (get-next-cond))
                      (dotimes [_ 1000] (get-next-cond))
                      (dotimes [_ 1000] (get-next-cond))
                      (dotimes [_ 1000] (get-next-cond))))

  (assert (let [mapsref-tot (reduce + (map :count @mapsref))
                atom-tot (reduce-kv #(+ %3 %1) 0 @atom-map)]
            (println (format  "mapsref-tot == %d, atom-tot == %d." mapsref-tot atom-tot))
            (= mapsref-tot atom-tot)) "Error.")
  (assert (zero? @probcounter)))


(inc-conds-atomically mapsref)


(- (:count (apply max-key :count @mapsref)) (:count (apply min-key :count @mapsref)))

(get-lowest-key @mapsref :count)

(def test [{:test 1} {:test 2}])
(remove #(=  {:test 1} %) test)

(alter conds (update-in @conds []))
(def conds (ref test-vec-maps))
(def conds (inc-conds-atomically conds))

(get-next-condition-and-inc! conditions)

(def bilbo {:loot  (into #{} (range 1 27))})
(def gandalf {:loot (into #{} (range 26 56))})

(map (comp count :loot) [bilbo gandalf])
(filter (:loot bilbo) (:loot gandalf))

(def t1 conditions)
(def t2 (apply min-key :count t1))
(def t3 (update-in t2 [:count] inc))

(def db (mg/get-db "f2013-conditions"))
(db/get-collection-names)
(comment mc/drop "conditions")
@conditions
(mc/find-maps "conditions")

(mc/find-one-as-map "participants" {:nsid "chp201"})

(update-mongodb-with-conditions (ref [{:_id (ObjectId. "52ad07d3c7062bef57d8ebe3")
                                       :site "f2013c1l",
                                       :count 1,
                                       :condition "td-sp-recall-pre"}]))
(update-mongodb-with-conditions conditions)



(def tst (str-to-b64-str "nsid"))
(b64-str-to-str tst)

(doseq [{oid :_id :as cond} @conditions]
  (println oid)
  (pprint (dissoc cond :_id)))

(map (fn [{oid :_id :as cond}]
       (do
         (repl-print "inside the map")
         (mc/update-by-id "conditions" oid (dissoc cond :_id)))) @conditions)


(mc/insert-batch "documents"
                 [{:product "book" :title "Harry Pobber" :price 3.99 :pubdate "20000128"}
                  {:product "book" :title "Flubber Buster" :price 8.99 :pubdate "20020128"}
                  {:product "book" :title "Judas Unchained" :price 12.99 :pubdate "20040501"}])
(mc/save "people" {:name "Joe" :age "34"})
(def tmp (mc/find-maps "documents"))
(let [rec (mc/insert-and-return "people" {:name "Bill" :age "35"})
      oid (:_id rec)
      _ (mc/save "people" {:name "Bill"} {:hobby "reading"})
      rec (mc/find-by-id "people" oid)]
  rec)
(def res (mc/find-maps "documents" {:price {$gt 4}}))
(def res (vec res))
(mc/count "documents")
(mc/count "documents" {:price {$gt 4}})
#_(with-collection "documents"
    (find {:price {$gt 4}})
    (snapshot))

(mc/count "counters")
(ObjectId.)
mg/*mongodb-write-concern*

(def xs (atom 3))
(compare-and-set! xs 3 4)


;; page 172 in Clojure Programming
;; Helper functions for concurrency testing.
(defmacro futures
  [n & exprs]
  (vec (for [_ (range n)
             expr exprs]
         `(future ~expr))))

(defmacro wait-futures
  [& args]
  `(doseq [f# (futures ~@args)]
     @f#))

;; old condition-response
#_(defn- condition-response [nsid]
    (let [b64-nsid (str-to-b64-str nsid)]
      (if-let [cond (already-have-cond nsid)]
        (generate-string (assoc (dissoc cond :participants :count) :bnNpZA b64-nsid))
        ;; didn't find an existing cond for this nsid.
        (let [cond (inc-conds-atomically conditions nsid)]
          (update-mongodb-with-conditions conditions)
          ;; nsid in base64 is: bnNpZA
          (generate-string (assoc (dissoc cond :participants :count) :bnNpZA b64-nsid))))))
