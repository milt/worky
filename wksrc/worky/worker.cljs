(ns worky.worker
  (:require-macros [servant.macros :refer [defservantfn]])
  (:require [servant.core :as servant]
            [servant.worker :refer [post-array-buffer run-function-name worker-fn-map]]
            [cognitect.transit :as t]
            [cljs.reader :as reader]
            [datascript :as d]
            [datascript.core :as dc]
            [datascript.btset]))

;; test fns

(defservantfn passthru [& args]
  args)


;; Datascript transit handlers

(deftype DatomHandler []
  Object
  (tag [this v] "datascript/Datom")
  (rep [this v] #js [(.-e v) (.-a v) (.-v v) (.-tx v) (.-added v)])
  (stringRep [this v] nil))

(deftype DBHandler []
  Object
  (tag [this v] "datascript/DB")
  (rep [this v] #js {"schema" (.-schema v)
                     "datoms" (d/datoms v :eavt)})
  (stringRep [this v] nil))


 (def ds-writer
   (t/writer :json
             {:handlers
              {datascript.core/DB (DBHandler.)
               datascript.core/Datom (DatomHandler.)
               datascript.btset/BTSetIter (t/VectorHandler.)}}))

(def ds-reader
  (t/reader :json
            {:handlers
             {"datascript/Datom" (fn [[e a v tx]] (d/datom e a v tx))
              "datascript/DB" (fn [{:strs [datoms schema]}]
                                (d/init-db datoms schema))}}))

;; Datascript API stuff

(defservantfn q [q-form db & args]
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
