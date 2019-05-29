(ns qbits.tape-test
  (:require [clojure.test :refer :all]
            [qbits.tape.tailer :as tailer]
            [qbits.tape.async :as tape.async]
            [qbits.tape.appender :as appender]
            [qbits.tape.queue :as queue]
            [clojure.core.async :as async]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen])
  (:import (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)))

(def ^:dynamic *queue*)
(def ^:dynamic *tailer*)
(def ^:dynamic *appender*)

(s/def ::msg map?)

(defn gen-msgs
  ([] (gen-msgs 10))
  ([n]
   (binding [s/*recursion-limit* 1]
     (gen/sample (s/gen ::msg)
                 n))))

(use-fixtures :each
  (fn setup [t]
    (let [dir (str (Files/createTempDirectory "qbits_tape_test"
                                              (into-array FileAttribute [])))
          queue (queue/make dir nil)]
      (binding [*queue* queue
                *tailer* (tailer/make queue)
                *appender* (appender/make queue)]
        (with-open [q *queue*]
          (t))
        (doseq [f (reverse (file-seq (io/file dir)))]
          (io/delete-file f))))))

(deftest test-append-tail
  (let [msgs (gen-msgs)]
    (doseq [m msgs]
      (appender/write! *appender* m))
    (is (= msgs
           (repeatedly (count msgs)
                       #(tailer/read! *tailer*))))))

(deftest test-async-tailer
  (let [tailer-ch (tape.async/tailer-chan *tailer*)
        msgs (gen-msgs)]

    (run! #(appender/write! *appender* %)
          msgs)

    (is (= msgs
           (repeatedly (count msgs)
                       #(async/<!! tailer-ch))))))

(deftest test-async-appender
  (let [appender-ch (tape.async/appender-chan *appender*)
        msgs (gen-msgs)]

    (run! #(async/>!! appender-ch %)
          msgs)

    (is (= msgs
           (repeatedly (count msgs)
                       #(tailer/read! *tailer*))))))

;; (run-tests)
