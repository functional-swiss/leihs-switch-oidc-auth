(ns fun.leihs-oidc.leihs.shared
  (:refer-clojure :exclude [keyword str])
  (:require
    [cuerdas.core :as string]
    [fun.leihs-oidc.config-file :as config]
    [fun.leihs-oidc.utils.core :refer [keyword presence presence! str get-in! get-in-presence!]]
    [taoensso.timbre :refer [error warn info debug spy]]
    ))

(defn basic-request-properties []
  {:accept :json
   :as :auto
   :basic-auth [(config/leihs-api-token) ""]
   :content-type :json
   :insecure true})


