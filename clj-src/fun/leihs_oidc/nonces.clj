(ns fun.leihs-oidc.nonces
  (:require
    [cuerdas.core :as string :refer []]
    [fun.leihs-oidc.utils.url :as url]
    [taoensso.timbre :as timbre :refer [error warn info debug spy]])
  (:import  [java.util UUID]))


(defonce nonces* (atom #{}))

(defn create []
  (let [nonce (UUID/randomUUID)]
    (swap! nonces* conj nonce)
    nonce))

(defn validate!
  "Validates presences of nonce and removes it to prevent reuse."
  [nonce]
  (if (instance? String nonce)
    (validate! (UUID/fromString nonce))
    (swap! nonces*
           (fn [nonces nonce]
             (when-not (contains? nonces nonce)
               (throw (ex-info "Nonce has already been used or expired, please try again." {:status 422})))
             (disj nonces nonce))
           nonce)))

