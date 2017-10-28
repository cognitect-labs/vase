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
 [com.cognitect/pedestal.vase "0.9.1"]
```

[Maven](http://maven.apache.org/) dependency information:

```xml
<dependency>
  <groupId>com.cognitect</groupId>
  <artifactId>pedestal.vase</artifactId>
  <version>0.9.1</version>
</dependency>
```


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

## Copyright

Copyright © 2015-2017 Cognitect, Inc. All rights reserved.
