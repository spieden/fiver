(ns watership.core
  (:require [clojure.core.async :as async]
            [langohr.basic :as lb]
            [langohr.consumers :as lc]
            [langohr.channel :as lch]))

(defn- make-receive-handler [out-chan ack-chan]
  (fn [_ meta ^bytes payload]
    (async/>!! out-chan {:meta     meta
                         :payload  payload
                         :ack-chan ack-chan})))

(defn- put-ack-or-nack [orig-msg ack-or-nack]
  (async/go (async/>! (:ack-chan orig-msg) {:type     ack-or-nack
                                            :orig-msg orig-msg})))

(defn nack! [msg]
  (put-ack-or-nack msg :nack))

(defn ack! [msg]
  (put-ack-or-nack msg :ack))

(defn- do-ack-or-nack [amqp-chan msg]
  (let [delivery-tag (get-in msg [:orig-msg :meta :delivery-tag])]
    (if (= (:type msg) :ack)
      (lb/ack amqp-chan delivery-tag)
      (lb/reject amqp-chan delivery-tag true))))

(defn- pump-messages [amqp-chan queue-name queue-chan control-chan]
  (let [ack-chan        (async/chan)
        handler-chan    (async/chan)
        message-handler (make-receive-handler handler-chan ack-chan)
        consumer-tag    (lc/subscribe amqp-chan
                                      queue-name
                                      message-handler
                                      {:auto-ack false})]
    (async/go
      (try
        (loop [draining?      false
               in-flight-msgs #{}]
          (let [[msg chan] (async/alts! [handler-chan ack-chan control-chan])]
            (condp = chan
              handler-chan (if draining?
                             (do (nack! msg)
                                 (recur true in-flight-msgs))
                             (do (async/>! queue-chan msg)
                                 (recur false (conj in-flight-msgs msg))))

              ack-chan (do (do-ack-or-nack amqp-chan msg)
                           (when-not (and draining? (empty? in-flight-msgs))
                             (recur draining? (disj in-flight-msgs (:orig-msg msg)))))

              control-chan (when (= msg :drain)
                             (lb/cancel amqp-chan consumer-tag)
                             (when-not (empty? in-flight-msgs)
                               (recur true in-flight-msgs))))))
        :done
        (catch Throwable t t)
        (finally
          (async/close! handler-chan)
          (async/close! queue-chan)
          (async/close! ack-chan))))))

(defn chan-for-queue [amqp-conn queue-name & {:keys [in-flight-count]
                                              :or {in-flight-count 1}}]
  (let [amqp-chan    (lch/open amqp-conn)
        _            (lb/qos amqp-chan in-flight-count)
        control-chan (async/chan)
        queue-chan   (async/chan in-flight-count)
        pump-chan    (pump-messages amqp-chan queue-name queue-chan control-chan)]
    {:queue-chan   queue-chan
     :control-chan control-chan
     :pump-chan    pump-chan}))

