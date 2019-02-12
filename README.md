# tape

[![cljdoc badge](https://cljdoc.xyz/badge/cc.qbits/tape)](https://cljdoc.xyz/d/cc.qbits/tape/CURRENT) [![Clojars Project](https://img.shields.io/clojars/v/cc.qbits/tape.svg)](https://clojars.org/cc.qbits/tape)

<img src="http://i.imgur.com/yNrbl1D.png" title="qbits/tape" align="right"/>

Simple [Chronicle Queue](https://github.com/OpenHFT/Chronicle-Queue) 5
helpers for clojure.

> Micro second messaging that stores everything to disk

In short for when Kafka is too much and durable-queue not enough.

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
(appender/write! t {:foo [:bar {:baz 0}]}) => 76759655514210
(appender/write! t {:another :thing}) => 76759655514211

(tailer/read! t) => {:foo [:bar {:baz 0}]}
(tailer/read! t) => {:another :thing}
(tailer/read! t) => nil ;; empty now


;; back to some existing index, essentially rewinding to it
(tailer/to-index! t 76759655514210)

[...]
```

There's also a core.async facade for appenders/tailers on
`qbits.tape.async` and other utilities to cleanup queues files you
don't care about anymore. Anything created with a `make` function can
be inspected with `clojure.datafy/datafy`.


## License

Copyright © 2018 Max Penet

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
