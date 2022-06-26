(ns fun.leihs-oidc.run
  (:require
    [fun.leihs-oidc.http.server :as http-server]
    [fun.leihs-oidc.remote-openid-config :as remote-openid-config]
    [fun.leihs-oidc.http.routing :as routing]
    [fun.leihs-oidc.config-file :as config-file]
    [clojure.pprint :refer [pprint]]
    [clojure.tools.cli :as cli :refer [parse-opts]]
    [environ.core :refer [env]]
    [taoensso.timbre :refer [debug info warn error]]
    ))

(def cli-options
  (concat
    [["-h" "--help"]]
    config-file/cli-options
    http-server/cli-options
    ))


(defn main-usage [options-summary & more]
  (->> ["Functional leihs-OIDC Ad-Hoc Authenticator RUN"
        ""
        "usage: fun-leihs-oidc [<opts>] run [<run-opts>]"
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


(defn run [options]
  (info "run with " options)
  (when (:dev-mode options)
    (warn 're-require)
    (require '[cider-ci.server.routing-resolver]
             '[cider-ci.server.routing]))
  (config-file/init options)
  (remote-openid-config/init @config-file/config*)
  (let [routes (routing/init options)]
    (http-server/init routes options)))

(defn main [gopts args]
  (debug 'main {'gopts gopts 'args args})
  (let [{:keys [options arguments
                errors summary]} (cli/parse-opts args cli-options :in-order true)
        cmd (some-> arguments first keyword)
        pass-on-args (->> (rest arguments) flatten (into []))
        options (merge gopts options)
        print-summary #(println (main-usage summary {:args args :options options}))]
    (info {'args args 'options options 'cmd cmd 'pass-on-args pass-on-args})
    (cond
      (:help options) (print-summary)
      :else (run options))))
