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
  (close! [q])
  (closed? [q])
  (underlying-queue ^net.openhft.chronicle.queue.impl.single.SingleChronicleQueue [q]))

(defn ^java.io.Closeable make
  "Creates and return a new queue"
  ([dir]
   (make dir nil))
  ([dir {:keys [roll-cycle autoclose-on-jvm-exit?
                cycle-release-tasks
                cycle-acquire-tasks
                codec]
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
