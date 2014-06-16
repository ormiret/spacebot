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
  (with-open [client (http/create-client :follow-redirects true)]
    (let [response (http/GET client url :timeout timeout)]
      (http/await response)
      (if (http/failed? response) 
        false
        (http/string response)))))

(defn insult []
  (let [greetings ["greetings, you" "howdy, you" "oh, hi there, you" "screw me, you are a" "konichiwa! you are a"
                   "mabuhay, you Microsoft loving" "oi, you! you are a" "hello there, you" "salutations, you" 
                   "hey you, you" "ahoy there, you" "bonjour, you" "you, yes you," "<insert expletive here> off, you" "screw you. you are a" 
                   "good day, you" "are you shitting me? you" "welcome, you" "aloha, you frelling" "you are a" "Thou"]
        adjectives ["diseased" "stinky" "foul faced" "horrid breathed" "horrible" "soulless" "shockingly imbecilic"
                    "leathery" "decrepit" "malodorous" "porcine" "rotund" "recalcitrant" "bitter balled" "degenerate"
                    "pus-filled" "maggot-brained" "numb-nippled" "damn intolerant" "gunt cankled" "blistered" "dirtcaked" 
                    "scabby-faced" "rottencrotched" "shitstacked" "cream-faced" "three inch" "instinct lacking" "overly saucy"
                    "mite breeding" "smooth-tongue" "knot pated" "frelling" "fracking" "barbaric" "slug slimed"
                    "elderberry smelling" "not hoopy" "assimilated" "futile" "whining" "hypocritical"]
        verbs ["cockknocking" "banana bruising" "frenulum fraying" "toe stubbing" "mild cheese-eating" "jelly wearing"
               "ball roundling" "avocado juggling" "shart distilling" "sphincter loosening" "knuckle shuffling" "decaying"
               "grunting" "funk spraying" "bowel loosening" "stomach churning" "ugg wearing" "sock thieving" "gentoo using"
               "embossed" "corrupted" "tossed with" "C# writing" "arduino using" "Twilight reading" "freedom hating"
               "regex loving" "wombling"]
        nouns ["louse factory" "surrender monkey" "wardrobe knocker" "faeces burrito with double guacamole" 
               "gentoo user" "lebouef" "fromunda stain" "sock sniffer" "pit molester" "yoghurt squirter" "santorum sucker"
               "meat spinner" "menstrual cramp" "earwax scraping" "'pair of urchin gonads" "shit cynosure" "filth homunculus"
               "anus technician" "wart ninja" "sebaceous sycophant" "porcine packer" "fudge lieutenant" "marble manager"
               "shit olympian" "onanist" "jabber jowls" "masturbation merchant" "loon" "toad" "fat guts" "flesh monger"
               "bitch spawn" "motherlover" "labial leech" "pubis pretender" "puff pegger" "pleasure towel" "cheek jockey" 
               "balrog boffer" "pillow moistener" "stinkhole" "horse whisperer" "pooper cooper" "shart distiller" "fool"
               "ball boil" "enlightened monkey" "navel residue" "gunt display" "eyeball sore" "faeces sanctuary" "noob"
               "coward" "sculion" "fustilarian" "rampallian" "stewed prune" "boil" "plague sore" "carbuncle" "blood"
               "natural coward" "minion" "cut-throat" "weasel" "spleen" "cheese" "leathern-jerkin" "puke stocking"
               "muppet" "pillock" "trollop" "sod" "gannet" "chuffer" "twit" "windows user" "bad solderer" "redshirt"
               "smeghead" "shell script" "heathen" "community manager" "CTO" "management type" "borg" "nerf herder"
               "zergling" "rat guts in cat vomit" "cheesy scab"]
        ]
    (str (rand-nth greetings) " "
         (rand-nth adjectives) " "
         (rand-nth verbs) " "
         (rand-nth nouns))))

(defn blame [irc msg]
  (let [target (respond-to msg)]
    (irc/message irc target "It was Iain.")))

(defn insult-cmd [irc msg]
  (let [target (respond-to msg)
        person (last (re-find #"(?i)^\?insult (.+)" (msg :text)))]
    (if (not (nil? person))
      (irc/message irc target (str person ", "(insult)))
      (irc/message irc target (insult)))))



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
  (let [page (get-from-web "https://raw.githubusercontent.com/hackerdeen/rules/master/rules.md")
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
        (irc/message irc (respond-to msg) (str "In the space the temperature is probably not " 
                                               temp 
                                               "°C and the humidity is probably not " 
                                               humid "%")))
      )))

(defn status-of-stuff [irc msg]
  (let [target (respond-to msg)
        stuff [{:thing "Earth" :states ["was still spinning." "hadn't blown up."]}
               {:thing "the sun" :states ["was still busy fusing." "was a glowing ball of plasma." "was hot."]}
               {:thing "you" :states ["are quite annoying." "are behind on your quota." "need to get out more."
                                       "are doomed." "are doing OK." "look a bit funny" "need to try harder."
                                       "are a beautiful snowflake."]}
               {:thing "the space" :states ["was cashflow negative." "was too damn small." "wasn't on fire."
                                            "was in need of tidying."]}
               {:thing "the directors" :states ["are hard working and conscientious." "are a bunch of slackers."]}
               {:thing "the mailing list" :states ["was full of drama." "was a bit dull." "was generally ignored."]}
               {:thing "IRC" :states ["is for people with too much time on their hands." "promotes breaches of rule 1."]}
               {:thing "the rules" :states ["are a breach of my civil rights." "are politically incorrect." 
                                            "are overly restrictive." "are too vague." "are a tool of oppression."
                                            "are hindering my free expression."]}
               {:thing "hackhub" :states ["is going to be replaced." "is *not* going to be replaced." "is a thing of beauty."
                                          "is wonderfully stable." "is not fit for purpose." "has a glitch or two."]}
               {:thing "the website" :states ["was too damn pink." "was full of lies." "was insufficiently pink."]}
               ]
        object (rand-nth stuff)
        thing (:thing object)
        state (rand-nth (:states object))]
    (irc/message irc target (str "As of the last check, " thing " " state))))


(defn time-to [dt]
  (if (t/after? (t/now) dt)
    (rand-nth ["Didn't that happen already?" "Past" "Real Soon Now™" "yesterday(ish)" "A while ago" "Over there" "hippopotamus"])
  (let [left (t/interval (t/now) dt)
        days (t/in-days left)
        mins (t/in-minutes left)]
    (if (> days 30)
      (rand-nth ["Ages yet" "A good while" "Still well off" "Not soon" "Parakeet"])
      (if (>  days 15)
        (rand-nth ["A couple of weeks" "Weeks" "A few weeks" "A fortnight or so" "Less than a month" "Still enough time for a short holiday first" 
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
                (rand-nth ["any minutes now" "IMMINENT!!!11!" "rhinoceros" "Run now, save yourself!"]))))))))))

(defn time-cmd [irc msg]
  (let [target (respond-to msg)]
    (if (not (nil? (re-find #"(?i)hack.+make" (msg :text))))
      (irc/message irc target (time-to (t/date-time 2014 05 11 9)))
      (if (not (nil? (re-find #"(?i)campgnd" (msg :text))))
        (irc/message irc target (time-to (t/date-time 2014 6 27 17)))
        (if (not (nil? (re-find #"(?i)EMF" (msg :text))))
          (irc/message irc target (time-to (t/date-time 2014 8 29 12)))
          (if (not (nil? (re-find #"(?i)oggcamp" (msg :text))))
            (irc/message irc target (time-to (t/date-time 2014 10 4 9)))
            (irc/message irc target (rand-nth ["*shrug*" "I dunno" "Ye what now?" "How would I know?" "Piss off!" 
                                               "Why don't you ask someone who cares?"]))))))))
(defn readable-events [events]
  (map #(str ((% "start") "displaylocal") " " (% "summaryDisplay")) events))
          
(defn events [irc msg]
  (let [target (respond-to msg)
        event-list (readable-events 
                    ((json/read-str (get-from-web 
                                     "http://opentechcalendar.co.uk/api1/group/151/events.json")) 
                     "data"))
        ]
    (doseq [event (take 4 event-list)]
      (irc/message irc target event))))
    

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
              "?insult [object] - Generate an insult"
              "?events - list some upcoming space events"
              "?status - get the status of something"
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
               {:regex #"(?i)^\?insult" :func insult-cmd}
               {:regex #"(?i)^\?blame" :func blame}
               {:regex #"(?i)^\?events" :func events}
               {:regex #"(?i)^\?status" :func status-of-stuff}
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

