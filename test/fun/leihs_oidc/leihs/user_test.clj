(ns fun.leihs-oidc.leihs.user-test
  (:require
    [clj-http.client :as http-client]
    [clojure.test :refer [deftest is testing]]
    [fun.leihs-oidc.leihs.user :as user]
    [fun.leihs-oidc.switch.main :as switch]
    [fun.leihs-oidc.test-config :refer [with-leihs-config]]))

(def base-url "https://leihs.example")

(def properties
  {:org_id "123456"
   :email "ada@org.example"
   :organization "org.example"
   :lastname "Example"
   :firstname "Ada"})

(def existing-user
  (assoc properties :id "user-id" :account_enabled true))

(defn run
  "Runs create-or-update-user against fake leihs accounts.
  Returns the result or the thrown ex-data, and the write requests."
  [{:keys [by-org-id by-email]}]
  (let [writes (atom [])]
    (with-redefs [user/request-user-by-org-id (fn [_ _] by-org-id)
                  user/request-user-by-email (fn [_ email] (get by-email email))
                  user/patch-user (fn [_ id props]
                                    (swap! writes conj [:patch id props])
                                    {:id id})
                  http-client/request (fn [req]
                                        (swap! writes conj [:post (:url req)])
                                        {:body {:id "new-id"}})]
      {:result (try (user/create-or-update-user base-url properties)
                    (catch clojure.lang.ExceptionInfo e
                      {:status (:status (ex-data e)) :refusal? (switch/refusal? e)}))
       :writes @writes})))

(def affiliation-mode {:org-id-claim "swissEduIDLinkedAffiliationUniqueID"})


(deftest default-mode-test
  (with-leihs-config
    {}
    (fn []
      (testing "a miss removes the email from the other account and creates a user"
        (is (= {:result {:id "new-id"}
                :writes [[:patch "other-id" {:email nil}]
                         [:post (str base-url "/admin/users/")]]}
               (run {:by-email {"ada@org.example" {:id "other-id"}}}))))
      (testing "a changed email is removed from the other account"
        (is (= [[:patch "other-id" {:email nil}]
                [:patch "user-id" {:email "ada@org.example"}]]
               (:writes (run {:by-org-id (assoc existing-user :email "old@org.example")
                              :by-email {"ada@org.example" {:id "other-id"}}}))))))))

(deftest linked-affiliation-mode-test
  (with-leihs-config
    affiliation-mode
    (fn []
      (testing "a miss with a free email creates a user"
        (is (= {:result {:id "new-id"}
                :writes [[:post (str base-url "/admin/users/")]]}
               (run {}))))
      (testing "a miss with an email of another account refuses and writes nothing"
        (is (= {:result {:status 409 :refusal? true}
                :writes []}
               (run {:by-email {"ada@org.example" {:id "other-id"}}}))))
      (testing "a hit with unchanged properties writes nothing"
        (is (= {:result existing-user :writes []}
               (run {:by-org-id existing-user
                     :by-email {"ada@org.example" existing-user}}))))
      (testing "a hit with a changed email of another account refuses and writes nothing"
        (is (= {:result {:status 409 :refusal? true}
                :writes []}
               (run {:by-org-id (assoc existing-user :email "old@org.example")
                     :by-email {"ada@org.example" {:id "other-id"}}}))))
      (testing "a hit with a changed free email updates the user only"
        (is (= [[:patch "user-id" {:email "ada@org.example"}]]
               (:writes (run {:by-org-id (assoc existing-user :email "old@org.example")})))))
      (testing "the email lookup finds the same account"
        (is (= [[:patch "user-id" {:email "ada@org.example"}]]
               (:writes (run {:by-org-id (assoc existing-user :email "ADA@org.example")
                              :by-email {"ada@org.example" existing-user}}))))))))
