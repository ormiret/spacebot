(ns spacebot.core
  (:gen-class)
  (:require [http.async.client :as http]
            [clojure.data.json :as json]
            [irclj.core :as irc]
            [overtone.at-at :as at]))


(defn get-status []
  ((json/read-str
    (with-open [client (http/create-client)]
      (let [response (http/GET client "http://status.hackerdeen.org.uk/status.json")]
        (-> response
            http/await
            http/string)))) "state"))

(defn status-message [status]
  (let [verb (if (status "open") "opened" "closed")]
    (str (status "trigger_person") " " verb " the space: " (status "message"))))

(def status (ref {}))

(defn check-status [outfn]
  (let [prev-status @status
        cur-status (get-status)]
    (if (not= (prev-status "lastchange")
              (cur-status "lastchange"))
      (do
        (dosync (ref-set status cur-status))
        (outfn (status-message cur-status)))
        )))
         
(def bot (ref {}))
(def my-pool (at/mk-pool))

(defn connect []
  (let [refs (irc/connect "chat.freenode.net" 6666 "hackerdeenbot")]
  (irc/join refs "#hackerdeen")
  (dosync (ref-set bot refs))
  ))

(defn -main
  "Print status changes for hackerdeen"
  [& args]
  (connect)
  (irc/message @bot "#hackerdeen-test" "Hi")
  (let [update #(irc/message @bot "#hackerdeen" %)]
    (println "Running.")
    (at/every 1000 #(check-status update) my-pool))
  )

