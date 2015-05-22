(ns watership.test-queues
  (:require [langohr.core :as rmq]
            [langohr.channel :as lch]
            [langohr.queue :as lq]
            [langohr.basic :as lb]
            [watership.core :as core]
            [clojure.edn :as edn]))

(def lifetime-millis 200)

(defn do-with-queue [fn]
  (let [conn (rmq/connect)
        chan (lch/open conn)]
    (try (let [declare-args {"x-expires" lifetime-millis}
               queue-name  (lq/declare-server-named chan {:auto-delete false
                                                          :arguments declare-args})
               publish-fn  #(lb/publish chan "" queue-name (prn-str %))
               async-chans (core/chan-for-queue conn queue-name)]
           (fn {:publish publish-fn
                :chans async-chans
                :queue-name queue-name
                :conn conn}))
         (finally
           (lch/close chan)
           (rmq/close conn)))))

(defmacro with-queue [map-destructure & body]
  `(do-with-queue #(let [~map-destructure %] ~@body)))

(defn get-payload [msg]
  (edn/read-string (String. (:payload msg) "utf-8")))

