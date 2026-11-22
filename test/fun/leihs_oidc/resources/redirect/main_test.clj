(ns fun.leihs-oidc.resources.redirect.main-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [fun.leihs-oidc.http.ring-exception :as ring-exception]
    [fun.leihs-oidc.nonces :as nonces]
    [fun.leihs-oidc.resources.redirect.main :as redirect]
    [fun.leihs-oidc.test-config :refer [with-leihs-config]]
    [fun.leihs-oidc.utils.jwt :as jwt]))

(defn sign-in-response
  "Runs the redirect handler with the given userinfo up to the user
  attributes and returns the status and message of the error page."
  [user-info]
  (with-redefs [jwt/my-unsign (fn [_] {:sign_in_request_token "t"})
                jwt/leihs-unsign (fn [_] {:server_base_url "https://leihs.example"
                                          :path "/sign-in"})
                redirect/request-token (fn [_] {:body {:id_token "id"}})
                jwt/validate-id-token! (fn [_] {:nonce "n"
                                                :swissEduPersonUniqueID "1234567890123456@eduid.ch"})
                nonces/validate! (fn [_] true)
                redirect/request-user-info (fn [_] {:body user-info})]
    (try (redirect/handler {:params {:state "s" :code "c"}})
         (catch Exception e
           (let [resp (ring-exception/exception-response e)]
             {:status (:status resp) :message (.getMessage e)})))))

(deftest refusal-reaches-the-user-test
  (with-leihs-config
    {:org-id-claim "swissEduIDLinkedAffiliationUniqueID"}
    (fn []
      (testing "a missing linked affiliation ID shows status 422 and the reason"
        (let [{:keys [status message]} (sign-in-response
                                         {:swissEduIDLinkedAffiliationMail ["ada@org.example"]})]
          (is (= 422 status))
          (is (re-find #"no linked affiliation with org.example" message)))))))

(deftest other-errors-stay-unchanged-test
  (with-leihs-config
    {}
    (fn []
      (testing "a missing email is still an unexpected error"
        (is (= {:status 500 :message "Unexpected error in redirect."}
               (sign-in-response {:swissEduIDLinkedAffiliationMail ["ada@other.example"]})))))))
