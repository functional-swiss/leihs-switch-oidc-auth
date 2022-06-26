(ns fun.leihs-oidc.http.ring-exception
  (:refer-clojure :exclude [str keyword])
  (:require
    [clojure.stacktrace]
    [cuerdas.core :as string]
    [fun.leihs-oidc.utils.core :refer [keyword presence str]]
    [hiccup.core :refer [html ]]
    [hiccup.page :refer [html5 include-css]]
    [logbug.thrown :as thrown]
    [taoensso.timbre :refer [error warn info debug spy]]))


(defn logstr [e]
  ; TODO poosible iterate over exception when (instance? java.sql.SQLException)
  (-> (str (.getMessage e) " "
           (with-out-str
             (clojure.stacktrace/print-cause-trace e)))
      (string/replace "\n" " <<< ")
      (string/collapse-whitespace)))

(defn exception-response [e]
  (let [code  (or (and (instance? clojure.lang.ExceptionInfo e)
                       (:status (ex-data e)))
                  "500")]

    {:status (:status (ex-data e))
     :headers {"Content-Type" "text/html; charset=UTF-8"}
     :body (html5 {:lang "en"}
                  [:head [:meta {:charset "utf8"
                                 :name "viewport"
                                 :content "width=device-width, initial-scale=1"}]
                   [:title "Authentication Error"]]
                  (include-css "https://cdn.jsdelivr.net/npm/bootstrap@5.2.0/dist/css/bootstrap.min.css")
                  [:body
                   [:div.container
                    [:h1 "Authentication Error"]
                    [:div.alert.alert-warning
                     [:h2.alert-heading "Error code: " code]
                     [:p (.getMessage e)]]
                    [:div.alert.alert-primary
                     [:p "Use the browser back button to go back to leihs."]]
                    [:div.alert.alert-secondary
                     [:p "Contact your leihs support if this error persists. "
                      "Please supply the error message and code as displayed above."  ]]]])}))


(defn wrap [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable e
        (let [resp (exception-response e)]
          (case (:status resp)
            (401 403) (warn (logstr e) (ex-data e) {:request request})
            (error  (logstr e) (ex-data e) {:request request}))
          resp)))))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
