(defproject exp-condition "1.0.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://chdp.usask.ca:5000"
  :license {:name "FIXME: choose"
            :url "http://example.com/FIXME"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.8"]
                 [ring/ring-jetty-adapter "1.3.0"]
                 [ring/ring-devel "1.3.0"]
                 [ring/ring-json "0.3.1"]
                 [com.novemberain/monger "2.0.0-rc1"]
                 [cheshire "5.3.1"]
                 [org.clojure/tools.nrepl "0.2.3"]
                 [clojure-complete "0.2.3"]
                 [ring-basic-authentication "1.0.5"]
                 [nrepl-inspect "0.4.1"]
                 [ring.middleware.jsonp "0.1.4"]
                 [commons-codec "1.9"]
                 [clj-http "0.9.2"]
                 [clj-time "0.7.0"]
                 [org.clojure/tools.trace "0.7.8"]
                 ;[com.smnirven/biomass "0.5.1"]
                 ;[com.github.kyleburton/clj-xpath "1.4.3"]
                 ;;[local/x2j "1.0.0"]
                 ;[cpoile/xml-to-clj "0.9.0"]
                 [org.clojure/math.numeric-tower "0.0.4"]]
  :repl-options {:nrepl-middleware [inspector.middleware/wrap-inspect
                                    ;; ritz.nrepl.middleware.javadoc/wrap-javadoc
                                    ;; ritz.nrepl.middleware.apropos/wrap-apropos
                                    ]}
  :min-lein-version "2.0.0"
  :profiles {:production {:env {:production true}}
             :dev {:env {:production false
                         :dev true}}}
  :resource-paths ["resources"]
  :repositories {"project" "file:repo"}
  :main exp-condition.exp-condition-web)
