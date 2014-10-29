(ns congestion.test-utils
  (:require [clojure.test :refer :all]
            [congestion.limits :as l]))

(def default-response
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body "Hello, world!"})

(defn rate-limit [rsp] (some-> rsp
                               (get-in [:headers "X-RateLimit-Limit"])
                               Integer.))

(defn remaining [rsp] (some-> rsp
                              (get-in [:headers "X-RateLimit-Remaining"])
                              Integer.))

(defn retry-after [rsp] (get-in rsp [:headers "Retry-After"]))

(defrecord MethodRateLimit [method period quota]
  l/RateLimit
  (get-quota [self]
    quota)
  (get-key [self req]
    (let [req-method (:request-method req)]
      (if (= req-method method)
        (str ::method "-" (-> req :request-method name)))))
  (get-period [self]
    period))
