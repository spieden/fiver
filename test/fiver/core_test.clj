(ns fiver.core-test
  (:require [clojure.test :refer :all]
            [fiver.core :as core]
            [fiver.test-queues :as tqs]
            [clojure.core.async :as async]))

(def rabbitmq-deliver-timeout-millis 300)
(def rabbitmq-ack-timeout-millis 200)

(deftest chan-for-queue
  (tqs/with-queue {:keys [publish chans]}
    (testing "Nacked message is redelivered"
      (publish :foo)
      (let [queue-chan (:queue chans)
            msg1 (async/<!! queue-chan)
            _ (core/nack! msg1)
            msg2 (async/<!! queue-chan)]
        (is (= :foo (tqs/get-payload msg2))))))

  (tqs/with-queue {:keys [publish chans conn queue-name]}
    (testing "Message never acked is redelivered to fresh channel after a kill"
      (publish :foo)

      (async/<!! (:queue chans))
      (async/>!! (:control chans) :kill)
      (is (= (async/<!! (:termination chans)) :done))

      (let [fresh-chans (core/chan-for-queue conn queue-name)
            msg (async/<!! (:queue fresh-chans))]
        (is (= :foo (tqs/get-payload msg))))))

  (tqs/with-queue {:keys [publish chans conn queue-name]}
    (testing "Acked message is not redelivered to fresh channel"
      (publish :foo)

      (core/ack! (async/<!! (:queue chans)))
      (async/>!! (:control chans) :drain)
      (is (= (async/<!! (:termination chans)) :done))

      (let [second-chans (core/chan-for-queue conn queue-name)
            [msg _] (async/alts!! [(:queue second-chans)
                                   (async/timeout rabbitmq-deliver-timeout-millis)])]
        (is (nil? msg)))))

  (tqs/with-queue {:keys [publish chans queue-name conn]}
    (testing "Messages only consumed from queue up to in-flight limit"
      (publish :foo)
      (publish :bar)
      (async/<!! (:queue chans))
      (let [fresh-chans (core/chan-for-queue conn queue-name)
            msg (async/<!! (:queue fresh-chans))]
        ; if more than the in-flight limit (1) had been consumed, we'd never get here
        (is (= :bar (tqs/get-payload msg))))))

  (tqs/with-queue {:keys [publish chans queue-name conn]}
    (testing "In flight message count is constrained by limit"
      (publish :foo)
      (publish :bar)
      (async/<!! (:queue chans))
      (is (nil? (first (async/alts!! [(:queue chans)
                                      (async/timeout rabbitmq-deliver-timeout-millis)]))))))

  (tqs/with-queue {:keys [publish queue-name conn]}
    (testing "Increasing in-flight message count allows more to be consumed at once"
      (publish :foo) ; default message pump consumes this one
      (publish :bar)
      (publish :baz)
      (let [fresh-chans (core/chan-for-queue conn queue-name :in-flight-count 2)
            bar-msg (async/<!! (:queue fresh-chans))
            baz-msg (async/<!! (:queue fresh-chans))]
        (is (= :bar (tqs/get-payload bar-msg)))
        (is (= :baz (tqs/get-payload baz-msg))))))

  (tqs/with-queue {:keys [chans]}
    (testing "Draining terminates when nothing previously in flight"
      (async/>!! (:control chans) :drain)
      (is (= (async/<!! (:termination chans))
             :done))))

  (tqs/with-queue {:keys [publish chans]}
    (testing "Draining terminates when message previously in flight"
      (publish :foo)
      (core/ack! (async/<!! (:queue chans)))
      (async/>!! (:control chans) :drain)
      (is (= (async/<!! (:termination chans))
             :done))))

  (tqs/with-queue {:keys [publish chans]}
    (testing "Draining doesn't terminate until in-flight message responded to"
      (publish :foo)
      (let [msg (async/<!! (:queue chans))
            check-term #(first (async/alts!! [(:termination chans)
                                              (async/timeout rabbitmq-ack-timeout-millis)]))]
        (async/>!! (:control chans) :drain)
        (is (nil? (check-term)))
        (core/ack! msg)
        (is (= (check-term) :done))))))
