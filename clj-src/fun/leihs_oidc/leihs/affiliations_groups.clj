(ns fun.leihs-oidc.leihs.affiliations-groups
  (:refer-clojure :exclude [keyword str])
  (:require
    [cheshire.core :as cheshire]
    [clj-http.client :as http-client]
    [clojure.set :refer [difference]]
    [clojure.walk :refer [keywordize-keys stringify-keys]]
    [cuerdas.core :as string]
    [fun.leihs-oidc.config-file :as config]
    [fun.leihs-oidc.leihs.shared :refer [basic-request-properties]]
    [fun.leihs-oidc.utils.core :refer [keyword presence presence! str get-in! get-in-presence!]]
    [fun.leihs-oidc.utils.url :as url]
    [logbug.debug :as debug]
    [slingshot.slingshot :refer [try+]]
    [taoensso.timbre :refer [error warn info debug spy]]
    ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn leihs-affiliation-groups [base-url]
  "Returns a map org_id -> group"
  ; technically we would have to iterate over pagination, practically
  ; affiliations are always less than 10
  (-> (basic-request-properties)
      (assoc :method :get)
      (assoc :url (str base-url "/admin/groups/"))
      (assoc :query-params {:organization (config/leihs-organization)})
      http-client/request spy :body spy :groups spy
      (->> (map (fn [g] [(:org_id g) g]))
           (into {}))))

(comment (leihs-affiliation-groups "http://test.home.arpa"))

(defn create-group [base-url affiliation]
  (-> (basic-request-properties)
      (assoc :url (str base-url "/admin/groups/"))
      (assoc :method :post)
      (assoc :body (cheshire/generate-string
                     (merge
                       (get-in-presence! @config/config* [:leihs :group-create-defaults])
                       {:org_id affiliation
                        :organization (config/leihs-organization)
                        :name (str (string/capital affiliation)
                                   " " (config/leihs-organization))})))
      spy http-client/request spy :body spy))

(defn leihs-groups [base-url affiliations]
  (doseq [affil-id (->> (leihs-affiliation-groups base-url) keys set
                        (difference affiliations))]
    (create-group base-url affil-id))
  (leihs-affiliation-groups base-url))

(comment
  (debug/get-last-argument #'leihs-groups)
  (debug/re-apply-last-argument #'leihs-groups))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn update-memberships [base-url leihs-user affiliations leihs-groups]
  (doseq [add-to-affiliation affiliations]
    (debug "add-to-affiliation " add-to-affiliation)
    (-> (basic-request-properties)
        (assoc :method :put)
        (assoc :body "{}")
        (assoc :url (str base-url "/admin/groups/"
                         (get-in-presence! leihs-groups [add-to-affiliation :id])
                         "/users/" (:id leihs-user)))
        spy http-client/request))
  (comment
    ; TODO reenable this, needs leihs upgrade to send 404 when
    ; user ist not in the group
    (doseq [remove-from-affiliation (difference (-> leihs-groups keys set)
                                                affiliations)]
      (try+
        (-> (basic-request-properties)
            (assoc :method :delete)
            (dissoc :content-type)
            (assoc :url (str base-url "/admin/groups/"
                             (get-in-presence! leihs-groups [remove-from-affiliation :id])
                             "/users/" (:id leihs-user)))
            spy http-client/request)
        (catch [:status 404] {} nil)
        (catch Exception e
          (throw (ex-info
                   (str "Failed to remove user from the group. "
                        "See logs for details.")
                   {:status 500} e)))))))

(comment (debug/re-apply-last-argument #'update-memberships))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn update-groups [base-url leihs-user affiliations]
  (let [leihs-groups (leihs-groups base-url affiliations)]
    (update-memberships base-url leihs-user affiliations leihs-groups)))

(comment (debug/re-apply-last-argument #'update-groups))


;;; debug ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;(debug/debug-ns *ns*)


