(ns fun.leihs-oidc.http.routing
  (:refer-clojure :exclude [keyword str])
  (:require
    [clj-yaml.core :as yaml]
    [clojure.walk :refer [keywordize-keys]]
    [logbug.debug :as debug :refer [I>]]

    [logbug.ring :refer [wrap-handler-with-logging]]
    [fun.leihs-oidc.resources.redirect.main :as redirect]
    [fun.leihs-oidc.http.static-resources :as static-resources]
    [fun.leihs-oidc.http.routes :as routes]
    [fun.leihs-oidc.utils.cli :refer [long-opt-for-key]]
    [fun.leihs-oidc.utils.core :refer [keyword presence str]]
    [fun.leihs-oidc.http.ring-exception :as ring-exception]
    [fun.leihs-oidc.resources.request-sign-in.main :as request-sign-in]
    [fun.leihs-oidc.resources.status.main :as status]
    [ring.middleware.accept]
    [ring.middleware.content-type :refer [wrap-content-type]]
    [ring.middleware.cookies]
    [ring.middleware.json]
    [ring.middleware.keyword-params]
    [ring.middleware.params]
    [taoensso.timbre :as logging :refer [debug info warn error spy]]
    ))


;;; cli-options ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce options* (atom nil))
(def cache-busting-enabled-key :http-cache-busting-enabled)
(def options-keys [cache-busting-enabled-key])

(def cli-options
  [[nil (long-opt-for-key cache-busting-enabled-key)
    "YAML falsy to disable, which should never be neccessacy"
    :default (or
               (some-> cache-busting-enabled-key :env yaml/parse-string)
               true)
    :parse-fn #(yaml/parse-string %)
    :validate [boolean? "Must be a bool"]]])

;;; routing ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def resolve-table
  {:status #'status/handler
   :redirect #'redirect/handler
   :request-sign-in #'request-sign-in/handler
   })

(defn route-resolve [handler request]
  (debug 'route-resolve (:uri request))
  (if-let [route (some-> request :uri
                         routes/route)]
    (let [{{route-name :name} :data} route
          params-coerced (routes/coerce-params route)
          ; replace plain :path-params with coerced ones
          route (update route :path-params
                        #(merge {} % (:path params-coerced)))]
      (debug 'route route)
      (debug 'route-coreced-params params-coerced)
      (debug "route match" route-name)
      (handler (-> request
                   (assoc
                     :route route
                     :route-name route-name
                     :route-handler (resolve-table route-name))
                   (update-in [:params] #(merge {} % (:path-params route))))))
    (handler request)))

(defn wrap-route-resolve [handler]
  (fn [request]
    (route-resolve handler request)))

(defn route-dispatch [handler request]
  (if-let [route-handler (:route-handler request)]
    (route-handler request)
    (handler request)))

(defn wrap-route-dispatch [handler]
  (fn [request]
    (route-dispatch handler request)) )

;;; routing ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def handler-chain* (atom nil))

(defn not-found-handler [request]
  {:status 404
   :body "Not Found"})

(defn wrap-accept [handler]
  (ring.middleware.accept/wrap-accept
    handler
    {:mime
     ["application/json" :qs 1 :as :json
      "image/apng" :qs 0.8 :as :apng
      "text/css" :qs 1 :as :css
      "text/html" :qs 1 :as :html]}))

(defn wrap-add-vary-header [handler]
  "should be used if content varies based on `Accept` header, e.g. if using `ring.middleware.accept`"
  (fn [request]
    (let [response (handler request)]
      (assoc-in response [:headers "Vary"] "Accept"))))

(defn wrap-parsed-query-params [handler]
  (fn [request]
    (handler
      (assoc request :query-params-parsed
             (->> request :query-params
                  (map (fn [[k v]] [(keyword  k) (yaml/parse-string v)]))
                  (into {}))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn build-routes []
  (I> wrap-handler-with-logging
      not-found-handler
      wrap-route-dispatch
      ;(logbug.ring/wrap-handler-with-logging :info)
      wrap-route-resolve
      ring.middleware.json/wrap-json-response
      (ring.middleware.json/wrap-json-body {:keywords? true})
      wrap-parsed-query-params
      ring.middleware.keyword-params/wrap-keyword-params
      ring.middleware.params/wrap-params
      ;(logbug.ring/wrap-handler-with-logging :debug)
      wrap-accept
      wrap-add-vary-header
      ring.middleware.cookies/wrap-cookies
      (static-resources/wrap
        "" {:allow-symlinks? true
            :cache-bust-paths []
            :never-expire-paths
            [#".*[^\/]*\d+\.\d+\.\d+.+"  ; match semver in the filename
             #".+\.[0-9a-fA-F]{32,}\..+"] ; match MD5, SHAx, ... in the filename
            :cache-enabled? (cache-busting-enabled-key @options*)})
      ring-exception/wrap
      wrap-content-type))

(defn init [options]
  (reset! options* (select-keys options options-keys))
  (logging/info "initializing routing " @options* " ...")
  (reset! handler-chain* (build-routes))
  (logging/info "initialized routing")
  @handler-chain*)

;(logbug.debug/debug-ns 'fun.leihs-oidc.resources.ws-back)
;(logbug.debug/debug-ns *ns*)
