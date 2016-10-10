(ns wake-up-mr-west.core
  (:require [aleph.http :as http]
            [clojure.core.async :as a]
            [clj-uuid :as uuid]
            [outpace.config :refer [defconfig]]
            [compojure.core :refer [POST defroutes]]
            [wake-up-mr-west.twilio :as twil])
  (:gen-class)
  (:import (java.util Date)))

(defonce state (atom {}))                                   ;TODO: replace this with a database

(defconfig to)
(defconfig port 8080)

(defn get-words-for-id [id]
  (-> @state
      (get id)
      :words))

(defroutes app-routes
  (POST "/twilio/:id" [id]
    (let [id (clj-uuid/as-uuid id)]
      (swap! state update-in [id :done] not)
      {:status 200
       :headers {"content-type" "application/xml"}
       :body (twil/say (get-words-for-id id))})))

(defn datetime->timeout [^Date date]
  (a/timeout (- (.getTime date) (System/currentTimeMillis))))

(def default-retries 5)

(defmacro go-with-retries
  "This won't work if you pass a symbol at the moment."
  [retries & body]
  ;TODO should be a better way of doing this...
  (let [body (if-not (integer? retries)
               (cons retries body)
               body)
        retries (if-not (integer? retries)
                  default-retries
                  retries)]
    `(a/go-loop [counter# 1]
       (let [result# (try ~@body
                          (catch Exception e#
                            e#))]
         (if (and (instance? Exception result#)
                    (< counter# ~retries))
           (recur (inc counter#))
           result#)))))

(defn go-delayed-call [{:keys [id time number]}]
  (go-with-retries
    (a/<! (datetime->timeout time))
    (let [result (a/<! (twil/make-call number (twil/build-twilio-url id)))]
      (when (instance? Exception result)
        (throw result)))))

(defn stash [data]
  (swap! state assoc (:id data) data))

(defn add [^Date date ^String words ^String number]
  (let [data {:id (uuid/v1)
              :time date
              :words words
              :number number
              :done false}]
    (stash data)
    (go-delayed-call data)))

(defn wait-for [atom fn]
  (a/go-loop [now @atom]
    (a/<! (a/timeout 10000))
    (when-not (fn now)
      (recur @atom))))

(defn -main
  "Start a phone call"
  [& args]
  (with-open [_ (http/start-server app-routes {:port port})]
    (add (Date.) "Wake up mr. west" to)
    (a/<!! (wait-for state #(->> %1
                                 (vals)
                                 (every? :done))))))
