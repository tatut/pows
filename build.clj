(ns build
  (:require [clojure.tools.build.api :as b])
  (:import (java.time LocalDate)))

(def version (str (LocalDate/now) "." (b/git-count-revs nil)))
(def basis (b/create-basis {:project "deps.edn"}))
(def uberjar (str "target/pows-" version "-standalone.jar"))
(def class-dir "target/classes")

(defn uber [_]
  (spit "resources/version" version)
  (b/delete {:path "target"})
  (b/compile-clj {:basis basis
                  :ns-compile '[pows.core]
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uberjar
           :basis basis
           :main 'pows.core}))
