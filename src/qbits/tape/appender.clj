(ns qbits.tape.appender
  (:require [clojure.data.fressian :as fressian]
            [clojure.core.protocols :as p]
            [qbits.tape.codec :as codec])
  (:import (net.openhft.chronicle.queue ChronicleQueue
                                        ExcerptAppender)
           (net.openhft.chronicle.bytes Bytes)))

(set! *warn-on-reflection* true)

(defprotocol IAppender
  (write! [appender x])
  (last-index [appender]))

(defn ^ExcerptAppender make
  ([^ChronicleQueue queue]
   (make queue nil))
  ([^ChronicleQueue queue opts]
   (.acquireAppender queue)))

(extend-type ExcerptAppender
  IAppender
  (write! [^ExcerptAppender appender x]
    (let [rw (Bytes/wrapForRead (codec/write x))
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

  (last-index [appender]
    (.lastIndexAppended appender))

  (queue [appender]
    (.queue appender)))

(extend-protocol p/Datafiable
  ExcerptAppender
  (datafy [^ExcerptAppender appender]
    {::cycle (.cycle appender)
     ::last-index-appended (.lastIndexAppended appender)
     ::source-id (.sourceId appender)
     ::queue (.queue appender)}))
