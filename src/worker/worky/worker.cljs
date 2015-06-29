(ns worky.worker
  (:require-macros [servant.macros :refer [defservantfn]])
  (:require
   [worky.common :refer [ds-reader ds-writer]]
   [servant.core :as servant]
   [servant.worker :refer [post-array-buffer run-function-name worker-fn-map]]
   [cljs.reader :as reader]
   [datascript :as d]
   [cognitect.transit :as t]))

;; test fns

(defservantfn passthru [& args]
  args)

(defservantfn nil-result [_]
  nil)

(defservantfn throw-error [_]
  (throw (js/Error. "Test Error")))

;; Datascript API stuff

(defservantfn q
  "runs a datalog query on arbitrary input data"
  [q-form db & args]
  (apply d/q q-form db args))


;; Datascript internal state

(def conn (d/create-conn))

(defservantfn empty-db [& [schema]]
  (reset! conn (d/empty-db schema))
  :success)

(defservantfn init-db [datoms & [schema]]
  (reset! conn (d/init-db datoms schema))
  :success)

(defservantfn reset-db! [db]
  (reset! conn db)
  :success)

(defservantfn db []
  @conn)

(defservantfn datoms [index & components]
  (apply d/datoms @conn index components))

(defservantfn transact! [data]
  (d/transact! conn data) ;; todo.. handle transaction reports, listeners
  :success)

(defservantfn transact-async [data]
  (d/transact-async conn data)
  :apparent-success)

(defservantfn q-worker
  "query the internal state of the worker"
  [q-form & args]
  (apply d/q q-form @conn args))

;; worker or host

(defservantfn pull
  ([pattern eid]
   (pull @conn pattern eid))
  ([db pattern eid]
   (d/pull db pattern eid)))

(defservantfn pull-many
  ([pattern eids]
   (pull-many @conn pattern eids))
  ([db pattern eids]
   (d/pull-many db pattern eids)))

(defservantfn entity
  ([eid]
   (entity @conn eid))
  ([db eid]
   (d/entity db eid)))

;; transit codec
(defn decode-message [worker-id event]
  (condp = (aget (.-data event) "command")
    "channel" (.postMessage js/self (run-function-name (.-data event)))
    "channel-arraybuffer" (post-array-buffer (run-function-name (.-data event)))
    "channel-transit" (.postMessage js/self (let [message-data (.-data event)
                                                  fn-key (reader/read-string (aget message-data "fn"))
                                                  f (get @worker-fn-map fn-key)
                                                  args (t/read ds-reader (aget message-data "args"))
                                                  result (apply f args)]
                                              #_(.log js/console (str "worker " (str worker-id) " : " (name fn-key) " at " (.getTime (js/Date.)) " result " result))
                                              (t/write ds-writer result)))))

(defn bootstrap []
  (set! (.-onmessage js/self) (partial decode-message (d/squuid))))

(bootstrap)
