(defproject spacebot "0.1.0-SNAPSHOT"
  :description "Simple IRC bot to update an IRC channel of status changes for a hackspace"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [irclj "0.5.0-alpha3"]
                 [org.clojure/data.json "0.2.3"]
                 [http.async.client "0.5.2"]
                 [overtone/at-at "1.2.0"]]
  :main ^:skip-aot spacebot.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
