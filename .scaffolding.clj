(require
 '[clojure.edn :as edn])

(set! *warn-on-reflection* true)

(defn version [] (slurp "version.edn"))

(defonce tools-deps (delay (edn/read-string (slurp "deps.edn"))))

(defn deps
  []
  (some->> @tools-deps
           :deps
           (map (fn [[coord {:keys [mvn/version exclusions]}]]
                  [coord version :exclusions exclusions]))))

(defn repositories []
  (:mvn/repos @tools-deps))
