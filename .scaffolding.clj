(require
 '[clojure.java.shell :as sh]
 '[clojure.edn :as edn])

(set! *warn-on-reflection* true)

(defn next-version [version]
  (when version
    (let [[a b] (next (re-matches #"(.*?)([\d]+)" version))]
      (when (and a b)
        (str a (inc (Long/parseLong b)))))))

(defn deduce-version-from-git
  "Avoid another decade of pointless, unnecessary and error-prone
  fiddling with version labels in source code."
  []
  (let [[version commits hash dirty?]
        (next (re-matches #"(.*?)-(.*?)-(.*?)(-dirty)?\n"
                          (:out (sh/sh "git" "describe" "--dirty" "--long" "--tags" "--match" "[0-9]*.*"))))]
    (cond
      dirty? (str (next-version version) "-" hash "-dirty")
      (pos? (Long/parseLong commits)) (str (next-version version) "-" hash)
      :otherwise version)))

(defonce tools-deps (delay (edn/read-string (slurp "deps.edn"))))

(defn deps
  []
  (some->> @tools-deps
           :deps
           (map (fn [[coord {:as props
                             :keys [mvn/version exclusions]}]]
                  [coord version :exclusions exclusions]))))

(defn repositories []
  (:mvn/repos @tools-deps))
