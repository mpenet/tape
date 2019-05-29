(ns qbits.tape.tailer
  (:require [qbits.commons.enum :as enum]
            [qbits.tape.codec :as codec]
            [qbits.tape.queue :as q]
            [clojure.core.protocols :as p])
  (:import (net.openhft.chronicle.queue ChronicleQueue
                                        ExcerptTailer
                                        TailerDirection)))

(set! *warn-on-reflection* true)

(def ->tailer-direction (enum/enum->fn TailerDirection))

(defprotocol ITailer
  (read! [tailer])
  (set-direction! [tailer dir])
  (to-index! [tailer i])
  (to-end! [tailer])
  (to-start! [tailer])
  (index [tailer])
  (queue [tailer]))

(defn make
  ([queue]
   (make queue nil))
  ([queue {:keys [reducible-poll-interval]
           :or {reducible-poll-interval 50}}]
   (let [^ExcerptTailer tailer (.createTailer (q/underlying-queue queue))
         codec (q/codec queue)]
     (reify
       ITailer
       (read! [_]
         (with-open [ctx (.readingDocument tailer)]
           (let [ret (try
                       (when (.isPresent ctx)
                         (->> ctx
                              .wire .read .bytes
                              java.nio.ByteBuffer/wrap
                              (codec/read codec)))
                       (catch Throwable t
                         (.rollbackOnClose ctx)
                         t))]
             (when (instance? Throwable ret)
               (throw (ex-info "Tailer read failed"
                               {:type ::read-failed
                                :tailer tailer}
                               ret)))
             ret)))

       (set-direction! [_ direction]
         (.direction tailer (->tailer-direction direction)))

       (to-index! [_ i]
         (.moveToIndex tailer i))

       (to-end! [_]
         (.toEnd tailer))

       (to-start! [_]
         (.toStart tailer))

       (index [_]
         (.index tailer))

       (queue [_] queue)

       clojure.lang.IReduceInit
       (reduce [this f init]
         (loop [ret init]
           (if (q/closed? queue)
             ret
             (if-let [x (read! this)]
               (let [ret (f ret x)]
                 (if (reduced? ret)
                   @ret
                   (recur ret)))
               (do
                 (Thread/sleep reducible-poll-interval)
                 (recur ret))))))

       p/Datafiable
       (datafy [_]
         #::{:cycle (.cycle tailer)
             :index (.index tailer)
             :source-id (.sourceId tailer)
             :direction (.direction tailer)
             :state (.state tailer)
             :queue queue})))))
