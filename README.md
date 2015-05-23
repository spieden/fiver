# fiver

> "I think we ought to do all we can to make these creatures friendly. It might turn out to be well
> worth the trouble."
>> Hazel, "Watership Down"

[![Build Status](https://travis-ci.org/spieden/fiver.svg?branch=master)](https://travis-ci.org/spieden/fiver)

```clj
[exaptic/fiver "0.1.0"]
```

Fiver makes core.async channels for consuming RabbitMQ queues via [langohr](http://clojurerabbitmq.info/). Test coverage is thorough but production use is currently limited (let me know your experience).

## Basics

Here's a complete usage example for fiver followed by inline explanations.

First the [langohr setup](http://clojurerabbitmq.info/articles/getting_started.html#langohr-overview), just for completeness:

```clj
=> (require '[fiver.core :as fvr])

=> (require '[langohr.core :as rmq]
            '[langohr.channel :as lch]
            '[langohr.queue :as lq]
            '[langohr.basic :as lb]
            '[clojure.core.async :as async])

=> (def conn (rmq/connect))
=> (def amqp-chan (lch/open conn))
=> (def queue-name (lq/declare-server-named amqp-chan))

=> (lb/publish amqp-chan "" queue-name "hai")
```

Now to consume the message we just published via core.async:

```clj
=> (def fiver-chans (fvr/chan-for-queue conn queue-name))
=> (keys fiver-chans)
(:queue :control :termination)

=> (def msg (async/<!! (:queue fiver-chans)))
=> (keys msg)
(:meta :payload :response-chan)

=> (String. (:payload msg) "utf-8")
"hai"

=> (fvr/ack! msg)
:ok

=> (async/>!! (:control fiver-chans) :drain)
=> (async/<!! (:termination fiver-chans))
:done
```

Let's go through that in a little more detail.

```clj
=> (def fiver-chans (fvr/chan-for-queue conn queue-name))
=> (keys fiver-chans)
(:queue :control :termination)
```

The `chan-for-queue` function is the main entrypoint into fiver, and returns a map with three core.async channels in it:

| Channel      | Description |
| :queue       | Get messages pumped from the RabbitMQ queue |
| :control     | Put either :drain or :kill to shut down the message pump |
| :termination | Get either :done or an Exception when the message pump shuts down |

Draining stops the AMQP consumer and waits for all in-flight messages to be `ack!`ed or `nack!`ed. Killing stops the consumer and `nack!`s all in-flight messages.

```clojure
=> (def msg (async/<!! (:queue fiver-chans)))
=> (keys msg)
(:meta :payload :response-chan)

=> (String. (:payload msg) "utf-8")
"hai"
```

Messages received from the :queue channel contain the following keys:

| Key            | Description |
| :meta          | A map of "message metadata (content type, type, reply-to, etc) and delivery information (routing key, if the mesasge is redelivered, etc)" from langohr |
| :payload       | An array of bytes containing the message payload |
| :response-chan | A  core.async channel used by `ack!` and `nack!` (implementation detail -- no need to use directly) |

```clojure
=> (fvr/ack! msg)
:ok
```

Messages must be `ack!`ed or `nack!`ed before another will appear in the :queue channel, depending on the maximum in-flight count. If you `nack!` a message it will be redelivered to the same queue. (See next heading for details.)

```clojure
=> (async/>!! (:control fiver-chans) :drain)
=> (async/<!! (:termination fiver-chans))
:done
```

The :drain completes immediately because there are no messages in flight.

The :termination channel receives a :done after a :drain or :kill completes, or an Exception if one is caught while trying to pump messages. Once a message is received on the :termination channel, all other channels will be closed (in addition to the langohr channel fiver opens).

## In Flight Count

By default fiver consumes messages from RabbitMQ queues one at a time. (It uses :auto-ack false in its subscriptions and a QOS of 1.) If you want to retrieve more messages at a time for performance (buffering) or parallel processing you can increase the in-flight message count using the :in-flight-count keyword argument.

```clojure
=> (lb/publish amqp-chan "" queue-name "rich")
=> (lb/publish amqp-chan "" queue-name "hickey")

=> (def fiver-chans
     (fvr/chan-for-queue conn queue-name :in-flight-count 2))

=> (async/<!! (:queue fiver-chans))

; If we hadn't specified :in-flight-count 2 this call would block.
=> (async/<!! (:queue fiver-chans))
```

Both messages are immediately retrieved and buffered in the :queue channel when `chan-for-queue` is called, and so won't be available to other RabbitMQ consumers (unless they're `nack!`ed).

## Contributing

In order to run the tests you'll need a RabbitMQ server running locally such that [rmq/connect](http://reference.clojurerabbitmq.info/langohr.core.html#var-connect) succeeds without any parameters (the default out-of-the-box behavior).

## Other Solutions

Also see [kehaar](https://github.com/democracyworks/kehaar), another Watership Down character named RabbitMQ/core.async solution. Some minds think alike. =)

## License

Copyright © 2015 Exaptic Systems

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.

