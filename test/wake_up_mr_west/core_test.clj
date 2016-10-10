(ns wake-up-mr-west.core-test
  (:require [clojure.test :refer :all]
            [wake-up-mr-west.core :refer :all]
            [clojure.core.async :as a]
            [clojure.xml :as xml]
            [clojure.string :as str]
            [wake-up-mr-west.twilio :as twil])
  (:import (java.io ByteArrayInputStream)
           (java.util Date)))

(use-fixtures :once #(do (reset! state {})
                         (%1)
                         (reset! state {})))

(deftest go-with-retries-test
  (testing "Default counter"
    (let [counter (atom 0)
         finished-ch? (go-with-retries
                        (swap! counter inc)
                        (throw (Exception.)))]
     (a/alts!! [finished-ch? (a/timeout 200)])
     (is (= @counter default-retries))))
  (testing "Custom number of retries"
    (let [counter (atom 0)
          finished-ch? (go-with-retries 2
                         (swap! counter inc)
                         (throw (Exception.)))]
      (a/alts!! [finished-ch? (a/timeout 200)])
      (is (= @counter 2))))
  (testing "Returns value"
    (is (= 1 (a/<!! (go-with-retries (do 1))))))
  (testing "Behaves like go block"
    (let [ch (a/chan)
          val 1]
      (a/put! ch val)
      (is (= val (a/<!! (go-with-retries (a/<! ch))))))))

(deftest add-test
  (testing "Makes the call"
    (let [made-call? (a/promise-chan)]
      (with-redefs [twil/make-call (fn [& _] (a/put! made-call? true))]
        (add (Date.) "" "")
        (is (a/<!! made-call?))))))
