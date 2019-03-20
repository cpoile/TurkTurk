(ns exp-condition.web
  (:require [clojure.string :refer [split]]))

(def a (promise))

(deliver a 42)

@a

(map #(every? (set "qwertasdfgzxcvb") %)
     ["hello" "fast" "zylophone" "sweatervest" "south" "zebras"])

(map (seq "test") #(%))
(re-pattern "\\a+")

(vec "qwertasdfgzxcvb")

(reduce + (filter #(or (= (mod % 5) 0) (= (mod % 3) 0)) (range 1001)))

(mod 19 5)

(def temp [{:condition "dg-post"
            :count 0
            :participants ["sen123" "fsq492" "wsy502"]
            :site "http://fluidsurveys.usask.ca/s/f2013c2"}
           {:condition "dg-pre"
            :count 0
            :participants ["dig203" "oll103" "fel523"]
            :site "http://fluidsurveys.usask.ca/s/f2013c1"}])

(dissoc (first (filter #(some #{"sen123"} (:participants %)) temp)) :participants :count)
