(ns qbits.tape.appender
  (:require [clojure.core.protocols :as p]
            [qbits.tape.queue :as q]
            [qbits.tape.codec :as codec])
  (:import (net.openhft.chronicle.queue ChronicleQueue
                                        ExcerptAppender)
           (net.openhft.chronicle.bytes Bytes)))

(set! *warn-on-reflection* true)

(defprotocol IAppender
  (write! [appender x])
  (last-index [appender])
  (queue [appender]))

(defn make
  ([queue]
   (make queue nil))
  ([queue opts]
   (let [^ExcerptAppender appender (.acquireAppender (q/underlying-queue queue))
         codec (q/codec queue)]
     (reify
       IAppender
       (write! [_ x]
         (let [rw (Bytes/wrapForRead (codec/write codec x))
               ret (with-open [ctx (.writingDocument appender)]
                     ;; Could throw if the queue is closed in another thread or on
                     ;; thread death: be paranoid here, we dont want to end up with
                     ;; a borked file, trigger rollback on any exception.
                     (try
                       (-> ctx .wire .write (.bytes rw))
                       (.index ctx)
                       (catch Throwable t
                         (.rollbackOnClose ctx)
                         t)))]
           (when (instance? Throwable ret)
             (throw (ex-info "Appender write failed"
                             {:type ::write-failed
                              :appender appender
                              :msg x}
                             ret)))
           ret))

       (last-index [_]
         (.lastIndexAppended appender))

       (queue [_] queue)

       p/Datafiable
       (datafy [_]
         #::{:cycle (.cycle appender)
             :last-index-appended (.lastIndexAppended appender)
             :source-id (.sourceId appender)
             :queue queue})))))
