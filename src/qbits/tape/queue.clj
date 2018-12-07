(ns qbits.tape.queue
  (:require [qbits.commons.enum :as enum]
            [qbits.commons.jvm :as jvm]
            [clojure.tools.logging :as log]
            [clojure.core.protocols :as p]
            [qbits.tape.cycle-listener :as cycle-listener])
  (:import (net.openhft.chronicle.queue ChronicleQueue
                                        RollCycles)
           (net.openhft.chronicle.queue.impl.single SingleChronicleQueueBuilder
                                                    SingleChronicleQueue)))

(set! *warn-on-reflection* true)

(def ->roll-cycle (enum/enum->fn RollCycles))

(extend-protocol p/Datafiable
  SingleChronicleQueue
  (datafy [^SingleChronicleQueue q]
    ;; https://github.com/OpenHFT/Chronicle-Queue/blob/master/src/main/java/net/openhft/chronicle/queue/impl/single/SingleChronicleQueue.java
    {::source-id (.sourceId q)
     ::last-acknowledged-index-replicated (.lastAcknowledgedIndexReplicated q)
     ::last-index-replicated (.lastIndexReplicated q)
     ::file (.fileAbsolutePath q)
     ::index-count (.indexCount q)
     ::index-spacing (.indexSpacing q)
     ::roll-cycle (.rollCycle q)
     ::delta-checkpoint-interval (.deltaCheckpointInterval q)
     ::buffered (.buffered q)
     ::cycle (.cycle q)
     ::closed? (.isClosed q)}))

(defn closed?
  [^SingleChronicleQueue queue]
  (.isClosed queue))

(defn close!
  [^SingleChronicleQueue queue]
  (.close queue))

(defn register-jvm-exit-handler!
  [^SingleChronicleQueue queue]
  (jvm/add-shutdown-hook!
   (fn []
     (when-not (closed? queue)
       (log/infof "JVM exit handler triggered for open Queue: %s"
                  queue)
       (close! queue)
       (log/infof "Closed Queue: %s"
                  queue)))))

(defn make
  "Creates and return a new queue"
  ([dir]
   (make dir nil))
  ([dir {:as opts
         :keys [roll-cycle autoclose-on-jvm-exit?
                cycle-release-tasks
                cycle-acquire-tasks]
         :or {roll-cycle :small-daily
              autoclose-on-jvm-exit? true}}]
   (let [queue (cond-> (ChronicleQueue/singleBuilder ^String dir)
                 roll-cycle
                 (.rollCycle (->roll-cycle roll-cycle))

                 (or (seq cycle-release-tasks)
                     (seq cycle-acquire-tasks))
                 (.storeFileListener (cycle-listener/make dir
                                                          (->roll-cycle roll-cycle)
                                                          {:release-tasks cycle-release-tasks
                                                           :acquire-tasks cycle-acquire-tasks}))

                 :then (.build))]
     (when autoclose-on-jvm-exit?
       (register-jvm-exit-handler! queue))
     queue)))
