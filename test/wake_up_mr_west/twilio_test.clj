(ns wake-up-mr-west.twilio-test
  (:require [clojure.test :refer [deftest testing is]]
            [wake-up-mr-west.twilio :as twil]
            [clojure.string :as str]
            [clojure.xml :as xml])
  (:import (java.io ByteArrayInputStream)))


(defn parse-xml-str [s]
  (xml/parse (ByteArrayInputStream. (.getBytes s))))

(deftest say-test
         (testing "Returns valid xml"
                  (is (parse-xml-str (twil/say "Wake up Mr. West"))))
         (testing "Contains target string"
                  (let [target-str "Wake up Mr. West"]
                    (is (= target-str
                           (-> (parse-xml-str (twil/say target-str))
                               :content
                               (first)
                               :content
                               (first)
                               (str/trim)))))))

