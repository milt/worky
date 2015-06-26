(ns ^:figwheel-always worky.core
    #?@(:cljs [(:require-macros
                [worky.core :refer [slurp-worker]]
                [servant.macros :refer [defservantfn]]
                [cljs.core.async.macros :as asyncm :refer (go go-loop)])
               (:require [worky.common :refer [ds-writer ds-reader]]
                         [servant.core :as servant]
                         [servant.worker :refer [worker-fn-map]]
                         [cljs.core.async :as async :refer (<! >! put! chan pub sub tap mult close!)]
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
     ;(enable-console-print!)

     (defonce kill-servants ;heh
       servant/kill-servants)

     (defn transit-message [worker fn-key args]
       (let [args (t/write ds-writer args)]
         (.postMessage worker (js-obj "command" "channel-transit" "fn" fn-key "args" args))))

     (defn- clean-up-listeners [worker]
       (doto worker
         (.removeEventListener "error")
         (.removeEventListener "message")))

     (defn servant-thread-transit-with-key [servant-channel post-message-fn fn-key & args]
       (let [out-channel (chan 1)]
         (go
           (let [worker (<! servant-channel)]
             (post-message-fn
              (doto worker
                (.addEventListener "error"
                                   #(go
                                      (doto out-channel
                                        (>! :worky/error-result)
                                        close!)
                                      (when-not (>! servant-channel (clean-up-listeners worker)) ;; back in the pool!
                                        (.terminate worker))
                                      (.error js/console %)))
                (.addEventListener "message"
                                   #(go
                                      (let [result (or (t/read ds-reader (.-data %1)) :worky/nil-result)]
                                        (doto out-channel
                                          (>! result)
                                          close!)

                                        ;; return the worker back to the servant-channel
                                        ;; or terminate if the channel is closed
                                        (when-not (>! servant-channel (clean-up-listeners worker))
                                          (.terminate worker))))))
              (pr-str fn-key) args)
             ;; Add an event listener for the worker
             ))
         out-channel))


     (defonce worker-blob (js/Blob. #js [(slurp-worker)] (js-obj "type" "application/javascript")))

     (defn spawn-servant [& [path-or-blob]]
       (js/Worker. (or path-or-blob
                       (.createObjectURL js/URL worker-blob))))

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
     #_(defn test-nil []
       (let [servant-channel (spawn-servants 1)
             result-channel (run servant-channel :nil-result)]
         (go
           (let [result (<! result-channel)]
             (print result)))))
     #_(defn test-error []
       (let [servant-channel (spawn-servants 1)
             result-channel (run servant-channel :throw-error)]
         (go
           (let [result (<! result-channel)]
             (print result)))))
     #_(defn test-q
       "send a db and a query to the worker, then print it. enable console print first!"
       []
       (let [servant-channel (spawn-servants 1)
             ;; async fns like reduce and merge won't run until a channel closes!
             result-channel (async/reduce conj []
                                          (async/merge [(run
                                              servant-channel
                                              :q '[:find  ?n ?a
                                                   :where [?e :aka "Maks Otto von Stirlitz"]
                                                   [?e :name ?n]
                                                   [?e :age  ?a]
                                                   ]
                                              (:db-after (d/with (d/empty-db {:aka {:db/cardinality :db.cardinality/many}})
                                                                 [ { :db/id -1
                                                                    :name  "Maksim"
                                                                    :age   45
                                                                    :aka   ["Maks Otto von Stirlitz", "Jack Ryan"] } ])
                                                         :eavt))]))]
         (go
           (let [result (<! result-channel)]
             (print result)))))



     (defn on-js-reload []
       ;; optionally touch your app-state to force rerendering depending on
       ;; your application
       ;; (swap! app-state update-in [:__figwheel_counter] inc)
       )]))
