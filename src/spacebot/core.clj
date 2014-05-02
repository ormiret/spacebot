(ns spacebot.core
  (:gen-class)
  (:require [http.async.client :as http]
            [clojure.data.json :as json]
            [irclj.core :as irc]
            [irclj.events :as events]
            [clojure.string :as string]
            [clj-time.core :as t]))

(def config (read-string (slurp "config.clj")))

(defn respond-to [message]
  (if (= (config :nick) (message :target)) 
    (message :nick)
    (message :target)))

(defn get-from-web 
  "Fetch a page from the web and return its contents, or false if the request fails."
  [url & {:keys [timeout] :or [timeout 5000]}]
  (with-open [client (http/create-client)]
    (let [response (http/GET client url :timeout timeout)]
      (http/await response)
      (if (http/failed? response) 
        false
        (http/string response)))))


(defn get-status []
  (let [status (get-from-web "http://57north.co/spaceapi")]
    (if status
      ((json/read-str status) "state"))))

(defn status-message [status]
  (let [verb (if (status "open") "opened" "closed")]
    (str (status "trigger_person") " " verb " the space: " (status "message"))))

(def status (ref (get-status)))

(defn cah [irc msg]
  (let [page (get-from-web "http://doorbot.57north.co/cah.json")]
    (if (not page)
      (irc/message irc (respond-to msg) "doorbot didn't dispense any wisdom. *shrug*")
      (irc/message irc (respond-to msg) ((json/read-str page) "wisdom")))))

(defn rules [irc message]
  (let [page (get-from-web "https://raw.github.com/hackerdeen/rules/master/rules.md")
        target (respond-to message)]
    (if (not page)
        (irc/message irc target "Failed to get rules.")
        (let [number (re-find #"\d+" (message :text))
              rules (string/split-lines page)]
          (if (not number)
            (doseq [line rules]
              (irc/message irc target (string/trim line)))
            (let [index (+ (Integer. number) 2)]
              (if (> (count rules) index)
                (irc/message irc target (string/trim (nth rules index)))
                (irc/message irc target (str "There is no rule " number))))
            )))))

(defn sensors [irc msg]
  (let [spaceapi (get-from-web "http://57north.co/spaceapi")]
    (if (not spaceapi)
      (do (println (str "Return:" spaceapi))
          (irc/message irc (respond-to msg) "Failed to get sensor values."))
      (let [sensors ((json/read-str spaceapi) "sensors")
            temp ((first (sensors "temperature")) "value")
            humid ((first (sensors "humidity")) "value")]
        (irc/message irc (respond-to msg) (str "In the space the temperature is " 
                                               temp 
                                               "°C and the humidity is " 
                                               humid "%")))
      )))

(defn time-to [dt]
  (if (t/after? (t/now) dt)
    (rand-nth ["Any day now" "Didn't that happen already?" "Past" "Imminent" "Real Soon Now™" "yesterday(ish)" "A while ago" "Over there" "hippopotamus"])
  (let [left (t/interval (t/now) dt)
        days (t/in-days left)
        mins (t/in-minutes left)]
    (if (> days 30)
      (rand-nth ["Ages yet" "A good while" "Still well off" "Not soon" "Parakeet"])
      (if (>  days 15)
        (rand-nth ["A couple of weeks" "Weeks" "A few weeks" "A fortnight or so" "Less than a month" "Still enough time for a short holliday first" 
                   "Geoffrey" "A couple generations of fruit flys"])
        (if (> days 7)
          (rand-nth ["I think a week or so" "days" "Not all that soon" "Enough time to sober up" "About a fortnight" "long enough to run far away"
                     "A quarter moon or so"])
          (if (> days 1)
            (rand-nth ["days" "A while, less than a week" "TOO DAMN SOON" "elephant"])
            (if (> (* 12 60) mins)
              (rand-nth ["today... tomorrow... definitely this week." "Soon"])
              (if (> 60 mins)
                (rand-nth ["Shits coming up soon." "Are you ready for this?" "Enough time for one more beer first" "ostrich"])
                (rand-nth ["any minutes now" "IMMINENT" "rhinoceros" "Run now, save yourself!"]))))))))))

(defn time-cmd [irc msg]
  (let [target (respond-to msg)]
    (if (not (nil? (re-find #"(?i)hack.+make" (msg :text))))
      (irc/message irc target (time-to (t/date-time 2014 05 11 9)))
      (if (not (nil? (re-find #"(?i)campgnd" (msg :text))))
        (irc/message irc target (time-to (t/date-time 2014 06 27 17)))
        (irc/message irc target (rand-nth ["*shrug*" "I dunno" "Ye what now?" "How would I know?" "Piss off!" 
                                       "Why don't you ask someone who cares?"]))))))
                  
  

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
    (str "So far this month " current 
         " people have paid. Last month it was " previous ".")))

(defn get-membership-list []
  (let [page (get-from-web "http://hackerdeen.org/api/membership")]
    (if (not page)
        "Failed to get membership."
        ((json/read-str page) "membership"))))
        


(defn membership-histogram [irc msg]
  (doseq [month (take 4 (get-membership-list))]
          (irc/message irc (respond-to msg) 
                       (format "%4d/%02d: %s" (nth month 1) (nth month 0)
                               (string/join (repeat (nth month 2) "|"))))))

(defn membership [irc msg]
  (let [message (membership-message (get-membership-list))]
         (irc/message irc (respond-to msg) message)))

(defn llama [irc msg]
  (let [opening ["|   Yokohama the drama llama says...    |"
                 "----------------------------------------"]
        message " (Calm down Children)......              "
        tony   ["                           \\            "
                "                             \\          "
                "                               <)        "
                "                                (_---;  "	
                "                                /|~|\\  "
                "                               / / / \\ "
                "                                        "
                "----------------------------------------"]
        user-message (last (re-find #"(?i)^\?llama (.+)" (msg :text)))
        target (respond-to msg)]
    (doseq [line opening]
      (irc/message irc target line))
    (if (not (nil? user-message))
      (irc/message irc target (str " (" user-message ")" (if (< 24 (count user-message))
                                                           ""
                                                           (apply str (repeat (- 24 (count user-message)) ".")))))
      (irc/message irc target message))
    (doseq [line tony]
      (irc/message irc target line))))
      
(defn help-message [irc msg]
  (let [help ["Commands available:"
              "?membership - Give the number of people who've paid membership this month and last."
              "?histogram - Give a histogram of membership for the last four months"
              "?rules [n] - Give the rules, if n is supplied then you get rule n"
              "?sensors - Give readings from the sensors in the space"
              "?cah - Get some wisdom from doorbot playing cards against hackspace."
              "?llama [m] - Summon the drama llama, if m is given it is used as the message the llama will deliver"
              "?time hack'n'make|campGND - fuzzy countdown to events"
              "?help - This help text"
              "ping - Respond with pong"]]
    (doseq [line help]
      (irc/message irc (respond-to msg) line))))

(def commands [{:regex #"(?i)^\?membership" :func membership}
               {:regex #"(?i)^\?histogram" :func membership-histogram}
               {:regex #"(?i)^\?rules" :func rules}
               {:regex #"(?i)^\?sensors" :func sensors}
               {:regex #"(?i)^\?cah" :func cah}
               {:regex #"(?i)^\?llama" :func llama}
               {:regex #"(?i)^\?time" :func time-cmd}
               {:regex #"(?i)^ping" :func #(irc/message %1 (respond-to %2) "pong")}
               {:regex #"(?i)^\?help" :func help-message}
               ])


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

