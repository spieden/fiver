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

(defn- put-response [msg op]
  (async/go (async/>! (:ack-chan msg) {:op  op
                                       :msg msg})))

(defn nack! [msg]
  (put-response msg :nack))

(defn ack! [msg]
  (put-response msg :ack))

(defn- do-response [amqp-chan msg op]
  (let [delivery-tag (get-in msg [:meta :delivery-tag])]
    (if (= op :ack)
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
                             (do (do-response amqp-chan msg :nack)
                                 (recur true in-flight-msgs))
                             (do (async/>! queue-chan msg)
                                 (recur false (conj in-flight-msgs msg))))

              ack-chan (do (do-response amqp-chan (:msg msg) (:op msg))
                           (let [new-in-flight (disj in-flight-msgs (:msg msg))]
                             (when-not (and draining? (empty? new-in-flight))
                               (recur draining? new-in-flight))))

              control-chan (when (#{:drain :kill} msg)
                             (lb/cancel amqp-chan consumer-tag)
                             (if (= msg :drain)
                               (when-not (empty? in-flight-msgs)
                                 (recur true in-flight-msgs))
                               (for [msg in-flight-msgs]
                                 (do-response amqp-chan msg :nack)))))))
        :done
        (catch Throwable t t)
        (finally
          (async/close! handler-chan)
          (async/close! queue-chan)
          (async/close! ack-chan)
          (lch/close amqp-chan))))))

(defn chan-for-queue [amqp-conn queue-name & {:keys [in-flight-count]
                                              :or   {in-flight-count 1}}]
  (let [amqp-chan        (lch/open amqp-conn)
        _                (lb/qos amqp-chan in-flight-count)
        control-chan     (async/chan)
        queue-chan       (async/chan in-flight-count)
        termination-chan (pump-messages amqp-chan queue-name queue-chan control-chan)]
    {:queue       queue-chan
     :control     control-chan
     :termination termination-chan}))

