(ns fun.leihs-oidc.config-file
  (:refer-clojure :exclude [str keyword])
  (:require
    [buddy.core.keys :as buddy-keys]
    [clj-yaml.core :as yaml]
    [cuerdas.core :as string :refer [snake kebab upper]]
    [environ.core :refer [env]]
    [fun.leihs-oidc.http.routes :refer [path]]
    [fun.leihs-oidc.utils.cli :refer [long-opt-for-key]]
    [fun.leihs-oidc.utils.core :refer [keyword presence presence! str get-in! get-in-presence!]]
    [taoensso.timbre :as timbre :refer [error warn info debug spy]]))


(defonce config* (atom nil))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def config-file-key :config-file)
(def config-file "config.yml" )

(def defaults
  {config-file-key config-file})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn env-or-default-value [k]
  (or (some-> k env presence)
      (some-> k defaults presence)
      (throw (ex-info (str "No default for key "k " defined!" {})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def cli-options
  [["-c" (long-opt-for-key config-file-key)
    :default (env-or-default-value config-file-key)]])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn import-keypair [cfg ks-prefix]
  (-> cfg
      (update-in (conj ks-prefix :private-key)
                 #(some-> % buddy-keys/str->private-key))
      (update-in (conj ks-prefix :public-key)
                 #(some-> % buddy-keys/str->public-key))
      (update-in (conj ks-prefix :algorithm)
                 #(some-> % str upper keyword))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn redirect-url []
  (str (get-in-presence! @config* [:service :external-base-url])
       (path :redirect {:id (get-in-presence! @config* [:service :id])})))

(defn leihs-remote-pub-key []
  (get-in-presence! @config* [:leihs :remote-key-pair :public-key]))

(defn leihs-my-pub-key []
  (get-in-presence! @config* [:leihs :my-key-pair :public-key]))

(defn leihs-my-priv-key []
  (get-in-presence! @config* [:leihs :my-key-pair :private-key]))

(defn leihs-my-key-algo []
  (-> (get-in-presence! @config* [:leihs :my-key-pair :algorithm])
      str string/lower keyword))


(defn client-id []
  (get-in-presence! @config* [:oidc :client-id]))

(defn client-secret []
  (get-in-presence! @config* [:oidc :client-secret]))

(defn leihs-organization []
  (get-in-presence! @config*  [:leihs :organization]))

(defn leihs-api-token []
  (get-in-presence! @config* [:leihs :api-token]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



(defn init [options]
  (info "initializing, read and parse config ...")
  (when-let [filename (get options config-file-key)]
    (let [config (-> filename slurp yaml/parse-string
                     (import-keypair [:leihs :my-key-pair])
                     (import-keypair [:leihs :remote-key-pair]))]
      (reset! config* config)))
  (info "initialized, read and parsed config-file ")
  (debug @config*))

