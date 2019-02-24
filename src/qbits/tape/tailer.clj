(ns qbits.tape.tailer
  (:require [qbits.commons.enum :as enum]
            [qbits.commons.ns :as n]
            [qbits.tape.codec :as codec]
            [clojure.core.protocols :as p])
  (:import (net.openhft.chronicle.queue ChronicleQueue
                                        ExcerptTailer
                                        TailerDirection)))

(def ->tailer-direction (enum/enum->fn TailerDirection))

(defn ^ExcerptTailer make
  ([^ChronicleQueue queue]
   (make queue nil))
  ([^ChronicleQueue queue opts]
   (.createTailer queue)))

(defprotocol ITailer
  (read! [tailer])
  (set-direction! [tailer dir])
  (to-index! [tailer i])
  (to-end! [tailer])
  (to-start! [tailer])
  (index [tailer])
  (queue [tailer]))

(extend-type ExcerptTailer
  ITailer
  (read! [^ExcerptTailer tailer]
    (with-open [ctx (.readingDocument tailer)]
      (let [ret (try
                  (when (.isPresent ctx)
                    (-> ctx
                        .wire .read .bytes
                        java.nio.ByteBuffer/wrap
                        codec/read))
                  (catch Throwable t
                    (.rollbackOnClose ctx)
                    t))]
      (when (instance? Throwable ret)
        (throw (ex-info "Tailer read failed"
                        {:type ::read-failed
                         :tailer tailer}
                        ret)))
      ret)))

  (set-direction! [tailer direction]
    (.direction tailer (->tailer-direction direction)))

  (to-index! [^ExcerptTailer tailer i]
    (.moveToIndex tailer i))

  (to-end! [tailer]
    (.toEnd tailer))

  (to-start! [tailer]
    (.toStart tailer))

  (index [tailer]
    (.index tailer))

  (queue [tailer]
    (.queue tailer)))

(extend-protocol p/Datafiable
  ExcerptTailer
  (datafy [^ExcerptTailer tailer]
    #::{:cycle (.cycle tailer)
        :index (index tailer)
        :source-id (.sourceId tailer)
        :queue (.queue tailer)
        :direction (.direction tailer)
        :state (.state tailer)}))
