(defproject clj-drivethru "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/clojurescript "1.11.54"]

                 [ca.inaimathi/trivial-openai "0.0.0"]

                 [http-kit "2.6.0"]
                 [ring "1.9.2"]
                 [bidi "2.1.6"]
                 [clojure-watch "0.1.11"]

                 [cheshire "5.11.0"]
                 [hiccup "1.0.5"]

                 [reagent "1.0.0"]

                 [overtone "0.10.3"]]
  :repl-options {:init-ns clj-drivethru.core})
