(ns worky.common
  (:require
   [datascript :as d]
   [datascript.core :as dc]
   [datascript.btset]
   [cognitect.transit :as t]))


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
