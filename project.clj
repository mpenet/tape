(load-file ".scaffolding.clj")
(defproject cc.qbits/tape (version)
  :description "ChronicleQueue helpers"
  :repositories ~(repositories)
  :dependencies ~(deps)
  :pedantic? :warn
  :source-paths ["src"]
  :jvm-opts ["--illegal-access=permit"
             "--add-exports"
             "java.base/jdk.internal.ref=ALL-UNNAMED"]
  :global-vars {*warn-on-reflection* true})
