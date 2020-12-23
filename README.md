# tape

[![cljdoc badge](https://cljdoc.xyz/badge/cc.qbits/tape)](https://cljdoc.xyz/d/cc.qbits/tape/CURRENT) [![Clojars Project](https://img.shields.io/clojars/v/cc.qbits/tape.svg)](https://clojars.org/cc.qbits/tape)

<img src="http://i.imgur.com/yNrbl1D.png" title="qbits/tape" align="right"/>

Simple [Chronicle Queue](https://github.com/OpenHFT/Chronicle-Queue) 5
helpers for clojure.

> Micro second messaging that stores everything to disk

In short for when Kafka is too much and durable-queue not enough.

Chronicle Queue is similar to a low latency broker-less
durable/persisted JVM topic. Tape focuses on embedded usage (we do not
support topic distribution).  It's essentially a disk-backed queue,
allowing for queues that can survive processes dying, and whose size
is bounded by available disk rather than memory.

Conceptually the api is somewhat similar to kafka, you can replay
queues, set tailer's index etc... It stores everything on flat-files,
you can control how rollup/purge of these happens via queue options.


I'd encourage you read about [Chronicle
Queue](https://github.com/OpenHFT/Chronicle-Queue) if you want to use
this lib, Chronicle Queue comes with its set of tradeoffs you want to
know first.

Started with a fork of https://github.com/malesch/croque and ended up
rewriting/dropping most of it, hence the rename.

## Installation

`tape` is [available on Clojars](https://clojars.org/cc.qbits/tape).

## Usage

``` clj
(ns foo
  (:require [qbits.tape.tailer :as tailer]
            [qbits.tape.appender :as appender]
            [qbits.tape.queue :as queue]))

;; create a queue instance
(def q (queue/make "/tmp/q1"))

;; create a tailer bound to that queue
(def t (tailer/make q))

;; nothing in queue yet, so nil
(tailer/read! t) => nil

;; to add to queue you need an appender
(def appender (appender/make q))

;; add stuff to queue, returns index
(appender/write! appender {:foo [:bar {:baz 0}]}) => 76759655514210
(appender/write! appender {:another :thing}) => 76759655514211

(tailer/read! t) => {:foo [:bar {:baz 0}]}
(tailer/read! t) => {:another :thing}
(tailer/read! t) => nil ;; empty now


;; back to some existing index, essentially rewinding to it
(tailer/to-index! t 76759655514210)

;; Tailers are also Sequential/Seqable/Reducible and behave as such.

(run! (fn [msg] (println msg)) t)

(doseq [msg t]
  (println msg))

[...]
```

There's also a core.async facade for appenders/tailers on
`qbits.tape.async` and other utilities to cleanup queues files you
don't care about anymore. Anything created with a `make` function can
be inspected with `clojure.datafy/datafy`.

We serialize data with fressian as a default, but you can supply your
own qbits.tape/ICodec when you make/bind to a queue if you need to use
something else.

## License

Copyright © 2019 Max Penet

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
