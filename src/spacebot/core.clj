(ns spacebot.core
  (:gen-class)
  (:require [http.async.client :as http]
            [clojure.data.json :as json]
            [irclj.core :as irc]
            [irclj.events :as events]))

(def nick (ref "hackerdeenbot2"))
(def channel (ref "#hackerdeen-test"))


(defn get-status []
  (with-open [client (http/create-client)]
    (let [response (http/GET client "http://hackerdeen.org/spaceapi" :timeout 1000)]
      (http/await response)
      (if (http/failed? response)
          (do (println "Request failed.") false)
          ((json/read-str (http/string response)) "state")))))

(defn status-message [status]
  (let [verb (if (status "open") "opened" "closed")]
    (str (status "trigger_person") " " verb " the space: " (status "message"))))

(def status (ref (get-status)))

(defn check-status [outfn]
  (let [prev-status @status
        cur-status (get-status)]
    (println "Checking status.")
    (if (and cur-status
             (not= (prev-status "lastchange")
                   (cur-status "lastchange")))
      (do
        (dosync (ref-set status cur-status))
        (outfn (status-message cur-status)))
        )))
         
(def bot (ref {}))

(defn ping-pong [irc args] 
  (println args)
  (if (= (args :text) "ping")
    (irc/message @bot @channel "pong")))

(defn connect []
  (let [refs (irc/connect "chat.freenode.net" 6666 @nick :callbacks {:privmsg ping-pong
                                                                     :raw-log events/stdout-callback})]
  (irc/join refs @channel)
  (dosync (ref-set bot refs))
  ))

(defmacro forever [& body]
  `(loop [] ~@body (recur)))


(defn -main
  "Print status changes for hackerdeen"
  [& args]
  (connect)
  ;(irc/message @bot "#hackerdeen-test" "Hi")
  (let [update #(irc/message @bot @channel %)]
    (println "Running.")
    (forever (do (check-status update)
                 (Thread/sleep 60000)))
  ))

