[![CircleCI](https://circleci.com/gh/cognitect-labs/vase.svg?style=svg&circle-token=21b84b7aea75483821d3852de6c5d9930e85720a)](https://circleci.com/gh/cognitect-labs/vase)

[![Clojars Project](https://img.shields.io/clojars/v/com.cognitect/pedestal.vase.svg)](https://clojars.org/com.cognitect/pedestal.vase)

# Vase: Data-driven microservices

This system provides a data-driven and extensible way to describe and
run HTTP APIs. Building blocks make it very easy to use Datomic as a
durable database. Other databases can be added via an extension mechanism.

## Major Changes Since v0.9

We learned from using Vase on real-world projects and from hearing
feedback in our user community. People like the promise of Vase but
found it hard to use.

This release aims to make Vase much easier for common cases:

1. A new input format called
   [Fern](https://github.com/cognitect-labs/fern) lets us produce much
   better error messages when something goes wrong.
2. Step-by-step examples show how to grow an API from scratch
   rather than relying on the Leiningen template.
3. A new public API allows applications to skip the files altogether
   and directly feed Vase with data structures to describe the
   application. You can produce those however you like, from code,
   files, values in a database... whatever.
4. Datomic has been "demoted" by one namespace level so we can add
   support for other external integrations. Instead of `vase/query`,
   for example, you'll now use `vase.datomic/query`.

## Releases and Dependency Information

If you would like to use the latest developments in Vase, you will need to
clone this repository and install it locally:

```
git clone https://github.com/cognitect-labs/vase
cd vase
lein install
```

Stable versions are currently deployed to the Clojars repository.

[Leiningen](https://github.com/technomancy/leiningen) dependency information:

```clj
 [com.cognitect/pedestal.vase "0.9.3"]
```

[Maven](http://maven.apache.org/) dependency information:

```xml
<dependency>
  <groupId>com.cognitect</groupId>
  <artifactId>pedestal.vase</artifactId>
  <version>0.9.3</version>
</dependency>
```

## Before you get started

Vase is built on top of [Pedestal](http://pedestal.io/)
and [Datomic](http://www.datomic.com/). While you don't need to be a
Pedestal or Datomic expert to use Vase, a little introductory material
goes a long way. Newcomers to either will find these resources especially helpful.

### Pedestal

[Pedestal](http://pedestal.io/index#what-is-pedestal) is a collection
of libraries for building services and applications. The core
components of Pedestal
are [Interceptors](http://pedestal.io/reference/interceptors),
the [Context Map](http://pedestal.io/reference/context-map) and the
[Interceptor Chain Provider](http://pedestal.io/reference/chain-providers).

Interceptors implement functionality and the Context Map controls how
Pedestal behaves (i.e., when interceptor chain execution terminates,
going async, etc...). The Interceptor Chain Provider connects the
interceptor chain to a given platform, creates an initial context and
executes the interceptor chain against it. Each interceptor has access
to the context map during execution which means that interceptors can
_alter_ how Pedestal behaves. What about routes?

Routes are data structures that relate request paths to
interceptors. After expansion, They are consumed by a Router which is
implemented as an interceptor. When a matching route is found for a
given request, the interceptor(s) it relates to are enqueued on the
interceptor chain.

Pedestal ships with support for the Jetty, Tomcat and Immutant
platforms. Although these are all servlet-based platforms, interceptor
providers can be implemented for other platforms.

It should come as no surprise that
Pedestal [interceptors](http://pedestal.io/reference/interceptors) are
a crucial part of Vase so it is helpful to understand what they are
and how they work.

As you dive into more advanced Vase usage scenarios, you'll benefit
from a deeper understanding of Pedestal.  Here's where you should look
for more information:

- [Pedestal Docs](http://pedestal.io): The Pedestal docs site is a good launching point.
- [Pedestal Samples](http://pedestal.io/samples/index): A collection of samples demonstrating Pedestal's capabilities.
- [Pedestal Repository](https://github.com/pedestal/pedestal): For those who like to dig into the source.

### Datomic

Datomic is a database
of [facts](http://docs.datomic.com/query.html#database-of-facts) and
Vase uses it as its backend store. You will immediately be confronted
by three Datomic concepts as you work with Vase: schema, query and
transaction. Of the three, Datomic queries offer the most variety and,
possibly, confusion. Datomic uses a declarative query language called
Datomic Datalog for
queries. [Learn Datalog Today](http://www.learndatalogtoday.org/) will
help you get up to speed with it.

The Datomic docs [site](http://docs.datomic.com/index.html) has
in-depth resources
covering
[schema](http://docs.datomic.com/schema.html),
[query](http://docs.datomic.com/query.html),
[transactions](http://docs.datomic.com/transactions.html) and
more. These are good resources to dive into as you move to more
advanced Vase usage scenarios.

# Getting Started

Your path to get running depends on what you need to do:

1. Just build an API for CRUD operations: Run Vase standalone.
2. Integrate with hand-written routes: Add Vase to an existing
   Pedestal project.
3. Use Vase in a new project, for more than just CRUD: Use the template
3. Extend Vase with new actions: Create Actions.

## Prerequisites

By default, Vase uses an in-memory Datomic database, using the
[publicly available
Datomic-free](https://clojars.org/com.datomic/datomic-free) version
located in Clojars.

## Run Vase Standalone

If you just want to run an API that does CRUD (create, read, update,
delete) operations on a database, then you can run Vase
directly. Download the [latest uberjar
JAR](https://clojars.org/com.cognitect/pedestal.vase) from Clojars and
use it with `java -jar`.

```
java -jar vase-standalone.jar my-service.fern
```

This path does assume you're using Fern for your input syntax. Vase
will look for the top-level key `vase/service` in your Fern
environment.

## Use the template

If you want to do more than CRUD, you will need a project. This
repository includes a
Leiningen [template](./template) for getting started. Look at the
[template's README](./template/README.md) for local/developer setup,
otherwise

`lein new vase my-service`

Look at `my-service/src/

## Adding Vase to an Existing Pedestal project

Vase turns API descriptions into Pedestal routes. The API descriptions
specify actions for routes, these are turned into interceptors in the
Pedestal routes. This is done by `vase/routes`.

`vase/routes` takes an API base URL and an API specification. The routes it
returns can be used directly by Pedestal like this:

```clj
(require '[io.pedestal.http.route.definition.table :as table])
(require '[com.cognitect.vase]))

(defn make-master-routes
  [spec]
  (table/table-routes
   {}
   (vase/routes "/api" spec)))
```

The routes that `vase/routes` returns can also be combined with
hand-written Pedestal routes by concatenating the input to
`table/table-routes`:

```clj
(require '[io.pedestal.http.route.definition.table :as table])
(require '[com.cognitect.vase]))

(defn make-master-routes
  [spec]
  (table/table-routes
   {}
   (concat
     [["/hello" :get [hello]]]
     (vase/routes "/api" spec))))
```


## Documentation

* [Adding Vase to an existing Pedestal service](./docs/adding_vase.md)
* [Building Your First API](./docs/your_first_api.md)
* [Design document](./docs/design.md)
* [Fern Input for Vase](./docs/vase_with_fern.md) - A new way to write
  descriptors
* [Migrating EDN to Fern](./docs/migrating_edn_to_fern.md) - Tooling
  to help port your descriptors

## Contributing

Contributing guidelines for Vase are the same as
[Pedestal](https://github.com/pedestal/pedestal/blob/master/CONTRIBUTING.md).

For code contribution, you'll need a signed [Cognitect Contributor
Agreement](https://secure.echosign.com/public/hostedForm?formid=8JU33Z7A7JX84U).
For small changes and doc updates, no CA is required.

If you're proposing a significant change, please open an Issue to
discuss the design of the change before submitting a Pull Request.

## Support

Don't hesitate to reach out if you run into issues or have questions!
The Vase community can be found in the
the [pedestal-users](https://groups.google.com/d/forum/pedestal-users)
mailing list or
the [#pedestal](https://clojurians.slack.com/messages/pedestal/) slack
channel.

## Copyright

Copyright © 2015-2018 Cognitect, Inc. All rights reserved.
