(ns qbits.tape.codec
  (:refer-clojure :exclude [read])
  (:require [clojure.data.fressian :as fressian])
  (:import (java.nio ByteBuffer)))

(defn ^ByteBuffer write
  [x]
  (fressian/write x))

(defn read
  [^ByteBuffer x]
  (fressian/read x))
