(ns wake-up-mr-west.core
  (:require [aleph.http :as http]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.core.async :as a]
            [clj-uuid :as uuid]
            [manifold.deferred :as d]
            [outpace.config :refer [defconfig]])
  (:gen-class)
  (:import (java.util Date)))

(defonce srv (atom nil))
(defonce state (atom {}))                                   ;TODO: replace this with a database

(defconfig account-sid)
(defconfig auth-token)
(defconfig twilio-api-url "https://api.twilio.com/2010-04-01/")
(defconfig from)
(defconfig to)
(defconfig port 8080)
(defconfig host)

(defn call-url [account]
  (str twilio-api-url "Accounts/" account "/Calls"))

(defn say
  ([message]
    (say message "en-US"))
  ([message lang]
   (str/join \n
             ["<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
              "<Response>"
              (str "<Say voice=\"alice\" language=\"" lang "\"")
              message
              "</Say>"
              "</Response>"])))

;TODO for real routing.
(defn handler [req]
  {:status 200
   :headers {"content-type" "application/xml"}
   :body (say "Wake up Mr. West")})

(defn build-twilio-url [id]
  (str host "/twilio/" id))

(defn make-call [number url]
  (let [ch (a/promise-chan)]
    (d/chain
      (http/post (call-url account-sid) {:basic-auth (str account-sid ":" auth-token)
                                         :form-params {:From from
                                                       :To number
                                                       :Url url}})
      #(a/put! %1 ch))
    ch))

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
    (let [result (a/<! (make-call number (build-twilio-url id)))]
      (when (instance? Exception result)
        (throw result)))))

(defn stash [data]
  (swap! state assoc (:id data) data))

(defn get-words-for-id [id]
  (-> @state
      (get id)
      :words))

(defn add [^Date date ^String words ^String number]
  (let [data {:id (uuid/v1)
              :time date
              :words words
              :number number
              :done false}]
    (stash data)
    (go-delayed-call data)))

(defn -main
  "Start a phone call"
  [& args]
  (add (Date.) "Wake up mr. west" to)
  #_(-> @(http/post (call-url account-sid) {:basic-auth (str account-sid ":" auth-token)
                                          :form-params {:From from
                                                        :To to
                                                        :Url "http://10e32fff.ngrok.io"}})
      :body
      (io/reader)
      (slurp)
      (println))
  (reset! srv (http/start-server handler {:port port})))
