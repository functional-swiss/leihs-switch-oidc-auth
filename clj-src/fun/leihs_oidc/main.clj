(ns fun.leihs-oidc.main
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.tools.cli :as cli]
    [fun.leihs-oidc.run :as run]
    [fun.leihs-oidc.utils.exit :as exit]
    [fun.leihs-oidc.utils.logging.main :as service-logging]
    [fun.leihs-oidc.utils.repl :as repl]
    [taoensso.timbre :refer [debug info warn error spy]])
  (:gen-class))


(def cli-options
  (concat
    [["-h" "--help"]
     ["-d" "--dev-mode"]]
    repl/cli-options
    service-logging/cli-options))

(defn main-usage [options-summary & more]
  (->> ["Fun leihs-OIDC "
        ""
        "usage: fun-leihs-oidc [<opts>] SCOPE [<scope-opts>] [<args>]"
        ""
        "available scopes: run"
        ""
        "Options:"
        options-summary
        ""
        ""
        (when more
          ["-------------------------------------------------------------------"
           (with-out-str (pprint more))
           "-------------------------------------------------------------------"])]
       flatten (clojure.string/join \newline)))

(defonce args* (atom nil))

(defn helpnexit [summary args options]
  (println (main-usage summary {:args args :options options}))
  (exit/exit))

; TODO try catch exit when startup exception

(defn- main []
  (info 'main [@args*])
  (let [args @args*
        {:keys [options arguments errors summary]}
        (cli/parse-opts args cli-options :in-order true)
        options (merge (sorted-map) options)]
    (service-logging/init options)
    (exit/init options)
    (repl/init options)
    (cond
      (:help options) (helpnexit summary args options)
      :else (case (-> arguments first keyword)
              :run (run/main options (rest arguments))
              (helpnexit summary args options)))))

; reload/restart stuff when requiring this file in dev mode
(when @args* (main))

(defn -main [& args]
  (service-logging/init {}) ; setup logging with some sensible defaults
  (info '-main [args])
  (reset! args* args)
  (main))

