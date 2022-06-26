(ns fun.leihs-oidc.resources.request-sign-in.main
  (:refer-clojure :exclude [keyword str])
  (:require
    [cheshire.core :as json]
    [clojure.contrib.humanize :as humanize]
    [cuerdas.core :as string :refer [lower split]]
    [fun.leihs-oidc.utils.jwt :as jwt]
    [fun.leihs-oidc.config-file :as config]
    [fun.leihs-oidc.nonces :as nonces]
    [fun.leihs-oidc.remote-openid-config :as remote-openid-config]
    [fun.leihs-oidc.utils.core :refer [keyword presence str]]
    [fun.leihs-oidc.utils.url :as url]
    [logbug.debug :as debug]
    [ring.util.response :as response]
    [taoensso.timbre :refer [error warn info debug spy]]))



;;; main ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defonce last-sign-in-request* (atom nil))


(defn build-uri [nonce state]
  (string/join
    [(remote-openid-config/authorization-endpoint)
     "?" (string/join
           "&" [(str "client_id=" (url/encode (config/client-id)))
                (str "redirect_uri=" (url/encode (config/redirect-url)))
                (str "nonce=" (url/encode nonce ))
                (str "scope=" (url/encode"openid swissEduIDBase swissEduIDExtended"))
                (str "state=" (url/encode state))
                "response_type=code"
                "response_mode=form_post"])]))

(comment (build-uri "foo"))

(defonce last-leihs-data* (atom nil))

; TODO try catch
(defn sign-in [{{token :token} :query-params-parsed
                request-method :request-method :as request}]
  (try
    (reset! last-sign-in-request* request)
    (let [leihs-data (jwt/leihs-unsign token)
          nonce (nonces/create)
          _ (info 'sign-in-request leihs-data)
          _ (reset! last-leihs-data* leihs-data)
          state (jwt/my-sign {:sign_in_request_token token
                              :nonce nonce})]
      (debug leihs-data)
      (let [url (build-uri nonce state)]
        (info 'redirecting-to-switch (string/prune url 80))
        (response/redirect url)))
    (catch Exception e
      (throw (ex-info "Unexpected error in request sign-in handler." {:status 500} e)))))

(defn handler [{request-method :request-method :as request}]
  (debug request)
  (sign-in request))


;#### debug ###################################################################
;(debug/debug-ns *ns*)
