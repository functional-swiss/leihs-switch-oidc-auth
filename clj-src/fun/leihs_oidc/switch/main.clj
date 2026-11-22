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

(defn claim-values
  "Returns the values of a claim as a sequence;
  a claim with a single value can arrive as a plain string."
  [v]
  (if (string? v) [v] v))

(defn refusal
  "An exception that refuses the sign-in; the message is shown to the user."
  [message status]
  (ex-info message {:status status ::refusal true}))

(defn refusal? [e]
  (boolean (some-> e ex-data ::refusal)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn select-affiliations [affiliations]
  (->> affiliations
       claim-values
       (filter #(string/ends-with?
                  (string/lower %)
                  (string/lower (config/leihs-affiliation-scope))))))

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
       claim-values
       (filter #(string/ends-with?
                  (string/lower %)
                  (string/lower (config/leihs-email-domain))))))


(defn order-emails [emails]
  (sort-by
    (fn [email] (-> email (string/split "@") last count))
    emails))

(defn throw-no-suiteable-email []
  (throw
    (ex-info
      "No suiteable affiliation email present but is required!"
      {:status 422})))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn split-unique-id
  "Splits a unique ID like 123456@org.example at the last @
  into [local-part scope]."
  [unique-id]
  (let [i (.lastIndexOf ^String unique-id "@")]
    (when (pos? i)
      [(subs unique-id 0 i) (subs unique-id (inc i))])))

(defn linked-affiliation-org-id
  "Returns the local part of the linked affiliation unique ID whose scope
  equals the given scope (case-insensitive). Refuses the sign-in if no ID
  or more than one distinct ID has this scope."
  [unique-ids scope]
  (let [local-parts (->> unique-ids
                         claim-values
                         (filter string?)
                         (keep split-unique-id)
                         (filter (fn [[_ s]] (= (string/lower s)
                                                (string/lower scope))))
                         (keep (fn [[local-part _]] (presence local-part)))
                         distinct)]
    (case (count local-parts)
      0 (throw (refusal
                 (str "Your edu-ID account has no linked affiliation with "
                      scope ". Link this affiliation in your edu-ID account "
                      "and sign in again.")
                 422))
      1 (first local-parts)
      (throw (refusal
               (str "Your edu-ID account has more than one linked "
                    "affiliation ID for " scope ". Sign-in is not possible. "
                    "Contact your leihs support.")
               422)))))

(defn org-id [id-token user-info]
  (if (config/key-on-linked-affiliation-id?)
    (linked-affiliation-org-id
      (:swissEduIDLinkedAffiliationUniqueID user-info)
      (config/leihs-affiliation-scope))
    (-> id-token :swissEduPersonUniqueID
        (string/split "@") first presence!)))


(defn user-attributes [id-token user-info]
  (-> {}
      (assoc :org_id (org-id id-token user-info))
      (assoc :email (-> user-info :swissEduIDLinkedAffiliationMail
                        (->> select-emails order-emails first)
                        (or (throw-no-suiteable-email))))
      (assoc :organization (config/leihs-organization))
      (assoc :lastname (:family_name user-info))
      (assoc :firstname (:given_name user-info))))
