(ns fun.leihs-oidc.http.routes
  (:require
    [cuerdas.core :as string :refer []]
    [fun.leihs-oidc.utils.query-params :as query-params]
    [reitit.coercion.schema :as reitit-schema]
    [reitit.coercion]
    [reitit.core :as reitit]
    [taoensso.timbre :refer [debug info warn error spy]]))


; http://127.0.0.1:3200/authenticators/switch-open-id/phbern/protected/redirect
; https://phbern.leihs.app/authenticators/switch-open-id/phbern/protected/redirect

; https://test.home.arpa:3200/authenticators/switch-open-id/phbern/protected/redirect

(def coerce-params reitit.coercion/coerce!)

(def routes
  [["/authenticators/switch-open-id/:id"
    ["" {:name :root}]
    ["/status" {:name :status}]
    ["/request-sign-in" :request-sign-in]
    ["/protected/redirect" {:name :redirect}]]])


(def router (reitit/router routes))

(def routes-flattened (reitit/routes router))


;(reitit/match->path (reitit/match-by-name router :upload {:upload-id 5}))
;(reitit/match-by-path router "/media-service/")

(defn route [path]
  (-> path
      (string/split #"\?" )
      first
      (->> (reitit/match-by-path router))))

(defn path
  ([kw]
   (path kw {}))
  ([kw route-params]
   (path kw route-params {}))
  ([kw route-params query-params]
   (when-let [p (reitit/match->path
                  (reitit/match-by-name
                    router kw route-params))]
     (if (seq query-params)
       (str p "?" (query-params/encode query-params))
       p))))

#?(:cljs
   (defn navigate!
     ([url]
      (navigation/navigate! url nil))
     ([url event &{:keys [reload]
                   :or {reload false}}]
      (if reload
        (set! js/window.location url)
        (navigation/navigate! url event)))))

(comment
  (path :redirect {:id :functional})
  )


(comment
  (->> [:user-email-addresses {:user-id "123"} ]
       spy
       (apply path)
       spy
       (reitit/match-by-path router)
       spy)

  (->> [:user-email-address {:user-id "123"
                             :email-address "12@abc"}]
       spy
       (apply path)
       spy
       ;(reitit/match-by-path router)
       spy
       )


  (reitit/match-by-name router :users {:user-id "123"}))
