(ns watership.core-test
  (:require [clojure.test :refer :all]
            [langohr.core :as rmq]
            [langohr.channel :as lch]
            [langohr.queue :as lq]
            [watership.core :as core]
            [langohr.basic :as lb]
            [clojure.core.async :as async]))

(defn do-with-queue [fn]
  (let [conn (rmq/connect)
        chan (lch/open conn)]
    (try (let [queue-name  (lq/declare-server-named chan {:exclusive true})
               publish-fn  #(lb/publish chan "" queue-name %)
               async-chans (core/chan-for-queue conn queue-name)]
           (fn publish-fn async-chans))
         (finally
           (lch/close chan)
           (rmq/close conn)))))

(defmacro with-queue [publish-fn-symbol chan-symbol & body]
  `(do-with-queue #(let [~publish-fn-symbol %1
                         ~chan-symbol %2]
                    ~@body)))

(defn get-payload [msg]
  (String. (:payload msg) "utf-8"))

(deftest chan-for-queue
  (with-queue publish chans
    (testing "Nacked message is redelivered"
      (publish "test")
      (let [queue-chan (:queue-chan chans)
            msg1 (async/<!! queue-chan)
            _ (core/nack! msg1)
            msg2 (async/<!! queue-chan)]
        (is (= "test" (get-payload msg2))))))

  (with-queue publish chans
    (testing "Acked message isn't redelivered"
      (publish "test")
      (let [queue-chan (:queue-chan chans)
            msg1 (async/<!! queue-chan)
            _ (core/ack! msg1)
            [msg2 _] (async/alts!! [queue-chan] :default nil)]
        (is (= "test" (get-payload msg1)))
        (is (nil? msg2)))))

  (with-queue publish chans
    (testing "Draining shuts down pump when nothing in flight"
      (async/>!! (:control-chan chans) :drain)
      (is (= (async/<!! (:pump-chan chans))
             :done))))

  (with-queue publish chans
    (testing "Draining keeps pump running until in-flight message acked"
      )))

; in-flight limited to what's passed
;
