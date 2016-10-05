(ns wake-up-mr-west.core-test
  (:require [clojure.test :refer :all]
            [wake-up-mr-west.core :refer :all]
            [clojure.core.async :as a]))

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
