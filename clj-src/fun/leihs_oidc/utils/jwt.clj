(ns fun.leihs-oidc.utils.jwt
  (:refer-clojure :exclude [str keyword])
  (:require
    [buddy.core.codecs :as codecs]
    [buddy.core.codecs.base64 :as b64]
    [buddy.sign.jwt :as jwt]
    [cheshire.core :as json]
    [cuerdas.core :as string :refer [lower split]]
    [fun.leihs-oidc.config-file :as config]
    [fun.leihs-oidc.constants :as constants]
    [fun.leihs-oidc.remote-openid-config :refer [get-key-by-kid]]
    [fun.leihs-oidc.utils.core :refer [keyword presence str]]
    [taoensso.timbre :as timbre :refer [debug info warn error spy]]))


(defn validate-algorithm! [algo]
  (when-not (contains? constants/ALLOWED_JWT_ALGOS algo)
    (throw (ex-info "Used JWT algorithm is not allowed! "
                    {:allowed constants/ALLOWED_JWT_ALGOS
                     :algo algo}))))

(defn decode [token]
  (->> (split token ".")
       (take 2)
       (map b64/decode)
       (map codecs/bytes->str)
       (map #(json/parse-string % true))))


(defn leihs-unsign [token]
  (let [[{alg :alg :as header}
         {nonce :nonce :as payload}] (decode token)
        algorithm (-> alg str lower keyword)]
    (debug [algorithm payload])
    (validate-algorithm! algorithm)
    (jwt/unsign token (config/leihs-remote-pub-key) {:alg algorithm})))


(defn my-sign [data]
  (jwt/sign
    data
    (config/leihs-my-priv-key)
    {:alg (config/leihs-my-key-algo)}))

(defn my-unsign [token]
  (jwt/unsign
    token
    (config/leihs-my-pub-key)
    {:alg (config/leihs-my-key-algo)}))


(defn validate-id-token! [id-token]
  (debug (pr-str (decode id-token)))
  (let [[{kid :kid alg :alg :as header} value] (decode id-token)
        sig-key (get-key-by-kid kid)]
    (debug {:id-token id-token :header header :value value :alg alg})
    (debug sig-key)
    (jwt/unsign id-token sig-key
                {:skip-validation false
                 :alg (-> alg str lower keyword)})))


