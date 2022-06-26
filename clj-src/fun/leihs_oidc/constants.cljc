(ns fun.leihs-oidc.constants
  (:require
    [cuerdas.core :as string :refer []]
    [fun.leihs-oidc.utils.url :as url]
    [taoensso.timbre :as timbre :refer [error warn info debug spy]]))

(def ALLOWED_JWT_ALGOS #{:es256, :es512
                         :eddsa,
                         :rs256, :rs512})


(def SWITCH_AUTH_BASE_URL
  (string/join
    ["https://login.test.eduid.ch/idp/profile/oidc/authorize?"
     (string/join
       "&" [
            ; TODO hardcoded for now
            (str "client_id=" (url/encode "functional.swiss-leihs-test"))
            ; TODO hardcoded for now
            (str "redirect_uri="  (url/encode "http://localhost:3200/authenticators/switch-open-id/functional/sign-in"))
            ; TODO real nonce
            (str "nonce=" (url/encode (string/join (shuffle [1 2 3 4 5 6 7 8]))))
            "scope=openid"
            "state=JustATestForNow"
            "response_type=code"
            "response_mode=form_post"])]))


"http://localhost:3200/authenticators/leihs-oidc/functional/sign-in"
"http://localhost:3200/authenticators/switch-open-id/functional/sign-in"
