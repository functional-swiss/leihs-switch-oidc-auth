(ns fun.leihs-oidc.switch.main-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [fun.leihs-oidc.config-file :as config]
    [fun.leihs-oidc.switch.main :as switch]
    [fun.leihs-oidc.test-config :refer [with-leihs-config]]))

(defn thrown-data [f]
  (try (f) nil
       (catch clojure.lang.ExceptionInfo e
         {:status (:status (ex-data e)) :refusal? (switch/refusal? e)})))

(def affiliation-mode {:org-id-claim "swissEduIDLinkedAffiliationUniqueID"})

(def id-token {:swissEduPersonUniqueID "1234567890123456@eduid.ch"})

(def user-info
  {:given_name "Ada"
   :family_name "Example"
   :swissEduIDLinkedAffiliationUniqueID ["123456@org.example" "999@other.example"]
   :swissEduIDLinkedAffiliationMail ["ada@other.example" "ada@students.org.example"]
   :swissEduIDLinkedAffiliation ["student@org.example" "staff@other.example"]})


(deftest claim-values-test
  (is (= ["a@org.example"] (switch/claim-values "a@org.example")))
  (is (= ["a@org.example" "b@org.example"]
         (switch/claim-values ["a@org.example" "b@org.example"])))
  (is (nil? (switch/claim-values nil))))

(deftest linked-affiliation-org-id-test
  (testing "list with one matching scope"
    (is (= "123456" (switch/linked-affiliation-org-id
                      ["999@other.example" "123456@org.example"] "org.example"))))
  (testing "single string instead of a list"
    (is (= "123456" (switch/linked-affiliation-org-id
                      "123456@org.example" "org.example"))))
  (testing "scope compares case-insensitively"
    (is (= "123456" (switch/linked-affiliation-org-id
                      ["123456@ORG.Example"] "org.EXAMPLE"))))
  (testing "scope must match exactly, a subdomain does not match"
    (is (= {:status 422 :refusal? true}
           (select-keys (thrown-data #(switch/linked-affiliation-org-id
                                        ["123456@students.org.example"
                                         "777@notorg.example"] "org.example"))
                        [:status :refusal?]))))
  (testing "no matching scope"
    (is (= 422 (:status (thrown-data #(switch/linked-affiliation-org-id
                                        ["999@other.example"] "org.example")))))
    (is (:refusal? (thrown-data #(switch/linked-affiliation-org-id nil "org.example"))))
    (is (:refusal? (thrown-data #(switch/linked-affiliation-org-id [] "org.example")))))
  (testing "blank local part or no @ does not match"
    (is (:refusal? (thrown-data #(switch/linked-affiliation-org-id
                                   ["@org.example" "org.example"] "org.example")))))
  (testing "the scope is the part after the last @"
    (is (= "a@b" (switch/linked-affiliation-org-id ["a@b@org.example"] "org.example"))))
  (testing "same local part twice is not ambiguous"
    (is (= "123456" (switch/linked-affiliation-org-id
                      ["123456@org.example" "123456@ORG.EXAMPLE"] "org.example"))))
  (testing "different local parts with the same scope are ambiguous"
    (is (= {:status 422 :refusal? true}
           (select-keys (thrown-data #(switch/linked-affiliation-org-id
                                        ["123456@org.example" "654321@org.example"]
                                        "org.example"))
                        [:status :refusal?])))))


(deftest default-mode-test
  (with-leihs-config
    {}
    (fn []
      (testing "settings default to the organization"
        (is (= "swissEduPersonUniqueID" (config/leihs-org-id-claim)))
        (is (not (config/key-on-linked-affiliation-id?)))
        (is (= "org.example" (config/leihs-affiliation-scope)))
        (is (= "org.example" (config/leihs-email-domain))))
      (testing "org_id is the personal edu-ID number"
        (is (= {:org_id "1234567890123456"
                :email "ada@students.org.example"
                :organization "org.example"
                :lastname "Example"
                :firstname "Ada"}
               (switch/user-attributes id-token user-info))))
      (testing "affiliations end with the organization"
        (is (= #{"student" "switch-oidc"} (switch/affiliations user-info))))
      (testing "a single string counts as one value"
        (is (= "ada@org.example"
               (:email (switch/user-attributes
                         id-token
                         (assoc user-info :swissEduIDLinkedAffiliationMail
                                "ada@org.example")))))
        (is (= #{"staff" "switch-oidc"}
               (switch/affiliations
                 {:swissEduIDLinkedAffiliation "staff@org.example"}))))
      (testing "no email with the organization domain"
        (is (= {:status 422 :refusal? false}
               (thrown-data #(switch/user-attributes
                               id-token
                               (assoc user-info :swissEduIDLinkedAffiliationMail
                                      ["ada@other.example"]))))))))
  (with-leihs-config
    {:org-id-claim "" :affiliation-scope "" :email-domain ""}
    (fn []
      (testing "blank settings fall back to the defaults"
        (is (= "swissEduPersonUniqueID" (config/leihs-org-id-claim)))
        (is (= "org.example" (config/leihs-affiliation-scope)))
        (is (= "org.example" (config/leihs-email-domain)))))))

(deftest unsupported-org-id-claim-test
  (with-leihs-config
    {:org-id-claim "swissEduIDLinkedAffiliationUniqueId"}
    (fn []
      (is (thrown? clojure.lang.ExceptionInfo (config/leihs-org-id-claim)))
      (is (thrown? clojure.lang.ExceptionInfo
                   (switch/user-attributes id-token user-info))))))

(deftest linked-affiliation-mode-test
  (with-leihs-config
    affiliation-mode
    (fn []
      (testing "org_id is the local part of the linked affiliation ID"
        (is (= {:org_id "123456"
                :email "ada@students.org.example"
                :organization "org.example"
                :lastname "Example"
                :firstname "Ada"}
               (switch/user-attributes id-token user-info))))
      (testing "no fallback to the personal edu-ID number"
        (is (= {:status 422 :refusal? true}
               (thrown-data #(switch/user-attributes
                               id-token
                               (dissoc user-info :swissEduIDLinkedAffiliationUniqueID))))))
      (testing "ambiguous linked affiliation IDs"
        (is (:refusal? (thrown-data
                         #(switch/user-attributes
                            id-token
                            (assoc user-info :swissEduIDLinkedAffiliationUniqueID
                                   ["123456@org.example" "654321@org.example"])))))))))

(deftest affiliation-scope-and-email-domain-test
  (with-leihs-config
    (merge affiliation-mode
           {:affiliation-scope "demo.example"
            :email-domain "org.example"})
    (fn []
      (let [user-info {:given_name "Ada"
                       :family_name "Example"
                       :swissEduIDLinkedAffiliationUniqueID "42@demo.example"
                       :swissEduIDLinkedAffiliationMail ["ada@demo.example" "ada@org.example"]
                       :swissEduIDLinkedAffiliation ["staff@demo.example" "student@org.example"]}]
        (testing "org_id uses the affiliation scope, email uses the email domain"
          (is (= {:org_id "42"
                  :email "ada@org.example"
                  :organization "org.example"
                  :lastname "Example"
                  :firstname "Ada"}
                 (switch/user-attributes id-token user-info))))
        (testing "affiliations use the affiliation scope"
          (is (= #{"staff" "switch-oidc"} (switch/affiliations user-info))))))))
