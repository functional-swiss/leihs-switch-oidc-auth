(ns fun.leihs-oidc.utils.logging.main
  (:refer-clojure :exclude [keyword str])
  (:require
    [fun.leihs-oidc.server.state :refer [state*]]
    [fun.leihs-oidc.utils.core :refer [keyword presence str]]
    [fun.leihs-oidc.utils.logging.core :as logging]
    [taoensso.timbre :as timbre]))

(timbre/merge-config! logging/DEFAULT_CONFIG)

(defn init [config]
  (timbre/merge-config! logging/DEFAULT_CONFIG)
  (swap! state* assoc-in [:logging :config] timbre/*config*)
  (timbre/info timbre/*config*))
