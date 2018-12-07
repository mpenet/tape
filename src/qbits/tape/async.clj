(ns qbits.tape.async
  "Experimental core.async interface"
  (:require [clojure.core.async :as async]
            [qbits.tape.tailer :as tailer]
            [qbits.tape.queue :as queue]
            [qbits.tape.appender :as appender]))

(defn tailer-chan
  ([tailer]
   (tailer-chan tailer nil))
  ([tailer {:as opts
            :keys [ch index-meta? poll-interval]
            :or {ch (async/chan)
                 index-meta? false
                 poll-interval 50}}]
   (let [q (tailer/queue tailer)]
     (async/thread
       (loop []
         ;; fetch next msg or nothing
         (if (queue/closed? q)
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
                                 (-> tailer
                                     tailer/index)))
             ;; wait and recur
             (do (async/<!! (async/timeout poll-interval))
                 (recur)))))))
   ch))

(defn appender-chan
  ([appender]
   (appender-chan appender nil))
  ([appender {:keys [ch error-ch]
              :or {ch (async/chan)
                   error-ch (async/chan (async/dropping-buffer 50))}
              :as opts}]
   (async/thread
     (loop []
       ;; just take vals as long as it's not closed
       (when-let [x (async/<!! ch)]
         (try
           (appender/write! appender x)
           (catch Throwable t
             (async/>!! error-ch x)))
         (recur))))
   ch))
