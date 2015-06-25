(defproject worky "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0-RC1"]
                 [org.clojure/clojurescript "0.0-3308"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.cemerick/clojurescript.test "0.3.3"]
                 [com.cognitect/transit-cljs "0.8.220" :exclusions [[org.clojure/clojure]
                                                                    [org.clojure/clojurescript]]]
                 [servant "0.1.3" :exclusions [[org.clojure/clojure]
                                               [org.clojure/clojurescript]
                                               [org.clojure/core.async]
                                               [com.cemerick/clojurescript.test]]]
                 [datascript "0.11.4" :exclusions [[org.clojure/clojure]
                                                   [org.clojure/clojurescript]]]]

  :plugins [[lein-cljsbuild "1.0.6"]
            [lein-figwheel "0.3.3"]]

  :source-paths ["src"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src/main" "src/common"]

                        :figwheel { :on-jsload "worky.core/on-js-reload" }

                        :compiler {:main worky.core
                                   :asset-path "js/compiled/out"
                                   :output-to "resources/public/js/compiled/worky.js"
                                   :output-dir "resources/public/js/compiled/out"
                                   :source-map-timestamp true }}

                       {:id "worker"
                        :source-paths ["src/worker" "src/common"]

                        :compiler {:main worky.worker
                                   :output-to "resources/js/compiled/worky_worker.js"
                                   :optimizations :advanced
                                   :pretty-print false}}
                       {:id "min"
                        :source-paths ["src"]
                        :compiler {:output-to "resources/public/js/compiled/worky.js"
                                   :main worky.core
                                   :optimizations :advanced
                                   :pretty-print false}}]}

  :figwheel {
             ;; :http-server-root "public" ;; default and assumes "resources"
             ;; :server-port 3449 ;; default
             :css-dirs ["resources/public/css"] ;; watch and update CSS

             ;; Start an nREPL server into the running figwheel process
             ;; :nrepl-port 7888

             ;; Server Ring Handler (optional)
             ;; if you want to embed a ring handler into the figwheel http-kit
             ;; server, this is for simple ring servers, if this
             ;; doesn't work for you just run your own server :)
             ;; :ring-handler hello_world.server/handler

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             ;; if you want to disable the REPL
             ;; :repl false

             ;; to configure a different figwheel logfile path
             ;; :server-logfile "tmp/logs/figwheel-logfile.log"
             }
  :aliases {"figwheel" ["do" "clean," "cljsbuild" "once" "worker," "figwheel"]})
