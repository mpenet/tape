(ns qbits.tape.codec
  (:refer-clojure :exclude [read]))

(defprotocol ICodec
  (write ^java.nio.ByteBuffer [codec x])
  (read [codec ^java.nio.ByteBuffer x]))
