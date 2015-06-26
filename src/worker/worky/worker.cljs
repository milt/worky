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

;; transit codec
(defn decode-message [event]
  (condp = (aget (.-data event) "command")
    "channel" (.postMessage js/self (run-function-name (.-data event)))
    "channel-arraybuffer" (post-array-buffer (run-function-name (.-data event)))
    "channel-transit" (.postMessage js/self (let [message-data (.-data event)
                                                  fn-key (reader/read-string (aget message-data "fn"))
                                                  f (get @worker-fn-map fn-key)
                                                  args (t/read ds-reader (aget message-data "args"))]
                                              (t/write ds-writer (apply f args))))))

(defn bootstrap []
  (set! (.-onmessage js/self) decode-message))

(bootstrap)
