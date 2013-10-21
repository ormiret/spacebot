(ns spacebot.core
  (:gen-class)
  (:require [http.async.client :as http])
  (:require [clojure.data.json :as json]))

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

(defn check-status [outf]
  (let [prev-status @status
        cur-status (get-status)]
    (if (= (prev-status "lastchanged")
           (cur-status "lastchanged"))
      (do
        (outf (status-message cur-status))
        (dosync (ref-set status cur-status))))))
         

(defn -main
  "Print status changes for hackerdeen"
  [& args]
  (check-status println))

