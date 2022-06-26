(ns fun.leihs-oidc.utils.logging.core (:refer-clojure :exclude [str keyword])
  (:require
    [fun.leihs-oidc.utils.core :refer [keyword presence str]]
    [taoensso.timbre :as timbre :refer [debug info]]))

(def DEFAULT_CONFIG
  {:min-level [[#{
                  ;"fun.leihs-oidc.leihs.main"
                  ;"fun.leihs-oidc.leihs.affiliations-groups"
                  ;"fun.leihs-oidc.resources.redirect.main"
                  }
                :debug]
               [#{"fun.leihs-oidc.*"} :info]
               [#{"*"} :warn]]
   :log-level nil})
