(ns fun.leihs-oidc.state
  (:require
    [taoensso.timbre :as timbre :refer [error warn info debug spy]]))

(defonce state* (atom {}))


(defn leihs-remote-pub-key []
  (get-in @state* [:config :leihs-remote-key-pair :public-key]))



