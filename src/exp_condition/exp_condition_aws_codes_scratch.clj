(ns exp-condition.exp-condition-aws-codes-scratch
  (:require [clojure.pprint :refer [pprint]]
            [environ.core :refer [env]]
            [clj-http.client :as cl]
            [biomass [hits :as hits] [request :as request]]
            [xml-to-clj.core :as x2c]
            [monger [core :as mg] [collection :as mc] [query :as mq] [operators :as mo]]
            [monger.joda-time]
            [monger.json]
            [clojure.math.numeric-tower :as math]
            [exp-condition.exp-condition-aws-codes :refer :all])
  (:import [java.io ByteArrayInputStream]))

(setup :sandbox? false)

(def all-hits (get-all-hits))
(count all-hits)

;; need to find this out manually by running the above and finding the
;; HITId with the Desription
(def game-HITIds
  (->> (filter #(re-find #"^Answer some questions, play a simulation" (:Description %))
              all-hits)
       (map :HITId)))

;; and this has all the info we need: :Assignment, :WorkerId, :Answer/:QuestionFormA
;; get one page of assignments:
;;(def t (amazon-request "GetAssignmentsForHIT" {:HITId game-HITId}))

;; get all assignments
(def all-assmts (mapcat #(get-reviewable-assignments-for-HITId %) game-HITIds))
(count all-assmts)
(frequencies (map :AssignmentStatus all-assmts))

(def submitted-assmts (remove #(= "Approved" (:AssignmentStatus %)) all-assmts))
(frequencies (map :AssignmentStatus submitted-assmts))

(def ids-codes (map (comp validate-worker-code get-workerid-compl-code) submitted-assmts))
(count ids-codes)
(def not-val (filter (comp not :valid) ids-codes))
(count not-val)

;; details about those who didn't enter valid codes -- do they have a finished record?
(map worker-info (filter (comp not :valid) ids-codes))

;; all the ones NOT in finished docs
(def invalid-codes (filter (comp not :in-fin-doc)
                         (map valid-in-finished-docs (filter (comp not :valid) ids-codes))))
(count invalid-codes)

;; theose that ARE valid in some way:
(def valid-codes (filter :in-fin-doc (map valid-in-finished-docs ids-codes)))
(count valid-codes)

;; no overlap:
(clojure.set/intersection (into #{} (map :id invalid-codes))
                          (into #{} (map :id valid-codes)))

;; and all are present:
(= (clojure.set/union (into #{} (map :id invalid-codes))
                             (into #{} (map :id valid-codes)))
   (into #{} (map :id ids-codes)))

;; now, for each that made it to finished, even if they put in the wrong code, give them the approval:
(def msg-for-turks "Thank you again for participating in the experiment last week.\nIf you recall, one pair of participants would be randomly chosen to actually split the $200. Those two particpants have been notified (about 10 minutes ago). There will be more experiments like this in the future -- some are similar to this, and cannot be taken again by participants who took this one. But in a month or two I will have a new set of experiments that will pay more and will be open to everyone again.\n\nIf you have any questions about the experiment, please let me know and I would be happy to answer.\nThank you kindly, \nChris.\n\nChristopher Poile, PhD\n Assistant Professor, Human Resources and Organizational Behaviour\n Edwards School of Business | University of Saskatchewan\n PotashCorp Centre   |  25 Campus Drive  |  Saskatoon, SK  S7N 5A7\n website | T  306.966.2491  |  F  306.966.2514")

;; approve assignments:
(pmap #(approve-assignment % msg-for-turks) valid-codes)

;; reject the ones that didn't finish:
(pmap #(reject-assignment %) invalid-codes)


;; what is the assmt-id of the turk who won?
(filter #(= "A2PCDF7IZ565KN" (:id %)) ids-codes)
;; assmt-id "3ZQIG0FLQEG7Q6ZDB0WIH61NJEIWVJ"

(amazon-request "NotifyWorkers" {:WorkerId "A9ML82UBUIZWK"
                                 :Subject "No code for experiment"
                                 :MessageText "Hello, \nI was going through the assignments today (for the experiment and simulation you did yesterday) and I noticed you said you didn't receive a code. Could you please tell me what the last screen was? (Just a couple of the types of questions it asked you is enugh), and then when you press the finished button, did anything happen? I'd like to know what went wrong so that it doesn't happen again. Thanks!  \nChris."})

(amazon-request "NotifyWorkers" {:WorkerId "A3EH26LZGAXHGX"
                                 :Subject "No code for experiment"
                                 :MessageText "Hello, \nI was going through the assignments today (for the experiment and simulation you did yesterday) and I noticed you said you didn't receive a code. Could you please tell me what the last screen was? (Just a couple of the types of questions it asked you is enugh), and then when you press the finished button, did anything happen? I'd like to know what went wrong so that it doesn't happen again. Thanks!  \nChris."})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; now save ids-codes information, just in case, and then dispose all
;; approved assignments

