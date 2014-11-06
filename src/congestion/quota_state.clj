(ns congestion.quota-state
  (:require [congestion.limits :as l]
            [congestion.responses :as r]
            [congestion.storage :as s]))

(defprotocol QuotaState
  (quota-exhausted? [self])
  (increment-counter [self storage])
  (build-error-response [self response-builder])
  (rate-limit-response [self rsp]))

(defrecord UnlimitedQuota []
  QuotaState
  (quota-exhausted? [self]
    false)

  (increment-counter [self storage]
    nil)

  (build-error-response [self response-builder]
    (assert false))

  (rate-limit-response [self rsp]
    rsp))

(defrecord AvailableQuota [key ttl quota remaining]
  QuotaState
  (quota-exhausted? [self]
    false)

  (increment-counter [self storage]
    (s/increment-count storage key ttl))

  (build-error-response [self response-builder]
    (assert false))

  (rate-limit-response [self rsp]
    (r/rate-limit-response rsp key)))

(defrecord ExhaustedQuota [key retry-after quota]
  QuotaState
  (quota-exhausted? [self]
    true)

  (increment-counter [self storage]
    (assert false))

  (build-error-response [self response-builder]
    (let [rsp (if response-builder
                (response-builder key retry-after)
                (r/too-many-requests-response key retry-after))]
      (r/rate-limit-response rsp key)))

  (rate-limit-response [self rsp]
    (assert false)))

(defn read-quota-state
  "Read the quota state from storage.

  Generates a storage key from the limit and the ring request. If the
  key is `nil`, the request will not be rate-limited. Otherwise looks
  up the current counter value from storage and based on the counter
  the quota state is either exhausted or available."
  [storage limit req]
  (if-let [key (l/get-key limit req)]
    (let [ttl (l/get-ttl limit req)
          quota (l/get-quota limit req)
          current-count (s/get-count storage key)
          remaining-requests (- quota current-count 1)]
      (if (neg? remaining-requests)
        (->ExhaustedQuota key (s/counter-expiry storage key) quota)
        (->AvailableQuota key ttl quota remaining-requests)))
    (->UnlimitedQuota)))
