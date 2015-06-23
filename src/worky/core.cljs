(ns ^:figwheel-always worky.core
    (:require-macros [servant.macros :refer [defservantfn]]
                     [cljs.core.async.macros :as asyncm :refer (go go-loop)])
    (:require [servant.core :as servant]
              [servant.worker :refer [worker-fn-map]]
              [cljs.core.async :as async :refer (<! >! put! chan pub sub tap mult)]
              [cognitect.transit :as t]
              [cljs.reader :as reader]
              [datascript :as d]
              [datascript.core :as dc]
              [datascript.btset]))

(enable-console-print!)


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

(defn transit-message [worker fn-key args]
  (let [args (t/write ds-writer args)]
    (.postMessage worker (js-obj "command" "channel-transit" "fn" fn-key "args" args))))

(defn servant-thread-transit-with-key [servant-channel post-message-fn fn-key & args]
  (let [r (t/reader :json)
        out-channel (chan 1 (map (partial t/read ds-reader)))]
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


(defonce worker-count 1)
(defonce worker-script "js/compiled/worky_worker.js")
(defonce servant-channel (servant/spawn-servants worker-count worker-script))

(defn result-chan [fn-key args]
  (apply servant-thread-transit-with-key
         servant-channel
         transit-message
         fn-key
         args))

(defn print-answer [fn-key & args]
  (let [result-channel (result-chan
                        fn-key
                        args)]
    (go
      (let [answer (<! result-channel)]
        (print answer)))))

(defn use-answer [answer-fn fn-key & args]
  (let [result-channel (result-chan
                        fn-key
                        args)]
    (go
      (let [answer (<! result-channel)]
        (answer-fn answer)))))


(defn bootstrap-conn! []
  (let [new-db @(d/create-conn {:aka {:db/cardinality :db.cardinality/many}})]
    (print-answer :bootstrap-conn! new-db)))

(defn primes [n]
  (print-answer :m-primes n))

(defn q [q-form]
  (print-answer :q q-form))

(defn foreign-q [q-form db]
  (print-answer :foreign-q q-form db))


(defn transact! [data]
  (print-answer :transact! data))

(defn transact-async [data]
  (print-answer :transact-async data))


;; Some stuff to play with

(defn- rand-char-str []
  (char (+ (rand-int 26) 65)))

(defn- rand-str []
  (apply str (repeatedly (+ 3 (rand-int 7)) rand-char-str)))

(defn- many-peeps [n]
  (into []
        (for [i (range n)]
          {:name (rand-str)
           :age (rand-int 100)
           :aka [(rand-str) (rand-str)]})))

(defn transact-shitload []
  (transact! (many-peeps 10000)))

(defn transact-one []
  (transact!
   [ { :db/id -1
      :name  "Maksim"
      :age   45
      :aka   ["Maks Otto von Stirlitz", "Jack Ryan"] } ]))

(defn q-one []
  (q '[ :find  ?n ?a
       :where [?e :aka "Maks Otto von Stirlitz"]
       [?e :name ?n]
       [?e :age  ?a]]))

(defn q-eids []
  (q '[:find ?e
       :where
       [?e :name]]))

(defn q-foreign []
  (let [db (-> (d/empty-db {:aka {:db/cardinality :db.cardinality/many}})
               (d/with (many-peeps 10000))
               :db-after)]
    (foreign-q '[:find (count ?e) . :where [?e :name]] db)))


(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
