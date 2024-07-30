;;;
;;; tools.build configuration.
;;;
;;; https://clojure.org/guides/tools_build
;;;
(ns build
  (:require
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.string :as s]
    [clojure.tools.build.api :as b]))

(def library 'commiterate/test-clojurescript-bun)
(def library-version "0.0.0")

(def basis (delay (b/create-basis)))

(def outputs-dir "outputs")
(def package-json-file "package.json")

(defn clean [_]
  ; Delete build outputs.
  (doseq
    [path
     [outputs-dir
      package-json-file]]
    (b/delete {:path path})))

(defn analyze [_]
  ; Format.
  (b/process
    {:command-args
     ["cljfmt"
      "fix"]})
  ; Lint.
  (b/process
    {:command-args
     ["clj-kondo"
      "--lint"
      (s/join ":" (:classpath-roots @basis))
      "--copy-configs"
      "--dependencies"
      "--parallel"]}))

(defn build [_]
  ; Clean.
  (clean nil)
  ; Analyze.
  (analyze nil)
  ; Create a package.json for the compile classpath.
  ;
  ; ClojureScript main dependencies are managed by inputs/main/clojure/deps.cljs.
  (spit
    package-json-file
    (json/write-str
      {:name
       (name library)

       :version
       library-version

       :private
       true

       :license
       "UNLICENSED"

       :type
       "module"

       :files
       ["outputs/main/cljs"]

       :exports
       {"."
        "./outputs/main/cljs/main.js"}}

      :escape-slash
      false

      :indent
      true))
  ; Other Node.js package managers have a prune function to remove unused modules in node_modules. Bun doesn't yet.
  ;
  ; https://github.com/oven-sh/bun/issues/3605
  (b/delete
    {:path
     "node_modules"})
  ; Install non-main dependencies.
  (b/process
    {:command-args
     ["bun"
      "install"]})
  ; Install main dependencies.
  (b/process
    {:command-args
     ["clj"
      "-M:cljs"
      ; Init options.
      "--compile-opts"
      (pr-str
        ;;;
        ;;; ClojureScript compiler configuration.
        ;;;
        ;;; https://clojurescript.org/reference/compiler-options
        ;;;
        {; Needed for installing dependencies.
         :deps-cmd
         "bun"})
      ; Main options.
      "--install-deps"]})
  ; Compile.
  (b/process
    {:command-args
     ["clj"
      "-M:cljs"
      ; Init options.
      "--compile-opts"
      (pr-str
        ;;;
        ;;; ClojureScript compiler configuration.
        ;;;
        ;;; https://clojurescript.org/reference/compiler-options
        ;;;
        {; Needed for indexing node_modules.
         :deps-cmd
         "bun"

         :optimizations
         :advanced

         :language-in
         :ecmascript-next

         :language-out
         :ecmascript-next

         :target
         :none

         :output-dir
         (str (io/file outputs-dir "main" "cljs"))

         :output-to
         (str (io/file outputs-dir "main" "cljs" "main.js"))})
      ; Main options.
      "--compile"
      "test-clojurescript-bun.main"]})
  ; Create a package.json for the runtime classpath.
  ;
  ; ClojureScript main dependencies are passed into the Closure compiler and turned into Closure modules in the build output.
  (spit
    (str (io/file outputs-dir "main" "cljs" package-json-file))
    (json/write-str
      {:name
       (str (name library) "-cljs")

       :version
       library-version

       :license
       "UNLICENSED"

       :type
       "module"

       :exports
       {"."
        "./main.js"}}

      :escape-slash
      false

      :indent
      true))
  ; Package Clojure source.
  (let
    [jar-contents-dir
     (str (io/file outputs-dir "main" "clojure" "jar"))

     jar-file
     (str (io/file outputs-dir "main" "clojure" (str (name library) ".jar")))]
    ; Copy the project source.
    (b/copy-dir
      {:target-dir
       jar-contents-dir

       :src-dirs
       (:paths @basis)})
    ; Create Maven metadata.
    (b/write-pom
      {:basis
       @basis

       :class-dir
       jar-contents-dir

       :lib
       library

       :version
       library-version})
    ; Archive the archive contents working directory.
    (b/jar
      {:class-dir
       jar-contents-dir

       :jar-file
       jar-file})
    ; Delete the archive contents working directory.
    (b/delete
      {:path
       jar-contents-dir})))
