(ns qbits.tape.async
  "Experimental core.async interface"
  (:require [clojure.core.async :as async]
            [qbits.tape.tailer :as tailer]
            [qbits.tape.queue :as queue]
            [qbits.tape.appender :as appender]))

(defn tailer-chan
  ([queue]
   (tailer-chan queue nil))
  ([queue {:keys [ch poll-interval]
           :or {ch (async/chan)
                poll-interval 50}
           :as opts}]
   (assert (satisfies? queue/IQueue queue))
   (async/thread
     (let [tailer (tailer/make queue opts)]
       (loop []
         ;; fetch next msg or nothing
         (if (queue/closed? queue)
           (async/close! ch)
           (if-let [x (try (tailer/read! tailer)
                           (catch Exception e
                             ;; on error just send ex to chan
                             e))]
             ;; enqueue and recur
             (if (async/>!! ch x)
               (recur)
               ;; move index back by one since we didn't consume that msg
               ;; and die
               (tailer/to-index! tailer
                                 (max 0 (dec (tailer/index tailer)))))
             ;; wait and recur
             (do (async/<!! (async/timeout poll-interval))
                 (recur)))))))
   ch))

(defn appender-chan
  ([queue]
   (appender-chan queue nil))
  ([queue {:as opts
           :keys [ch error-ch]
           :or {ch (async/chan)
                error-ch (async/chan (async/dropping-buffer 50))}}]
   (assert (satisfies? queue/IQueue queue))
   (async/thread
     (let [appender (appender/make queue opts)]
       (loop []
         ;; just take vals as long as it's not closed
         (when-let [x (async/<!! ch)]
           (try
             (appender/write! appender x)
             (catch Throwable t
               (async/>!! error-ch x)))
           (recur)))))
   ch))
