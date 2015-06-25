(ns ^:figwheel-always worky.core
    #?@(:cljs [(:require-macros
                [worky.core :refer [slurp-worker]]
                [servant.macros :refer [defservantfn]]
                [cljs.core.async.macros :as asyncm :refer (go go-loop)])
               (:require [worky.common :refer [ds-writer ds-reader]]
                         [servant.core :as servant]
                         [servant.worker :refer [worker-fn-map]]
                         [cljs.core.async :as async :refer (<! >! put! chan pub sub tap mult)]
                         [cognitect.transit :as t]
                         [cljs.reader :as reader]
                         [datascript :as d])]))

(do
  #?@(:clj
    [
     (defmacro slurp-worker []
       (slurp "resources/js/compiled/worky_worker.js"))
     ]
    :cljs
    [
     (enable-console-print!)


     (defn transit-message [worker fn-key args]
       (let [args (t/write ds-writer args)]
         (.postMessage worker (js-obj "command" "channel-transit" "fn" fn-key "args" args))))

     (defn servant-thread-transit-with-key [servant-channel post-message-fn fn-key & args]
       (let [out-channel (chan 1 (map (partial t/read ds-reader)))]
         (go
           (let [worker (<! servant-channel)]
             (post-message-fn worker (pr-str fn-key) args)
             ;; Add an event listener for the worker
             (.addEventListener worker "message"
                                #(go
                                   (>! out-channel (.-data %1))
                                   ;; return the worker back to the servant-channel
                                   (>! servant-channel worker)))))
         out-channel))


     (defonce worker-blob (js/Blob. #js [(slurp-worker)] (js-obj "type" "application/javascript")))

     (defn spawn-servants [worker-count & [path-or-blob]]
       (servant/spawn-servants worker-count (or path-or-blob
                                                (.createObjectURL js/URL worker-blob))))

     (defn result-chan [servant-channel fn-key args]
       (apply servant-thread-transit-with-key
              servant-channel
              transit-message
              fn-key
              args))

     (defn run
       "returns the result-channel with the result of (apply fn args)"
       [servant-channel fn-key & args]
       (result-chan servant-channel fn-key args))

     (defn run-with-response-fn
       "passes result to a fn"
       [servant-channel response-fn fn-key & args]
       (let [result-channel (apply
                             run
                             servant-channel
                             fn-key
                             args)]
         (go
           (let [response (<! result-channel)]
             (response-fn response)))))

     (defn print-response
       "prints the response"
       [servant-channel fn-key & args]
       (apply run-with-response-fn servant-channel print fn-key args))

     ;; Datascript API stuff

     (defn q
       "returns a channel with the result of query"
       [servant-channel q-form db & args]
       (apply run servant-channel :q q-form db args))


     ;; some test fns
     (defn test-q []
       (let [servant-channel (spawn-servants 1)]
         (print-response
          servant-channel
          :q '[:find  ?n ?a
               :where [?e :aka "Maks Otto von Stirlitz"]
               [?e :name ?n]
               [?e :age  ?a]]
          (:db-after (d/with (d/empty-db {:aka {:db/cardinality :db.cardinality/many}})
                             [ { :db/id -1
                                :name  "Maksim"
                                :age   45
                                :aka   ["Maks Otto von Stirlitz", "Jack Ryan"] } ])
                     :eavt))))



     (defn on-js-reload []
       ;; optionally touch your app-state to force rerendering depending on
       ;; your application
       ;; (swap! app-state update-in [:__figwheel_counter] inc)
       )]))
