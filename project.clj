(defproject dribble-stats "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [clj-http "3.3.0"]
                 [cheshire "5.6.3"]
                 [org.clojure/core.async "0.2.391"]
                 [midje "1.8.3"]]
  :plugins [[lein-midje "3.1.3"]]
  :main dribble-stats.core)
