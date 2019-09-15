(ns spacebot.core
  (:gen-class)
  (:require [http.async.client :as http]
            [clojure.data.json :as json]
            [irclj.core :as irc]
            [irclj.events :as events]
            [clojure.string :as string]
            [clj-time.core :as t]
            [clj-http.client :as client]))

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
                   "hey you, you" "ahoy there, you" "bonjour, you" "you, yes you," "<insert expletive here> off, you"
                   "screw you. you are a" 
                   "good day, you" "are you shitting me? you" "welcome, you" "aloha, you frelling" "you are a" "Thou"]
        adjectives ["diseased" "stinky" "foul faced" "horrid breathed" "horrible" "soulless" "shockingly imbecilic"
                    "leathery" "decrepit" "malodorous" "porcine" "rotund" "recalcitrant" "bitter balled" "degenerate"
                    "pus-filled" "maggot-brained" "numb-nippled" "damn intolerant" "gunt cankled" "blistered" "dirtcaked" 
                    "scabby-faced" "rottencrotched" "shitstacked" "cream-faced" "3.5 inch" "instinct lacking" "overly saucy"
                    "mite breeding" "smooth-tongue" "knot pated" "frelling" "fracking" "barbaric" "slug slimed" "betwattled"
                    "elderberry smelling" "not hoopy" "assimilated" "futile" "whining" "hypocritical" "socialist" "capitalist"
                    "hooper-arsed"]
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
               "zergling" "rat guts in cat vomit" "cheesy scab" "noisy power rail" "capacitor" "resistor" "thermistor" 
               "SCSI disk" "scientologist" "jackanapes" "twiddle-poop" "sneaksby" "meater" "whooperups"]
        ]
    (str (rand-nth greetings) " "
         (rand-nth adjectives) " "
         (rand-nth verbs) " "
         (rand-nth nouns))))

(defn blame [irc msg]
  (let [target (respond-to msg)
        person (rand-nth ["Iain" "Shell" "[tj]" "ormiret" "Nordin" "irl" "WSPR" "hibby" "noodle" "DocOcassi" "Andy"])]
    (irc/message irc target (str "It was " person "."))))

(defn insult-cmd [irc msg & [f]]
  (let [target (respond-to msg)
        person (last (re-find #"(?i)^\?insult (.+)" (msg :text)))
        fun (if f f irc/message)]
    (if (not (nil? person))
      (fun irc target (str person ": "(insult)))
      (fun irc target (insult)))))

(defn get-status []
  (let [status (get-from-web "http://57north.org.uk/spaceapi")]
    (if status
      ((json/read-str status) "state"))))

(defn status-message [status]
  (let [verb (if (status "open") "opened" "closed")]
    (str (status "trigger_person") " " verb " the space: " (status "message"))))

(def status (ref (get-status)))

(defn cah [irc msg & [f]]
  (let [topic (last (re-find #"(?i)\?cah\s+(\S+)" (msg :text)))
        page (if (nil? topic) 
               (get-from-web "http://idea.bodaegl.com/cah.json")
               (get-from-web (str "http://idea.bodaegl.com/cah.json/" topic)))
        fun (if f f irc/message)]
    (if (not page)
      (fun irc (respond-to msg) "doorbot didn't dispense any wisdom. *shrug*")
      (fun irc (respond-to msg) ((json/read-str page) "wisdom")))))

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
  (let [spaceapi (get-from-web "http://57north.org.uk/spaceapi")]
    (if (not spaceapi)
      (do (println (str "Return:" spaceapi))
          (irc/message irc (respond-to msg) "Failed to get sensor values."))
      (let [sensors ((json/read-str spaceapi) "sensors")
            temp ((first (sensors "temperature")) "value")
            humid ((first (sensors "humidity")) "value")]
        (irc/message irc (respond-to msg) (str "In the space the temperature is probably not " 
                                               temp 
                                               "°C and the humidity is unlikely to be  " 
                                               humid "% Someone should really fix the sensors.")))
      )))

(defn status-of-stuff [irc msg & [f]] 
  (let [target (respond-to msg)
        fun (if f f irc/message)
        stuff [{:thing "Earth" :states ["is still spinning." "hasn't blown up."]}
               {:thing "The sun" :states ["was still busy fusing." "was a glowing ball of plasma." "was hot."
                                          "is over there."]}
               {:thing "You" :states ["are quite annoying." "are behind on your quota." "need to get out more."
                                      "are doomed." "are doing OK." "look a bit funny" "need to try harder."
                                      "are a beautiful snowflake."]}
               {:thing "The space" :states ["was cashflow negative." "was too damn small." "wasn't on fire."
                                            "was in need of tidying." "is about to go bust" "needs new blood."
                                            "needs more members." "should just be closed."]}
               {:thing "The directors" :states ["are hard working and conscientious." "are trying."
                                                "will need to cough up their £1"]}
               {:thing "The mailing list" :states ["was full of drama." "was a bit dull." "was generally ignored."
                                                   "had posts that should be elsewhere."]}
               {:thing "IRC" :states ["is for people with too much time on their hands." "promotes breaches of rule 1."
                                      "is generally unexcellent."]}
               {:thing "The rules" :states ["are a breach of my civil rights." "are politically incorrect." 
                                            "are overly restrictive." "are too vague." "are a tool of oppression."
                                            "are hindering my free expression."]}
               {:thing "Hackhub" :states ["is going to be replaced." "is *not* going to be replaced." "is a thing of beauty."
                                          "is wonderfully stable." "is not fit for purpose." "has a glitch or two."]}
               {:thing "The website" :states ["was too damn pink." "was full of lies." "was insufficiently pink."
                                              "was bueatiful."]}
               {:thing "The wiki" :states ["is on an unauthorised server." "is out of control." "needs to be replaced." 
                                           "has out of date information." "should be done away with." "contains libel."
                                           "is not as good as the old one."
                                           "*still* doesn't have all the stuff from the old wiki."]}
               ]
        object (rand-nth stuff)
        thing (:thing object)
        state (rand-nth (:states object))]
    (fun irc target (str thing " " state))))

(defn time-to [dt]
  (if (t/after? (t/now) dt)
    (rand-nth ["Didn't that happen already?" "Past" "Real Soon Now™" "yesterday(ish)" "A while ago" 
               "Over there" "hippopotamus"])
    (let [left (t/interval (t/now) dt)
          days (t/in-days left)
          mins (t/in-minutes left)]
      (if (> days 365)
        (rand-nth ["Ages yet" "A good while" "Still well off" "Not soon"
                   "Once more around the sun (and then some)" "Parakeet"])
        (if (> days 60)
          (rand-nth ["Months" "Long enough for us to go bust"
                     "Enough time to brew some beer" "Still well off"])
          (if (> days 30)
            (rand-nth ["Weeks." "Maybe long enough to get the 3D printer working" 
                       "Somewhere in the region of 2 to 4 million lunar distances over c"])
            (if (>  days 15)
              (rand-nth ["A couple of weeks" "Weeks" "A few weeks" "A fortnight or so" "Less than a month" 
                         "Still enough time for a short holiday first" 
                         "Geoffrey" "A couple generations of fruit flys"])
              (if (> days 7)
                (rand-nth ["I think a week or so" "days" "Not all that soon" "Enough time to sober up" 
                           "About a fortnight" "long enough to run far away"
                           "A quarter moon or so"])
                (if (> days 1)
                  (rand-nth ["days" "A while, less than a week" "TOO DAMN SOON" "elephant"])
                  (if (> (* 12 60) mins)
                    (rand-nth ["today... tomorrow... definitely this week." "Soon"])
                    (if (> 60 mins)
                      (rand-nth ["Shit's coming up soon." "Are you ready for this?"
                                 "Enough time for one more beer first"  "ostrich"])
                      (rand-nth ["any minutes now" "IMMINENT!!!11!" "rhinoceros"
                                 "Run now, save yourself!"]))))))))))))
    (defn match-event 
  "Return the time for the first event where the regex matches."
  [events message]
  (loop [e events]
    (if (seq e)
      (if (not (nil? (re-find ((first e) :regex) message)))
        (:time (first e))
        (recur (next e))))))

(defn time-cmd [irc msg]
  (let [target (respond-to msg)
        events (load-file "events.clj")
        event (match-event events (msg :text))]
    (if (nil? event)
      (irc/message irc target (rand-nth ["I don't know when that happens."
                                         "How should I know?"
                                         "I don't have that information."
                                         "That has been classified."
                                         "The ~citrus fruit~ said not to tell you."
                                         "You are not cleared for that information."
                                         "It's complicated"
                                         "Umm, let me get back to you."
                                         "I'm sure I wrote that down somewhere."
                                         "E_BAD_QUESTION"
                                         "*shrug*"
                                         "Time for you to buck up"]))
      (irc/message irc target (time-to event)))))

(defn time-list [irc msg]
  (let [target (respond-to msg)
        events (load-file "events.clj")]
    (irc/message irc target (str "I can count down to: " (apply str (interpose ", " (map :desc events)))))))
                                         
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
(def conn-time (ref {}))
(def bored (ref {}))
(def bscnt (ref {}))

(defn membership-message [membership-list]
  (let [current (nth (first membership-list) 2)
        previous (nth (second membership-list) 2)]
    (str "So far this month " current 
         " people have paid. Last month it was " previous ".")))

(defn get-membership-list []
  (let [page (get-from-web "http://hub.57north.org.uk/api/membership")]
    (if (not page)
      "Failed to get membership."
      ((json/read-str page) "membership"))))

(defn membership-histogram [irc msg]
  (doseq [month (take 4 (get-membership-list))]
    (irc/message irc (respond-to msg) 
                 (format "%4d/%02d: %02d %s" (nth month 1) (nth month 0)
                         (nth month 2)
                         (string/join (repeat (nth month 2) "|"))))))

(defn membership [irc msg]
  (let [message (membership-message (get-membership-list))]
    (irc/message irc (respond-to msg) message)))

(defn unicorn [irc msg]
  (let [art ["             |\\ "
             "              \\\\______"
             "              /    \\  \\_ "
             "             / \u000313 O\u000F   \\   \\ "
             "            /  __     \\  | "
             "           (__/  /     \\/ " 
             "                /        \\__\u000304__\u000F"
             "            __--            \u000304/ \\______\u000F"
             "          --___/           \u000307/\\        \\__\u000F   "
             "         ( (  __----     \u000303_/  \\__\u000310___    /\u000F___"
             "          || (     \\___ \u000306/ \\_       \\  /\u000F    \\ "
             "           -\\_\\        \u000310\\_   \\___    \\/\u000F  /__/ "
             "             \\_\\         \u000308\\___   \\__ /\u000F\\ /  "
             "                           _/\u000312\\_____/\u000F  \\\\ "
             "                          /  __/ _/    `"
             "                         |__/  _/ "
             "                        /__\\__/ "
             "                           /__| "]
        target (respond-to msg)]
    (doseq [line art]
      (irc/message irc target line)
      (Thread/sleep 1000))))
                     

(defn llama [irc msg]
  (let [opening ["|   Yokohama the drama llama says...    |"
                 "----------------------------------------"]
        message (rand-nth 
                 [" (Calm down Children)......              "
                  " (BURN THE BUILDING TO THE GROUND)       "
                  " (FIGHT! FIGHT! FIGHT! FIGHT!)"
                  " (Don't make me come over there.)"
                  " ( *sigh* )................"
                  " (I'm not angry. I'm disappointed.)"
                  " (Could you not? Please?).."
                  " (Rule 2).................."])

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

(defn use-quest [irc msg]
  (let [target (respond-to msg)]
    (irc/message irc target (insult))
    (irc/message irc target "Commands start with a ?")))

(def commands [{:regex #"(?i)^\?membership" :func membership}
               {:regex #"(?i)^\?histogram" :func membership-histogram}
               {:regex #"(?i)^\?rule" :func rules}
               {:regex #"(?i)^\?sensors" :func sensors}
               {:regex #"(?i)^\?cah" :func cah}
               {:regex #"(?i)^\?llama" :func llama}
               {:regex #"(?i)^\?insult" :func insult-cmd}
               {:regex #"(?i)^\?blame" :func blame}
               {:regex #"(?i)^\?events" :func events}
               {:regex #"(?i)^\?status" :func status-of-stuff}
               {:regex #"(?i)^\?time-list" :func time-list}
               {:regex #"(?i)^\?time " :func time-cmd}
               {:regex #"(?i)^ping" :func #(irc/message %1 (respond-to %2) "pong")}
               {:regex #"(?i)^\?((unicorn)|(gary))" :func unicorn}
               {:regex #"(?i)^\?links" :func #(irc/message
                                               %1
                                               (respond-to %2) 
                                               "hackercat collects links from the channel at https://57north.org.uk/irclinks")}
               {:regex #"(?i)^\?source" :func #(irc/message %1 (respond-to %2)
                                                            "My source is at https://github.com/ormiret/spacebot")}
               {:regex #"(?i)^\?help" :func #(irc/message %1 (respond-to %2)
                                                          "Available commands are listed at https://github.com/ormiret/spacebot/blob/master/help.md")}
               {:regex #"^!\w+" :func use-quest}
               ])


(defn activity []
  (dosync (ref-set bored (t/plus (t/now) (t/minutes (+ 15 (rand-int 240)))))
          (ref-set bscnt 0)))

(defn unaddressed [msg]
  (string/replace msg (re-pattern (str "(?i)"  (config :nick) "[:,]?\\s+")) ""))

(defn message [irc msg]
  (activity)
  (doseq [command commands]
    (if (not (nil? (re-find (command :regex) (unaddressed (msg :text)))))
      (try 
        ((command :func) irc (update msg :text unaddressed))
        (catch Exception e (irc/message irc (respond-to msg) (str "FAIL: " (.getMessage e))))))))


(defn reconnect [connect & rest]
  (if (> 30 (t/in-seconds (t/interval @conn-time (t/now))))
    (connect)
    (do (Thread/sleep 60000)
        (connect))))


(defn connect []
    (dosync (ref-set conn-time (t/now)))
  (let [refs (irc/connect "irc.freenode.net" 6666 (config :nick)
                          :callbacks {:privmsg message
                                      :raw-log events/stdout-callback
                                      :on-shutdown (partial reconnect connect)})]
      (if (contains? config :pass)
        (irc/identify refs (config :pass)))
      (Thread/sleep 30000)
      (doseq [channel (config :channels)]
        (irc/join refs channel))
      
      (dosync (ref-set bot refs))
      ))


(defn check-bored []
  (if (and (t/after? (t/now) @bored)
           (contains? config :bored)
           (< @bscnt 3))
    (try
      (let [num (rand-int 100)
            new_bscnt (+ 1 @bscnt)]
        (cond
         (< num 2) (status-of-stuff @bot {:target (config :bored)} irc/notice) 
         (< num 8) (insult-cmd @bot {:target (config :bored) :text "?insult"} irc/notice)
         :else (cah @bot {:target (config :bored) :text "?cah"} irc/notice))
        (activity)
        (dosync (ref-set bscnt new_bscnt)))
       (catch Exception e (println (str "FAIL" e))))     
    (println (str "Not bored yet. Wating till " @bored " or for activity (bscnt: " @bscnt ")"))))


(defmacro forever [& body]
  `(loop [] ~@body (recur)))


(defn -main
  "Print status changes for 57North"
  [& args]
  (connect)
  (activity)
                                        ;(irc/message @bot "#hackerdeen-test" "Hi")
  (let [update #(doseq [channel (config :channels)] (irc/message @bot channel %))]
    (println "Running.")
    (forever (do (check-status update)
                 (check-bored)
                 (Thread/sleep 60000)))
    ))

