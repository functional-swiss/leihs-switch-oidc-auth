(ns fun.leihs-oidc.utils.cli
  (:require
    [cuerdas.core :as string :refer [snake kebab upper human]]
    ))

(defn extract-options-keys [cli-options]
  (->> cli-options
       (map (fn [option]
              (or (seq (drop-while #(not= :id %) option))
                  (throw (ex-info
                           "option requires :id to extract-options-keys"
                           {:option option})))))
       (map second)))



(defn long-opt-for-key [k]
  (str "--" (kebab k) " " (-> k snake upper)))
