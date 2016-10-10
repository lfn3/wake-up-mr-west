(ns wake-up-mr-west.twilio
  (:require [outpace.config :refer [defconfig]]
            [clojure.string :as str]
            [clojure.core.async :as a]
            [manifold.deferred :as d]
            [aleph.http :as http]))

(defconfig host)
(defconfig account-sid)
(defconfig auth-token)
(defconfig twilio-api-url "https://api.twilio.com/2010-04-01/")
(defconfig from)

(defn call-url [account]
  (str twilio-api-url "Accounts/" account "/Calls"))

;TODO: maybe this can be cleaned up with a real xml lib?
(defn say
  ([message]
   (say message "en-US"))
  ([message lang]
   (str/join \newline
             ["<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
              "<Response>"
              (str "<Say voice=\"alice\" language=\"" lang "\">")
              message
              "</Say>"
              "</Response>"])))

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
