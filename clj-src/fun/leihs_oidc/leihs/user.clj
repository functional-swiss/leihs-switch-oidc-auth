(ns fun.leihs-oidc.leihs.user
  (:refer-clojure :exclude [keyword str])
  (:require
    [cheshire.core :as cheshire]
    [clj-http.client :as http-client]
    [clojure.walk :refer [keywordize-keys stringify-keys]]
    [cuerdas.core :as string]
    [fun.leihs-oidc.config-file :as config]
    [fun.leihs-oidc.leihs.shared :refer [basic-request-properties]]
    [fun.leihs-oidc.switch.main :as switch]
    [fun.leihs-oidc.utils.core :refer [keyword presence presence! str get-in! get-in-presence!]]
    [fun.leihs-oidc.utils.url :as url]
    [logbug.debug]
    [slingshot.slingshot :refer [try+]]
    [taoensso.timbre :refer [error warn info debug spy]]
    ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn patch-user [base-url id props]
  (try (-> (basic-request-properties)
           (assoc :body  (-> props cheshire/generate-string))
           (assoc :method :patch)
           (assoc :url (str base-url "/admin/users/" id))
           spy http-client/request spy :body)
       (catch Exception e
         (throw (ex-info
                  (str "Failed to patch user account. "
                       "See service logs for details.")
                  {:status 500} e)))))

(defn request-user-by-email [base-url email]
  (try+ (-> (basic-request-properties)
            (assoc :method :get)
            (assoc :url (str base-url "/admin/users/"
                             (url/encode email)))
            spy http-client/request spy :body)
        (catch [:status 404] {} nil)
        (catch Exception e
          (throw (ex-info
                   (str "Failed to request-user-by-email. "
                        "See service logs for details.")
                   {:status 500} e)))))

(defn remove-email
  "Remove the email address from some account if it exists."
  [base-url email]
  (try (when-let [account (request-user-by-email base-url email)]
         (patch-user base-url (:id account) {:email nil}))
       (catch Exception e
         (throw (ex-info
                  (str "Failed to remove email address "
                       "See service logs for details.")
                  {:status 500} e)))))

(defn update-user [base-url nominal-props user]
  "Update the user account iff target-prots are distinct from current-props."
  (debug 'update-user nominal-props user)
  (try (let [target-user-props (merge {}
                                      (get-in-presence!
                                        @config/config*
                                        [:leihs :user-update-defaults])
                                      nominal-props)

             ks (keys target-user-props)
             current-user-props (select-keys user ks)
             diff-ks (->> ks
                          (filter #(not= (% target-user-props)
                                         (% current-user-props)))
                          set)]
         (if-not (empty? diff-ks)
           (do
             ; new email some other account might have it => remove it
             (when (contains? diff-ks :email)
               (remove-email base-url (:email target-user-props)))
             (patch-user base-url (:id user)
                         (select-keys target-user-props diff-ks)))
           user))
       (catch Exception e
         (throw (ex-info
                  (str "Failed to update-user account. "
                       "See service logs for details.")
                  {:status 500} e)))))

(defn create-user [base-url properties]
  "Create a new user with given properties"
  ; we don't know if some other user already has the email address => remove it
  (remove-email base-url (:email properties))
  (try
    (-> (basic-request-properties)
        (assoc :body (-> (merge
                           (get-in-presence!
                             @config/config*
                             [:leihs :user-create-defaults])
                           properties) keywordize-keys cheshire/generate-string))
        (assoc :method :post)
        (assoc :url (str base-url "/admin/users/"))
        http-client/request :body)
    (catch Exception e
      (throw (ex-info
               (str "Failed to create user account. "
                    "See service logs for details.")
               {:status 500} e)))))

(defn request-user-by-org-id [base-url org-id]
  (try+ (-> (basic-request-properties)
            (assoc :method :get)
            (assoc :url (str base-url "/admin/users/"
                             (url/encode
                               (str (config/leihs-organization)
                                    "|" org-id ))))
            spy http-client/request spy :body)
        (catch [:status 404] {} nil)
        (catch Exception e
          (throw (ex-info
                   (str "Failed to request-user-by-org"
                        "See service logs for details.")
                   {:status 500} e)))))

(defn create-or-update-user
  "Creates or updates leihs user account. Returns the properties of the user account.
  Potentially removes the designated primary email-address from some other account. "
  [base-url {email :email
             organization :organization
             org-id :org_id :as properties}]
  (try+
    (if-let [user (request-user-by-org-id base-url org-id)]
      (update-user base-url properties user)
      (create-user base-url properties))
    (catch Exception e
      (throw (ex-info
               (str "Failed to manage the user account in leihs. "
                    "Details of the error can be found in the authenticator logs.")
               {:status 500} e)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

