(defproject wake-up-mr-west "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.391"]
                 [aleph "0.4.1"]
                 [cheshire "5.6.3"]
                 [danlentz/clj-uuid "0.1.6"]
                 [com.outpace/config "0.10.0"]
                 [compojure "1.5.1"]
                 [hiccup "1.0.5"]]
  :main ^:skip-aot wake-up-mr-west.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
