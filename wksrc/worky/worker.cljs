(ns worky.worker
  (:require-macros [servant.macros :refer [defservantfn]])
  (:require [servant.core :as servant]
            [servant.worker :refer [post-array-buffer run-function-name worker-fn-map]]
            [cognitect.transit :as t]
            [cljs.reader :as reader]
            [datascript :as d]
            [datascript.core :as dc]
            [datascript.btset]))

;; RANDOM API
(defservantfn passthru [x]
  x)

(defservantfn increment [n]
  (inc n))


(defn in-order? [order xs]
  (or (empty? xs)
      (apply order xs)))

(defn bogosort* [xs]
  (if (in-order? < xs) xs
      (recur (shuffle xs))))

(defservantfn bogosort [xs]
  (clj->js (bogosort* xs)))

(defn prime? [i]
  (cond (< i 4)           (>= i 2)
        (zero? (rem i 2)) false
        :else (not-any? #(zero? (rem i %)) (range 3 (inc (.sqrt js/Math i))))))

(defn mersenne? [p] (or (= p 2)
                        (let [mp   (dec (bit-shift-left 1 p))]
                          (loop [n 3 s 4]
                            (if (> n p)
                              (zero? s)
                              (recur (inc n) (rem (- (* s s) 2) mp)))))))

(defservantfn m-primes [n]
  (vec (take n (filter mersenne? (filter prime? (iterate inc 1))))))


;; Datascript setup

(def conn
  (let [schema {:aka {:db/cardinality :db.cardinality/many}}
        conn   (d/create-conn schema)]
    conn))

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


(defservantfn bootstrap-conn! [db]
  (reset! conn db))

(defservantfn q [q-form]
  (d/q q-form @conn))

(defservantfn foreign-q [q-form db]
  (d/q q-form db))

(defservantfn transact! [data & [db-after?]]
  (if db-after?
    (:db-after (d/transact! conn data))
    (do
      (d/transact! conn data)
      :apparent-success)))

(defservantfn transact-async [data]
  (d/transact-async conn data)
  :apparent-success)



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
