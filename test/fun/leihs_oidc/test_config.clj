(ns fun.leihs-oidc.test-config
  (:require
    [fun.leihs-oidc.config-file :as config]))

(def base-config
  {:leihs {:organization "org.example"
           :api-token "test-token"
           :user-create-defaults {:account_enabled true}
           :user-update-defaults {:account_enabled true}}})

(defn with-leihs-config
  "Runs f with base-config merged with the given :leihs settings."
  [leihs-settings f]
  (let [previous @config/config*]
    (reset! config/config* (update base-config :leihs merge leihs-settings))
    (try (f)
         (finally (reset! config/config* previous)))))
