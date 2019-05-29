(ns qbits.tape.cycle-listener
  (:refer-clojure :exclude [cycle])
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.string :as str])
  (:import (net.openhft.chronicle.queue.impl StoreFileListener)
           (net.openhft.chronicle.queue RollCycle RollCycles)
           (java.io File FileOutputStream FileInputStream)
           (java.util.zip GZIPOutputStream)
           (java.time.format DateTimeFormatter DateTimeParseException)))

(set! *warn-on-reflection* true)

(defn gzip
  [^File file]
  (let [file-name (.getName file)
        parent (.getParent file)
        gz-file (io/file (format "%s/%s.gz"
                                 parent
                                 file-name))]
    (if (.exists gz-file)
      (log/infof "Skipping compression of %s" file)
      (do (log/infof "Compressing %s to %s" file gz-file)
          (with-open [output-stream (FileOutputStream. (io/file gz-file))
                      input-stream (FileInputStream. file)
                      gzip-output-stream (GZIPOutputStream. output-stream)]
            (let [buffer (byte-array (* 64 1024))]
              (loop []
                (let [size (.read input-stream buffer)]
                  (when (pos? size)
                    (.write gzip-output-stream buffer 0 size)
                    (recur))))
              (.finish gzip-output-stream)))
          ;; finally delete file we just compressed
          (io/delete-file file)))))

(defn file-formatter
  [fmt]
  (DateTimeFormatter/ofPattern (format "%s'.cq4'")))

(defn cycle-file?
  [^RollCycle roll-cycle ^File file]
  (try
    (-> (format "%s'.cq4'" (.format roll-cycle))
        DateTimeFormatter/ofPattern
        (.parse (.getName file)))
    (catch DateTimeParseException e
      nil)))

(defn gzip-file?
  [^File file]
  (-> file .getName (str/ends-with? ".gz")))

(defn delete
  [file]
  (log/infof "Deleting %s" file)
  (io/delete-file file))

(defn filter-files-by
  [dir f]
  (some->> (io/file dir)
           (file-seq)
           (filter f)
           sort
           reverse))

(defn cycle-files
  "Returns seq of cycle files sorted by date"
  [{:keys [dir roll-cycle]}]
  (filter-files-by dir
                   #(cycle-file? roll-cycle %)))

(defn gzip-files
  "Returns seq of gzip files sorted by date"
  [{:keys [dir]}]
  (filter-files-by dir
                   gzip-file?))

(defmulti action (fn [act ctx] (:type act)))

(defmethod action :log
  [opt ctx]
  (run! #(log/info ::log (str %))
        (cycle-files ctx)))

(defmethod action :gzip
  [{:keys [after-cycles max-gzip]
    :or {;; gzip everything after N
         after-cycles 1
         ;; drop all gzip files after N
         max-gzip 2}}
   ctx]
  (run! gzip
        (drop after-cycles
              (cycle-files ctx)))

  (run! delete
        (drop max-gzip
              (gzip-files ctx))))

(defmethod action :delete
  [{:keys [after-cycles]
    :or {after-cycles 1}}
   ctx]
  (run! delete
        (drop after-cycles (cycle-files ctx))))

(defn run-actions!
  [actions context]
  (run! #(action % context)
        actions))

(defn make
  ([dir roll-cycle]
   (make dir roll-cycle nil))
  ([dir roll-cycle {:keys [release-tasks acquire-tasks]}]
   (let [ctx {:roll-cycle roll-cycle
              :dir dir}]
     (reify StoreFileListener
       (onReleased [_ cycle file]
         (log/infof "Got release on %s %s" cycle file)
         (when release-tasks
           (log/infof "Running release tasks %s" release-tasks)
           (run-actions! release-tasks
                         (assoc ctx
                                :current-cycle cycle
                                :file file))))

       (onAcquired [_ cycle file]
         (log/infof "Got acquire on %s %s" cycle file)
         (when acquire-tasks
           (log/infof "Running acquire tasks %s" acquire-tasks)
           (run-actions! acquire-tasks
                         (assoc ctx
                                :current-cycle cycle
                                :file file))))))))
