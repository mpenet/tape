(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]
            [clojure.tools.build.tasks.process :as p]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'cc.qbits/tape)
(def version (format "1.0.0-alpha%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def copy-srcs ["src" "resources"])
(def target-dir "target")
(def jar-file (format "%s/%s-%s.jar" target-dir (name lib) version))

(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean
  [opts]
  (b/delete {:path target-dir})
  opts)

(defn jar
  [opts]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :src-dirs ["src"]
                :pom-data
                [[:licenses
                  [:license
                   [:name "EPL 1.0"]
                   [:url "https://www.eclipse.org/legal/epl/epl-v10.html"]
                   [:distribution "repo"]]]]})
  (b/copy-dir {:src-dirs copy-srcs
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file})
  opts)

(defn deploy
  [opts]
  (dd/deploy {:artifact jar-file
              :pom-file (format "%s/classes/META-INF/maven/%s/pom.xml"
                                target-dir
                                lib)
              :installer :remote
              :sign-releases? false})
  opts)

(defn- sh
  [& cmds]
  (doseq [cmd cmds]
    (p/process {:command-args ["sh" "-c" cmd]})))

(defn tag
  [opts]
  (sh
   (format "git tag -a \"%s\" --no-sign -m \"Release %s\"" version version)
   "git pull"
   "git push --follow-tags")
  opts)

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn release
  [opts]
  (-> opts
      clean
      jar
      deploy
      tag))
