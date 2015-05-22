# fiver

> "I think we ought to do all we can to make these creatures friendly. It might turn out to be well
> worth the trouble." - Hazel, "Watership Down"

Fiver makes core.async channels for consuming RabbitMQ queues via
[langohr](http://clojurerabbitmq.info/). Test coverage is thorough but production use is currently
limited (let me know your experience).

Langohr provides two built-in ways to consume messages from queues:

  * Callbacks via [langohr.consumers/subscribe](http://reference.clojurerabbitmq.info/langohr.consumers.html#var-subscribe)
  * Blocking reads via [langohr.basic/get](http://reference.clojurerabbitmq.info/langohr.basic.html#var-get)

Fiver gives you a third option that lets you consume RabbitMQ messages more naturally in your [core.async](https://github.com/clojure/core.async)
based programs.

## Usage



## Contributing

## License

Copyright © 2015 Exaptic Systems

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

