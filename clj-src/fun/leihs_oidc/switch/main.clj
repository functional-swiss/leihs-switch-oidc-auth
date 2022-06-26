(ns fun.leihs-oidc.switch.main
  (:refer-clojure :exclude [keyword str])
  (:require
    [cheshire.core :as cheshire]
    [clj-http.client :as http-client]
    [clojure.walk :refer [keywordize-keys stringify-keys]]
    [cuerdas.core :as string]
    [fun.leihs-oidc.config-file :as config]
    [fun.leihs-oidc.leihs.affiliations-groups :as affiliations-groups]
    [fun.leihs-oidc.leihs.shared :refer [basic-request-properties]]
    [fun.leihs-oidc.utils.core :refer [keyword presence presence! str get-in! get-in-presence!]]
    [fun.leihs-oidc.utils.url :as url]
    [logbug.debug :as debug]
    [slingshot.slingshot :refer [try+]]
    [taoensso.timbre :refer [error warn info debug spy]]
    ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn select-affiliations [affiliations]
  (->> affiliations
       (filter #(string/ends-with?
                  (string/lower %)
                  (string/lower (config/leihs-organization))))))

(defn affiliations [user-info]
  "Returns a sequence of words based on the affiliations
  ammended with \"switch-oidc\"; e.g. (\"students\", \"staff\", \"switch-oidc\") "
  (-> user-info
      :swissEduIDLinkedAffiliation
      (->> select-affiliations
           (map #(string/split % "@"))
           (map first)
           set)
      (conj (str "switch-oidc"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn select-emails [emails]
  (->> emails
       (filter #(string/ends-with?
                  (string/lower %)
                  (string/lower (config/leihs-organization))))))


(defn order-emails [emails]
  (sort-by
    (fn [email] (-> email (string/split "@") last count))
    emails))

(defn throw-no-suiteable-email []
  (throw
    (ex-info
      "No suiteable affiliation email present but is required!"
      {:status 422})))


(defn user-attributes [id-token user-info]
  (-> {}
      (assoc :org_id  (-> id-token :swissEduPersonUniqueID
                          (string/split "@") first presence!))
      (assoc :email (-> user-info :swissEduIDLinkedAffiliationMail
                        (->> select-emails order-emails first)
                        (or (throw-no-suiteable-email))))
      (assoc :organization (config/leihs-organization))
      (assoc :lastname (:family_name user-info))
      (assoc :firstname (:given_name user-info))))
