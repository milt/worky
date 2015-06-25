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


     (defonce worker-count 4)
     (defonce worker-blob (js/Blob. #js [(slurp-worker)] (js-obj "type" "application/javascript")))
     (defonce servant-channel (servant/spawn-servants worker-count (.createObjectURL js/URL worker-blob)))

     (defn result-chan [fn-key args]
       (apply servant-thread-transit-with-key
              servant-channel
              transit-message
              fn-key
              args))

     (defn run
       "returns the result-channel with the result of (apply fn args)"
       [fn-key & args]
       (result-chan fn-key args))

     (defn run-with-response-fn
       "passes result to a fn"
       [response-fn fn-key & args]
       (let [result-channel (apply
                             run
                             fn-key
                             args)]
         (go
           (let [response (<! result-channel)]
             (response-fn response)))))

     (defn print-response
       "prints the response"
       [fn-key & args]
       (apply run-with-response-fn print fn-key args))

     ;; Datascript API stuff

     (defn q
       "returns a channel with the result of query"
       [q-form db & args]
       (apply run :q q-form db args))



     ;; some test fns
     (defn test-q []
         (print-response
          :q '[:find  ?n ?a
               :where [?e :aka "Maks Otto von Stirlitz"]
               [?e :name ?n]
               [?e :age  ?a]]
          (:db-after (d/with (d/empty-db {:aka {:db/cardinality :db.cardinality/many}})
                             [ { :db/id -1
                                :name  "Maksim"
                                :age   45
                                :aka   ["Maks Otto von Stirlitz", "Jack Ryan"] } ])
                     :eavt)))



     (defn on-js-reload []
       ;; optionally touch your app-state to force rerendering depending on
       ;; your application
       ;; (swap! app-state update-in [:__figwheel_counter] inc)
       )]))
