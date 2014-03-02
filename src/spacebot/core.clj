(ns spacebot.core
  (:gen-class)
  (:require [http.async.client :as http]
            [clojure.data.json :as json]
            [irclj.core :as irc]
            [irclj.events :as events]
            [clojure.string :as string]))

(def config (read-string (slurp "config.clj")))

(defn respond-to [message]
  (if (= (config :nick) (message :target)) 
    (message :nick)
    (message :target)))

(defn get-status []
  (with-open [client (http/create-client)]
    (let [response (http/GET client "http://57north.co/spaceapi" :timeout 5000)]
      (http/await response)
      (if (http/failed? response)
          (do (println "Request failed.") false)
          ((json/read-str (http/string response)) "state")))))

(defn status-message [status]
  (let [verb (if (status "open") "opened" "closed")]
    (str (status "trigger_person") " " verb " the space: " (status "message"))))

(def status (ref (get-status)))

(defn rules [irc message]
  (with-open [client (http/create-client)]
    (let [response (http/GET client "https://raw.github.com/hackerdeen/rules/master/rules.md"
                             :timeout 5000)
          target (respond-to message)]
      (http/await response)
      (if (http/failed? response)
        (irc/message irc target "Failed to get rules.")
        (let [number (re-find #"\d+" (message :text))
              rules (string/split-lines (http/string response))]
          (if (not (nil? number))
            (let [index (+ (Integer. number) 2)]
              (if (> (count rules) index)
                (irc/message irc target (string/trim (nth rules index)))
                (irc/message irc target (str "There is no rule " (- index 2)))))
            (doseq [line (string/split-lines (http/string response))]
              (irc/message irc target (string/trim line)))))))))

(defn get-temperature []
  (with-open [client (http/create-client)]
    (let [response (http/GET client "http://whiteboard.57north.co/json.php" :timeout 5000)]
      (http/await response)
      (if (http/failed? response)
          (do (println "Request failed.") false)
          ((json/read-str (http/string response)) "temperature")))))

(defn get-humidity []
  (with-open [client (http/create-client)]
    (let [response (http/GET client "http://whiteboard.57north.co/json.php" :timeout 5000)]
      (http/await response)
      (if (http/failed? response)
          (do (println "Request failed.") false)
          ((json/read-str (http/string response)) "humidity")))))

(defn sensors [irc msg]
  (let [txt (str "In the space the temperature is " (get-temperature) 
                 "°C and the humidity is " (get-humidity) "%")]
      (irc/message irc (respond-to msg) txt)))

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

(defn membership-message [membership-list]
  (let [current (nth (first membership-list) 2)
        previous (nth (second membership-list) 2)]
    (str "So far this month " current " people have paid. Last month it was " previous ".")))

(defn get-membership-list []
  (println "Getting membership list...")
    (with-open [client (http/create-client)]
    (let [response (http/GET client "http://hackerdeen.org/api/membership" :timeout 5000)]
      (http/await response)
      (if (http/failed? response)
        "Failed to get membership."
        ((json/read-str (http/string response)) "membership"))
        )))


(defn membership-histogram [irc msg]
  (doseq [month (take 4 (get-membership-list))]
          (irc/message irc (respond-to msg) (format "%4d/%02d: %s" (nth month 1) (nth month 0)
                                                 (string/join (repeat (nth month 2) "|"))))))

(defn membership [irc msg]
  (let [message (membership-message (get-membership-list))]
         (irc/message irc (respond-to msg) message)))

(defn help-message [irc msg]
  (let [help ["Commands available:"
              "?membership - Give the number of people who've paid membership this month and last."
              "?histogram - Give a histogram of membership for the last four months"
              "?rules [n] - Give the rules, if n is supplied then you get rule n"
              "?sensors - Give readings from the sensors in the space"
              "?help - This help text"
              "ping - Respond with pong"]]
    (doseq [line help]
      (irc/message irc (respond-to msg) line))))

(def commands [{:regex #"(?i)^\?membership" :func membership}
               {:regex #"(?i)^\?histogram" :func membership-histogram}
               {:regex #"(?i)^\?rules" :func rules}
               {:regex #"(?i)^\?sensors" :func sensors}
               {:regex #"(?i)^ping" :func #(irc/message %1 (respond-to %2) "pong")}
               {:regex #"(?i)^\?help" :func help-message}])


(defn message [irc msg]
  (doseq [command commands]
    (if (not (nil? (re-find (command :regex) (msg :text))))
      ((command :func) irc msg))))


(defn connect []
  (let [refs (irc/connect "chat.freenode.net" 6666 (config :nick) :callbacks {:privmsg message
                                                                     :raw-log events/stdout-callback})]
  (doseq [channel (config :channels)]
    (irc/join refs channel))
  (dosync (ref-set bot refs))
  ))

(defmacro forever [& body]
  `(loop [] ~@body (recur)))


(defn -main
  "Print status changes for 57North"
  [& args]
  (connect)
  ;(irc/message @bot "#hackerdeen-test" "Hi")
  (let [update #(doseq [channel (config :channels)] (irc/message @bot channel %))]
    (println "Running.")
    (forever (do (check-status update)
                 (Thread/sleep 60000)))
  ))

