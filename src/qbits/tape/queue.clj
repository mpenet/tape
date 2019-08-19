(ns qbits.tape.queue
  (:require [qbits.commons.enum :as enum]
            [qbits.commons.jvm :as jvm]
            [clojure.tools.logging :as log]
            [clojure.core.protocols :as p]
            [qbits.tape.cycle-listener :as cycle-listener]
            [qbits.tape.codec.fressian :as fressian.codec])
  (:import (net.openhft.chronicle.queue ChronicleQueue
                                        RollCycles
                                        ExcerptTailer)
           (net.openhft.chronicle.queue.impl.single SingleChronicleQueueBuilder
                                                    SingleChronicleQueue)))

(set! *warn-on-reflection* true)

(def ->roll-cycle (enum/enum->fn RollCycles))

(defprotocol IQueue
  (codec [q] "Returns codec to be used with queue instance")
  (close! [q] "Closes the queue")
  (closed? [q] "Returns true if the queue is closed")
  (underlying-queue ^net.openhft.chronicle.queue.impl.single.SingleChronicleQueue [q]
    "Returns the underlying chronicle-queue instance"))

(defn ^java.io.Closeable make
  "Return a queue instance that will create/bind to a directory

  * `roll-cycle` roll-cycle determines how often you create a new
  Chronicle Queue data file. Can be `:minutely`, `:daily`,
  `:test4-daily`, `:test-hourly`, `:hourly`, `:test-secondly`,
  `:huge-daily-xsparse`, `:test-daily`, `:large-hourly-xsparse`,
  `:large-daily`, `:test2-daily`, `:xlarge-daily`, `:huge-daily`,
  `:large-hourly`, `:small-daily`, `:large-hourly-sparse`

  * `autoclose-on-jvm-exit?` wheter to cleanly close the queue on jvm
  exit (defaults to true)

  * `cycle-release-tasks` Tasks to run on queue release. See
  qbits.tape.cycle-listener

  * `cycle-acquire-tasks` Tasks to run on queue acquisition. See
  qbits.tape.cycle-listener

  * `codec` qbits.tape.codec/ICodec instance that will be used to
  encode/decode messages. Default to qbits.tape.codec.fressian/default"
  ([dir]
   (make dir nil))
  ([dir {:keys [roll-cycle autoclose-on-jvm-exit?
                cycle-release-tasks
                cycle-acquire-tasks
                codec
                block-size]
         :or {roll-cycle :small-daily
              autoclose-on-jvm-exit? true
              codec fressian.codec/default}}]
   (let [^SingleChronicleQueue queue
         (cond-> (ChronicleQueue/singleBuilder ^String dir)
           roll-cycle
           (.rollCycle (->roll-cycle roll-cycle))

           (or (seq cycle-release-tasks)
               (seq cycle-acquire-tasks))
           (.storeFileListener (cycle-listener/make dir
                                                    (->roll-cycle roll-cycle)
                                                    {:release-tasks cycle-release-tasks
                                                     :acquire-tasks cycle-acquire-tasks}))
           (number? block-size)
           (.blockSize (long block-size))

           :then (.build))
         q (reify
             IQueue
             (closed? [this]
               (.isClosed queue))

             (close! [this]
               (.close queue))

             (codec [this]
               codec)

             (underlying-queue [this] queue)

             java.io.Closeable
             (close [this] (.close queue))

             p/Datafiable
             (datafy [this]
               #::{:source-id (.sourceId queue)
                   :last-acknowledged-index-replicated (.lastAcknowledgedIndexReplicated queue)
                   :last-index-replicated (.lastIndexReplicated queue)
                   :file (.fileAbsolutePath queue)
                   :index-count (.indexCount queue)
                   :index-spacing (.indexSpacing queue)
                   :roll-cycle (.rollCycle queue)
                   :delta-checkpoint-interval (.deltaCheckpointInterval queue)
                   :buffered (.buffered queue)
                   :cycle (.cycle queue)
                   :closed? (.isClosed queue)}))]

     (when autoclose-on-jvm-exit?
       (jvm/add-shutdown-hook!
        (fn []
          (when-not (closed? q)
            (log/infof "JVM exit handler triggered for open Queue: %s"
                       q)
            (close! q)
            (log/infof "Closed Queue: %s"
                       q)))))
     q)))
