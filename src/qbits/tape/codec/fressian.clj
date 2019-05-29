(ns qbits.tape.codec.fressian
  (:require
   [qbits.tape.codec :as codec]
   [clojure.data.fressian :as fressian]))

(defn make
  []
  (reify
    codec/ICodec
    (write [_ x]
      (fressian/write x))
    (read [_ x]
      (fressian/read x))))

(defonce default (make))
