;; to use xml-to-clj, clone the repo, run lein install, link to it
;; from the checkouts directory, need to add it to project.clj
;; NOTE:
;; biomass needs to be switched to branch xmltoclj!!!!!
;; AND, biomass needs to have xml-to-clj checkedout, too.
;; then run lein install on biomass, after xml-to-clj
;; 
;; then you can run this in a local repl if you have the db setup
;; correctly (rsync copied from chp3)


(ns exp-condition.exp-condition-aws-codes
  (:require [clojure.pprint :refer [pprint]]
            [environ.core :refer [env]]
            [clj-http.client :as cl]
            [biomass [hits :as hits] [request :as request]]
            [xml-to-clj.core :as x2c]
            [monger [core :as mg] [collection :as mc] [query :as mq] [operators :as mo]]
            [monger.joda-time]
            [monger.json]
            [clojure.math.numeric-tower :as math]
            [clojure.string :as str])
  (:import [java.io ByteArrayInputStream]))

;; load this file
;; then call it from exp_condition_aws_codes_scratch

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; globals
(declare conn)
(declare db)
;; CHANGE ME:
(def sess-coll "s2014_sess4_finished")
(def sess-db "exp_condition_s14_sess4")

(defn process-response
  [req resp]
  (-> resp
      :body
      x2c/xml-to-clj
      ((keyword (str req "Response")))
      ((keyword (str req "Result")))))

(defn amazon-request
  ([req] (amazon-request req {}))
  ([req params]
     (->> (request/send-request req params)
          (process-response req))))

(defn get-all-request
  [operation & {:as params}]
  (let [first-resp (amazon-request operation (merge params {:PageSize 1}))
        total-num (:TotalNumResults first-resp)
        num-pages (math/ceil (/ total-num 100))
        reqs (for [page (range 1 (inc num-pages))
                   :let [_ (println "futuring page " page)]]
               (future (amazon-request operation (merge params {:PageSize 100 :PageNumber page}))))]
    (flatten (map (comp :HIT deref) reqs))))

(defn get-all-hits []
  (get-all-request "SearchHITs"))

(defn get-reviewable-hits []
  (get-all-request "GetReviewableHITs"))

(defn get-reviewable-assignments []
  (doall
   (->> (get-reviewable-hits)
        (map #(future (amazon-request "GetAssignmentsForHIT" %)))
        (map deref))))

(defn get-reviewable-assignments-for-HITId
  [hitid]
  (let [first-resp (amazon-request "GetAssignmentsForHIT" {:HITId hitid :PageSize 1})
        total-num (:TotalNumResults first-resp)
        num-pages (math/ceil (/ total-num 100))
        reqs (for [page (range 1 (inc num-pages))
                   :let [_ (println "futuring page " page)]]
               (future (amazon-request "GetAssignmentsForHIT" {:HITId hitid :PageSize 100 :PageNumber page})))]
    (flatten (map (comp :Assignment deref) reqs))))

(defn get-workerid-compl-code
  [assmt]
  (let [id (-> assmt :WorkerId)
        assmt-id (-> assmt :AssignmentId)
        code (-> assmt :Answer :QuestionFormAnswers :Answer :FreeText)
        code-safe (or code "")]
    {:id id :code (str/lower-case (str/trim code-safe)) :assmt-id assmt-id}))

(defn validate-worker-code
  [{:keys [id code] :as z}]
  (let [finished-doc (mc/find-one-as-map db sess-coll {:part-id id :code code})]
    (assoc z :valid (not (nil? finished-doc)))))

(defn worker-info
  [{:keys [id] :as z}]
  (let [fin-doc (mc/find-one-as-map db sess-coll {:part-id id})]
    (assoc z :fin-doc fin-doc)))

(defn valid-in-finished-docs
  [{:keys [id] :as z}]
  (let [fin-doc (mc/find-one-as-map db sess-coll {:part-id id})]
    (assoc z :in-fin-doc (not (nil? fin-doc)))))

(defn approve-assignment
  [{:keys [assmt-id]} & feedback]
  (let [params (merge {:AssignmentId assmt-id}
                      (and feedback
                           {:RequesterFeedback feedback}))]
    (amazon-request "ApproveAssignment" params)))

(defn reject-assignment
  [{:keys [assmt-id]}]
  (let [params (merge {:AssignmentId assmt-id}
                      {:RequesterFeedback "Our records indicate that you did not finish the experiment. If this is an error, please let us know and we'll investigate further. Also, if possible, please let us know what went wrong so that we can fix the problem for the next time we run the experiment.\n\nThank you for your time!"})]
    (amazon-request "RejectAssignment" params)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; set db and aws:
;;
(defn setup
  [& {:keys [sandbox?]}]

  (def conn (mg/connect))
  (def db (mg/get-db conn sess-db))

  ;; this will make the mturk.properties file
  (request/set-aws-creds {:AWSAccessKey (env :aws-access-key)
                          :AWSSecretAccessKey (env :aws-secret-key)})
  ;; sandbox testing or no?
  (request/set-aws-target-as-sandbox sandbox?)
  ;;(request/set-aws-target-as-sandbox true)
  ;;(request/set-aws-target-as-sandbox false)

  ;; not using soap at the moment
  ;;(def soap-service (RequesterService. (PropertiesClientConfig. "mturk.properties")))
  )


