[![CircleCI](https://circleci.com/gh/cognitect-labs/vase.svg?style=svg&circle-token=21b84b7aea75483821d3852de6c5d9930e85720a)](https://circleci.com/gh/cognitect-labs/vase)

[![Clojars Project](https://img.shields.io/clojars/v/com.cognitect/pedestal.vase.svg)](https://clojars.org/com.cognitect/pedestal.vase)

# Vase: Data-driven microservices

This system provides a data-driven and extensible way to describe and
run HTTP APIs that are backed by a durable database (Datomic).

### Vase should be considered beta technology

Vase has been used on real-world projects. Each new project teaches us
something more about what Vase can do. We're continuing to improve its
utility and usability. You may find some rough edges here and
there. When you do, raise an issue so we can make it better.

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


## Getting Started

By default, Vase uses an in-memory Datomic database, using the
[publicly available Datomic-free](https://clojars.org/com.datomic/datomic-free)
version located in Clojars.

Everything you need to get up and running with Vase is packaged in Vase itself.
Vase is completely self-contained -- You don't need to setup Datomic or create a MyDatomic account.

### Using the template

This repository also includes a Leiningen [template](./template) for
getting started.  Look at the [template's
README](./template/README.md) for local/developer setup, otherwise

`lein new vase my-service`

### Adding Vase to a Pedestal project

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
* [Fern Input for Vase](./docs/vase_with_fern.md) - A new way to write descriptors

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
