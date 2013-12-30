(ns spacebot.core
  (:gen-class)
  (:require [http.async.client :as http]
            [clojure.data.json :as json]
            [irclj.core :as irc]
            [irclj.events :as events]
            [clojure.string :as string]))

(def nick (ref "hackerdeenbot"))
(def channel (ref "#hackerdeen"))


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

(defn membership-message [membership-list]
  (let [current (nth (first membership-list) 2)
        previous (nth (second membership-list) 2)]
    (str "So far this month " current " people have paid. Last month it was " previous ".")))

(defn get-membership-list []
  (println "Getting membership list...")
    (with-open [client (http/create-client)]
    (let [response (http/GET client "http://hackerdeen.org/api/membership" :timeout 1000)]
      (http/await response)
      (if (http/failed? response)
        "Failed to get membership."
        ((json/read-str (http/string response)) "membership"))
        )))


(defn membership-histogram [irc msg]
  (doseq [month (take 4 (get-membership-list))]
          (irc/message irc (msg :target) (format "%4d/%02d: %s" (nth month 1) (nth month 0)
                                                 (string/join (repeat (nth month 2) "|"))))))

(defn membership [irc msg]
  (let [message (membership-message (get-membership-list))]
         (irc/message irc (msg :target) message)))

(defn command [irc msg]
  (irc/message irc (msg :target) "That's a command?"))

(defn message [irc msg]
  (if (not (nil? (re-find #"^\?" (msg :text))))
    (if (not (nil? (re-find #"(?i)^\?membership" (msg :text))))
      (membership irc msg)
      (if (not (nil? (re-find #"(?i)^\?histogram" (msg :text))))
        (membership-histogram irc msg)
        (command irc msg))))
  (if (not (nil? (re-find #"(?i)^ping" (msg :text))))
    (irc/message irc (msg :target) "pong")))


(defn connect []
  (let [refs (irc/connect "chat.freenode.net" 6666 @nick :callbacks {:privmsg message
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

