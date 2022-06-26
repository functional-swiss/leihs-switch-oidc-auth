(ns fun.leihs-oidc.remote-openid-config
  "Uses the `openid-config-url` to fetch and parse the openid-config"
  (:refer-clojure :exclude [keyword str])
  (:require
    [buddy.core.keys :refer [jwk->public-key]]
    [clj-http.client :as http-client]
    [clj-yaml.core :as yaml]
    [cuerdas.core :as string :refer [snake kebab upper]]
    [environ.core :refer [env]]
    [fun.leihs-oidc.config-file :as config-file]
    [fun.leihs-oidc.utils.cli :refer [long-opt-for-key]]
    [fun.leihs-oidc.utils.core :refer [keyword presence presence! str get-in-presence!]]
    [taoensso.timbre :as timbre :refer [error warn info debug spy]]))

(defonce config* (atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce keys* (atom nil))

(comment
  (->> @keys* :keys (map identity))
  (map jwk->public-key (:keys @keys*)))

(defn get-key-by-kid [kid]
  (-> @keys* :keys
      (->> (filter #(= kid (:kid %)))
           first)
      (or (throw (ex-info (str kid " kid not found within keys") {})))
      jwk->public-key))

(defn rsa-sig-key []
  (get-key-by-kid "defaultRSASign"))

(defn ec-sig-key []
  (get-key-by-kid "defaultECSign"))

(comment (rsa-sig-key)
         (ec-sig-key))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn fetch-config [config]
  (-> config
      (get-in-presence! [:oidc :config-url])
      (http-client/get {:accept :json :as :json})
      :body))

(defn fetch-keys [url]
  (-> url
      (http-client/get {:accept :json :as :json})
      :body))

(defn authorization-endpoint []
  (-> @config* :authorization_endpoint presence!))

(defn token-endpoint []
  (-> @config* :token_endpoint presence!))

(defn userinfo-endpoint []
  (-> @config* :userinfo_endpoint presence!))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init [config]
  (info "fetching remote openid configuration ...")
  (reset! config* (fetch-config config))
  (when (empty? @config*)
    (throw (ex-info "failed to fetch remote openid configuration" {})))
  (if-let [jwks-uri (-> @config* :jwks_uri presence)]
    (reset! keys* (fetch-keys jwks-uri))
    (throw (ex-info "jwks_uri not found in remote openid configuration" {})))
  (when (empty? @keys*)
    (throw (ex-info "failed to fetch remote keys" {})))
  (info "fetched remote openid configuration")
  (debug @config*))

(comment (init @config-file/config*) )
