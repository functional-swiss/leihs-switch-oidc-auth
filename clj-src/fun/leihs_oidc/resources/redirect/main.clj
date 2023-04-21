(ns fun.leihs-oidc.resources.redirect.main
  (:refer-clojure :exclude [keyword str])
  (:require
    [cheshire.core :as json]
    [clj-http.client :as http-client]
    [clojure.contrib.humanize :as humanize]
    [clojure.pprint :refer [pprint]]
    [cuerdas.core :as string :refer [lower split]]
    [fun.leihs-oidc.config-file :refer [redirect-url client-id client-secret]]
    [fun.leihs-oidc.constants :as constants]
    [fun.leihs-oidc.leihs.user :as leihs-user]
    [fun.leihs-oidc.leihs.affiliations-groups :as leihs-affiliations-groups]
    [fun.leihs-oidc.nonces :as nonces]
    [fun.leihs-oidc.remote-openid-config :refer [token-endpoint userinfo-endpoint]]
    [fun.leihs-oidc.state :as state]
    [fun.leihs-oidc.switch.main :as switch]
    [fun.leihs-oidc.utils.core :refer [keyword presence str get-in-presence!]]
    [fun.leihs-oidc.utils.jwt :as jwt]
    [hiccup.core :refer [html]]
    [hiccup.page :refer [html5]]
    [logbug.debug :as debug]
    [ring.util.response :as response]
    [taoensso.timbre :refer [error warn info debug spy]]))


(defonce last-token-response-body* (atom nil))

(comment
  (-> @last-token-response-body* :id_token jwt/decode)
  (-> @last-token-response-body* :access_token))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn request-token [code]
  (->> {;:save-request? true :debug-body true
        :as :json
        :basic-auth [(client-id) (client-secret)]
        :form-params {:grant_type "authorization_code"
                      :code code
                      :redirect_uri (redirect-url)}}
       spy
       (http-client/post (token-endpoint))
       spy
       ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn request-user-info
  [{access_token :access_token :as token-resp-body}]
  (->> {;:save-request? true
        ;:debug-body true
        :as :json
        :headers {:authorization (str "Bearer " access_token)}}
       (http-client/get (userinfo-endpoint))))

(comment (-> @last-token-response-body* request-user-info))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



(comment
  (-> @last-token-response-body* :id_token jwt/validate-id-token!))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;(defonce last-id-token* (atom nil))

(defn handler [{{state-token :state code :code} :params :as request}]
  (try
    (let [{{base-url :server_base_url
            path :path} :leihs-data
           :as state} (as-> state-token state
                        (jwt/my-unsign state)
                        (assoc state :leihs-data
                               (jwt/leihs-unsign (:sign_in_request_token state))))
          _ (info 'redirect state)
          {token-resp-body :body :as token-resp} (request-token code)
          _ (reset! last-token-response-body* token-resp-body)
          id-token (-> token-resp-body :id_token jwt/validate-id-token!)
          _ (nonces/validate! (:nonce id-token))
          user-info (:body (request-user-info token-resp-body))
          _ (info 'switch-user-info user-info)
          user-attributes (switch/user-attributes id-token user-info)]
      (assert base-url)
      (assert path)
      ; TODO when ad-hoc user management enabled
      (when true
        (info 'managing-leihs-user user-attributes)
        (let [user (leihs-user/create-or-update-user
                     base-url user-attributes)
              affiliations (switch/affiliations user-info)]
          (info 'managing-leihs-groups affiliations)
          (leihs-affiliations-groups/update-groups base-url user affiliations)))
      (let [token (jwt/my-sign
                     (spy {:sign_in_request_token (:sign_in_request_token state)
                           :email (:email user-attributes)
                           :success true}))
            url (str (get-in-presence! state [:leihs-data :server_base_url])
                     (get-in-presence! state [:leihs-data :path])
                     "?token=" token)]
        (info 'redirecting-to-leihs (string/prune url 80))
        (response/redirect url)))
    (catch Exception e
      (warn e)
      (throw (ex-info "Unexpected error in redirect." {:status 500} e)))))
