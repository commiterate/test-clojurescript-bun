# Test ClojureScript + Bun

Test `:deps-cmd "bun"` in ClojureScript for https://github.com/clojure/clojurescript/pull/231.

## Layout

```text
Key:
🤖 = Generated

.
│   # Build inputs.
├── inputs/
│   └── main/
│       └── clojure/
│           ├── test_clojurescript_bun/
│           │   └── main.cljc
│           │
│           │   # ClojureScript path configuration.
│           └── deps.cljs
│
│   # Build outputs.
├── outputs/ 🤖
│   └── main/
│       │
│       │   # Clojure build outputs.
│       │   #
│       │   # Dependencies procured through Git are omitted from the pom.xml.
│       ├── clojure/
│       │   └── test-clojurescript-bun.jar/
│       │       ├── META-INF/
│       │       │   ├── maven/
│       │       │   │   └── commiterate/
│       │       │   │       └── test-clojurescript-bun/
│       │       │   │           ├── pom.properties
│       │       │   │           └── pom.xml
│       │       │   └── MANIFEST.MF
│       │       └── {copy of :paths in deps.edn}
│       │
│       │   # ClojureScript build outputs.
│       └── cljs/
│           ├── main.js
│           ├── package.json
│           └── ...
│
│   # Reproducible shell configuration.
├── flake.nix
├── flake.lock 🤖
│
│   # Clojure path configuration.
├── deps.edn
│
│   # JavaScript path configuration.
├── package.json 🤖
├── bun.lockb 🤖
│
│   # Build tools.
└── build.clj
```

## Tools

* Java
* Clojure
* cljfmt
* clj-kondo
* Bun

A reproducible shell can be created with [Nix](https://nixos.org) (described by the `flake.nix` + `flake.lock` files).

Nix can be installed with the [Determinate Nix Installer](https://github.com/DeterminateSystems/nix-installer) ([guide](https://zero-to-nix.com/start/install)).

Afterwards, you can change into the project directory and create the reproducible shell with `nix develop`.

You can also install the [direnv](https://direnv.net) shell extension to automatically load and unload the reproducible shell when you enter and leave the project directory.

Unlike `nix develop` which drops you in a nested Bash shell, direnv extracts the environment variables from the nested Bash shell into your current shell (e.g. Bash, Zsh, Fish).

## Developing

To build the project, run:

```shell
clj -T:build build
```

To run the Node.js application, run:

```shell
bun run outputs/main/cljs/main.js
```

## Notes

### Making the `:npm-deps true` and `:install-deps true` ClojureScript Compiler Options Work

The ClojureScript compiler looks for `deps.cljs` files (not `package.json` files) on the classpath to figure out the Node.js packages needed by dependencies.

This means the classpath used by the ClojureScript compiler needs to include your project dependencies as well.

To minimize what dependencies are shipped to consumers, we might want to add `tools.build` and the ClojureScript compiler to their own `:build` alias. Unfortunately, this creates some complications.

If we're using `deps.edn` to configure classpaths and `build.clj` to use the ClojureScript compiler build API, this wouldn't work:

```clojure
;;;
;;; deps.edn
;;;

{:paths
 ["{source paths}"]

 ; Project :deps.
 :deps
 {; Computer algebra system (CAS) with Node.js dependencies for ClojureScript.
  ;
  ; Has a deps.cljs.
  org.mentat/emmy
  {:mvn/version "{version}"}}

 :aliases
 {:build
  {; ❌ Replaces the project :deps.
   :deps
   {io.github.clojure/tools.build
    {:mvn/version "{version}"}

    org.clojure/clojurescript
    {:mvn/version "{version}"}}

   :ns-default
   build}}}

;;;
;;; build.clj
;;;

(ns build
  (:require
    [cljs.build.api :as b-cljs]
    [clojure.tools.build.api :as b-clj]))

(def basis (delay (b-clj/create-basis)))

(defn build [_]
  (b-cljs/build
    (b-cljs/inputs (:paths @basis))
    {; ...
     :main
     "test-clojurescript-bun.main"

     :npm-deps
     true

     :install-deps
     true

     :deps-cmd
     "bun"}))
```

Instead we might try this:

```clojure
;;;
;;; deps.edn
;;;

{:paths
 ["{source paths}"]

 ; Project :deps.
 :deps
 {; Computer algebra system (CAS) with Node.js dependencies for ClojureScript.
  ;
  ; Has a deps.cljs.
  org.mentat/emmy
  {:mvn/version "{version}"}}

 :aliases
 {:build
  {; ✅ Adds to the project :deps.
   :extra-deps
   {io.github.clojure/tools.build
    {:mvn/version "{version}"}

    org.clojure/clojurescript
    {:mvn/version "{version}"}}

   :ns-default
   build}}}

;;;
;;; build.clj
;;;

(ns build
  (:require
    [cljs.build.api :as b-cljs]
    [clojure.tools.build.api :as b-clj]))

(def basis (delay (b-clj/create-basis)))

(defn build [_]
  (b-cljs/build
    (b-cljs/inputs (:paths @basis))
    {; ...
     :main
     "test-clojurescript-bun.main"

     :npm-deps
     true

     :install-deps
     true

     :deps-cmd
     "bun"}))
```

Except, this still doesn't work if we run `clj -T:build build` because tools do __not__ use the project classpath.

> In the Clojure CLI, "tools" are programs that provide functionality and do not use your project deps or classpath. Tools executed with `-T:an-alias` remove all project deps and paths, add "." as a path, and include any other deps or paths as defined in `:an-alias`.
>
> — [Clojure - tools.build Guide](https://clojure.org/guides/tools_build)

So even though we've used `:extra-deps`, it behaves as if it's `:deps`.

Unless there's a way to change the classpath the ClojureScript compiler build API sees, we can't directly use it in `build.clj`.

Instead, we need to start a new Java process and use the ClojureScript compiler CLI instead.

```clojure
;;;
;;; deps.edn
;;;

{:paths
 ["{source paths}"]

 ; Project :deps.
 :deps
 {; Computer algebra system (CAS) with Node.js dependencies for ClojureScript.
  ;
  ; Has a deps.cljs.
  org.mentat/emmy
  {:mvn/version "{version}"}}

 :aliases
 {:build
  {; ✅ Both :deps and :extra-deps behave like the former with `clj -T:build {fn}`.
   :deps
   {io.github.clojure/tools.build
    {:mvn/version "{version}"}}

   :ns-default
   build}

  :cljs
  {; ✅ Adds to the project :deps.
   :extra-deps
   {org.clojure/clojurescript
    {:mvn/version "{version}"}}

   :main-opts
   ["--main"
    "cljs.main"]}}}

;;;
;;; build.clj
;;;

(ns build
  (:require
    [clojure.tools.build.api :as b]))

(defn build [_]
  ; Install dependencies.
  (b/process
    {:command-args
     ["clj"
      "-M:cljs"
      ; Init options.
      "--compile-opts"
      (pr-str
        {; Needed for installing dependencies.
         :deps-cmd
         "bun"})
      ; Main options.
      "--install-deps"]})
  (b/process
    {:command-args
     ["bun"
      "install"]})
  ; Compile.
  (b/process
    {:command-args
     ["clj"
      "-M:cljs"
      ; Init options.
      "--compile-opts"
      (pr-str
        {; ...
         ; Needed for indexing node_modules.
         :deps-cmd
         "bun"})
      ; Main options.
      "--compile"
      "test-clojurescript-bun.main"]}))
```

This works, though getting here required looking at the ClojureScript compiler source code and lots of trial + error due to some sharp edges. In particular:

* We need to split the `--install-deps` and `--compile` main options since the CLI only seems to run whichever's first in a single invocation.
* Some [ClojureScript compiler options](https://clojurescript.org/reference/compiler-options) need to be passed as CLI options (e.g. `--{option}`) instead of in the `--compile-opts` EDN string/file.
	* For example, the main namespace is passed with `--compile {main namespace}` instead of the `:main` entry.
