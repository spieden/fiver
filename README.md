# fiver

> "I think we ought to do all we can to make these creatures friendly. It might turn out to be well
> worth the trouble."
>> Hazel, "Watership Down"

[![Build Status](https://travis-ci.org/spieden/fiver.svg?branch=master)](https://travis-ci.org/spieden/fiver)

Fiver makes core.async channels for consuming RabbitMQ queues via
[langohr](http://clojurerabbitmq.info/). Test coverage is thorough but production use is currently
limited (let me know your experience).

Langohr provides two built-in ways to consume messages from queues:

  * Callbacks via [langohr.consumers/subscribe](http://reference.clojurerabbitmq.info/langohr.consumers.html#var-subscribe)
  * Blocking reads via [langohr.basic/get](http://reference.clojurerabbitmq.info/langohr.basic.html#var-get)

Fiver creates a third option for more naturally consuming RabbitMQ messages in your [core.async](https://github.com/clojure/core.async)
based programs.

## Dependency

[exaptic/fiver "0.1.0"]

## REPL Tour



## Contributing

In order to run the tests you'll need a RabbitMQ server running locally such that [rmq/connect](http://reference.clojurerabbitmq.info/langohr.core.html#var-connect) succeeds without any parameters (the default out-of-the-box behavior).

## License

Copyright © 2015 Exaptic Systems

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

