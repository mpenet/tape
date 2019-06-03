(load-file ".scaffolding.clj")
(defproject cc.qbits/tape (version)
  :description "ChronicleQueue helpers"
  :repositories ~(repositories)
  :dependencies ~(deps)
  :pedantic? :warn
  :source-paths ["src"]
  :global-vars {*warn-on-reflection* true})
