(ns wake-up-mr-west.core
  (:require [aleph.http :as http]
            [clojure.core.async :as a]
            [clj-uuid :as uuid]
            [outpace.config :refer [defconfig]]
            [compojure.core :refer [GET POST defroutes]]
            [wake-up-mr-west.twilio :as twil]
            [hiccup.core :as hic]
            [hiccup.page :refer [doctype]]
            [hiccup.form :as form]
            [compojure.route :refer [resources not-found]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]])
  (:gen-class)
  (:import (java.util Date)))

(defonce state (atom {}))                                   ;TODO: replace this with a database

(defconfig to)
(defconfig port 8080)

(defn get-words-for-id [id]
  (-> @state
      (get id)
      :words))

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

;TODO move to another ns?
(defn html-response [headers & body]
  (let [body (if-not (map? headers)                         ;TODO: tidy this up, it's kinda gross.
               (cons headers body)
               body)
        headers (if (map? headers)
                  (merge {"content-type" "text/html"} headers)
                  {"content-type" "text/html"})]
    {:status 200
     :headers headers
     :body (hic/html
             (doctype :html5)
             [:html
              [:body body]])}))

(defroutes app-routes
  (GET "/" []
    (html-response
      [:h1 "Wake up mr. west"]
      (form/form-to [:post "/make-call/"]
        (form/label "to-number" "Number to call")
        (form/text-field {:type "tel"} "to-number"))))
  (POST "/make-call/" [to-number]
    (add (Date.) "Wake up mr. west" to-number)
    (html-response [:p (str "Calling " to-number)]))
  (POST "/twilio/:id" [id]                                  ;TODO move this into the twilio ns?
    (let [id (clj-uuid/as-uuid id)]
      (swap! state update-in [id :done] not)
      {:status 200
       :headers {"content-type" "application/xml"}
       :body (twil/say (get-words-for-id id))}))
  (resources "/")
  (not-found "Sorry"))

;TODO use mount?
(defonce srv (atom nil))

(defn -main
  "Start a phone call"
  [& args]
  (reset! srv (http/start-server (wrap-params (wrap-keyword-params app-routes)) {:port port}))
  (comment (add (Date.) "Wake up mr. west" to)
           (a/<!! (wait-for state #(->> %1
                                        (vals)
                                        (every? :done))))))
