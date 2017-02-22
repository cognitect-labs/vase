# Design Documentation and Vision

## Introduction

This document describes the design of the data-driven microservice
container "Vase."

It describes the service model and describes all operations,
URIs/destinations, and input/output formats.

This document elaborates on the constraints, trade-offs, formats, and
general architecture of the system.

**This is a living document, it should evolve along with the system.**

### Commonly used terms

 * **Core Service** - The main Vase container service, which hosts
   other data-described APIs
 * **API** - A service contained within the container; a hosted
   service within the core service
 * **edn** - Also EDN;
   [Extensible Data Notation](https://github.com/edn-format/edn); a
   data serialization notation, like JSON

# Vase

## Motivation

For many reasons, we are moving into a world of
microservices.  We have found that the majority of microservices
contain duplicated, mechanical code. All microservices must perform
similar functions:

 * Define HTTP routes
 * Map input bodies and parameters into transaction
 * Run queries and format results
 * Validate inputs

In fact, most such services can be described in a data format. We
wanted to take that data format from a static artifact to something
you could actually run. With Vase, we can take a data definition of
what a service should do and turn it into a running service.

## Design Goals

Our design objectives are as follows, in priority order:

   1. Radically shorten the time needed to deliver microservices.
   1. Achieve production quality
   1. To ensure the system can easily evolve and adapt

## Design Non-goals

These are the things we have decided not to work towards in Vase. That
doesn't mean we expect to prevent them. Indeed, some may "fall out" of
our implementation. However, we are not considering them when making
trade offs.

   1. Solving the general problem of mapping data into databases.
   2. Supporting arbitrary input formats.
   3. Supporting arbitrary output formats.
   4. Accommodating legacy schemata from existing applications.
   5. Databases other than Datomic.
   6. Web servers other than Pedestal.
   7. Java API

## Prototyping or Production?

Early prototypes of Vase allowed APIs to be submitted at runtime. That
is, Vase itself had an API to create APIs. We have removed that
feature for the present, for three reasons:

   1. While it is very helpful for prototyping, that approach
      conflicts with release management practices for production level
      software.
   2. We only want to commit to a limited "surface area" at this
      time. We can re-add dynamic APIs in the future, but if we
      release them now we're stuck with them forever.
   3. We did not have a good means to synchronize changes to APIs
      across multiple instances of Vase. This also reflects it's
      origin as a rapid prototyping tool.


# Design

Vase is an on-demand container service, using Pedestal and Datomic,
that allows us to write concise descriptions of data formats and API
definitions, then have a generic service bring that description to life.

Vase will be delivered as a library that can be incorporated into an
existing Pedestal service.

Vase will also deliver an application template that can create an
entire service from scratch.

## On-demand Container Service

**Vase** is an API Container. It allows us to create microservice APIs
from just a description of the routes, data model, and actions to
execute. This description is itself stored as data. The Vase runtime
should not need modification for the majority of "CRUD" services.

## Using Pedestal

Vase is an addition to Pedestal. Vase creates routes that Pedestal
then serves. The interface between Vase and Pedestal is just data.

The main service is purely additive to Pedestal. Developers can
incorporate Vase in an existing Pedestal service. Likewise, developers
can use Vase and add route written in a traditional means.

Vase will not provide every capability needed to build an
application. Its purpose is fast delivery of simple services.

Vase allows "mixing in" features via Pedestal interceptors. A Vase
service can have arbitrary developer-provided interceptors in its
routes.

## Using Datomic

Vase maps its data to Datomic. Schema for the data model is expressed
in terms of Datomic attributes. Transactions return Datomic
tx-results. Queries are written in Datomic's datalog format. Queries
will support Datomic's "pull" syntax as well as "tuple" syntax.

We have no plan to extend Vase for other databases at this time.

## Concise Description

Vase uses Clojure data structures, written in EDN and stored in files
to describe its data models, specifications, and APIs.

One Vase instance can use multiple description files.

One Vase instance can support multiple APIs.

Complete specifications for a Vase description can be found in
`src/com/cognitect/vase/spec.clj`.
`:com.cognitect.vase.spec/spec` is the root of a description file.

Description files are nested maps. At even-numbered levels of the map,
the keys are predefined by Vase. At odd-numbered levels, excluding the
first level, keys are user-provided names of APIs, schemas, and specs.

Example:

```clojure
{:activated-apis [ ,,, ]
 :datomic-uri    " ,,, "
 :descriptor
 {:vase/norms {:user.provided/name {:vase.norm/txes [ ,,, ] } }
  :vase/apis  {:user.provided/api  {:vase.api/routes { ,,, } } }
  :vase/specs { ,,, }}}
```

## Validation Using Clojure.spec

The APIs that Vase hosts are all described in data. Vase functions use
clojure.spec to ensure the integrity and correctness of API
descriptions.

## State and data management

The core service and APIs are largely stateless - request identity is
only maintained for that given request.  The core service and the
hosted APIs do not remember anything about previous requests (beyond
what was transacted into persistent/durable data).  Data is only
transacted into persistent storage if it is submitted.  Routes that
accept POST submissions are configured per API.

API-specific data that is persisted in the database is owned by that
API.  An API may only reference data that it owns.  Consumers of the
API (other services, mobile apps, etc), may choose to integrate data
from various APIs.  Unifying data across APIs is a design challenge
for API designers.  There is nothing within Vase that helps or hinders
unifying data.

## Reader Literals

The primary input to Vase is an EDN file. Clojure's reader literals
offer a concise way to extend the input format while maintaining
uniform syntax. Vase makes use of reader literals for:

 * Actions (i.e., interceptors) on routes
 * Attribute definitions in schemas

# Norms

"Norms" refer to fragments of schema that must exist for an API to
function.

## Norm Identity

The service can apply any number of norms. Each one is uniquely
identified by a namespaced keyword.

## Norm Transactions

A norm comprises:

 * `:vase.norm/txes` - A sequence of transaction data (tx-data) that
   will be transacted in Datomic at initialization time. Each tx-data
   is a vector of transactions (see below).
 * `:vase.norm/requires` - A collection of norm names that this schema
   requires. Vase ensures that all the required schemas are transacted
   before this one.

Norms are idempotent, so Vase transacts them at each startup.

The norms are captured as a map, with namespaced-keyword keys, and map
values that hold the schema transactions, or `txes`.  For example:

```clojure
{:vase/norms
 {:example-app/base-schema
  {:vase.norm/txes [[{:db/id #db/id [:db.part/db]
                      :db/ident :something/title
                      :db/valueType :db.type/string
                      :db/cardinality :db.cardinality/one
                      :db/index false
                      :db/doc "A simple title"
                      :db.install/_attribute :db.part/db}
                      ,,,]]}}
 :vase/apis
 {:example-app/v1
  {:vase.api/routes [[ ... ]]
   :vase.api/schemas [:example-app/base-schema ...]}}}
```

Each API specifies which of these schema segments it uses, captured as
a vector of keywords (the norm keys).  This ensures the data is
modeled appropriately per API version when queries run or data is
transacted.

## Schema-tx Reader Literal

Tx-data can also be described using a reader literal for shorthand.
The `#vase/schema-tx` literal takes a vector of vectors. The inner
vector is interpreted as follows:

 * The :db/ident of the attribute
 * The cardinality of the attribute, written as `:one` or `:many`
 * The type of the attribute, written as a simple keyword (e.g., for
   `:db.valueType/string`, use `:string`.)
 * An optional qualifier. One of `:unique`, `:identity`, `:index`, `:fulltext`,
   or `:component`
 * A doc string

The optional qualifiers describe attributes that contain `:unique`
values, that the DB should `:index`, or that allow `:fulltext` search.
`:fulltext` also implies `:index`. You can also say an entity's unique
`:identity` can be determined by an attribute. You may also state that
a ref-attribute is a `:component` of another entity.

The schema above using the short form would look like:

```clojure
{:vase/norms
 {:example-app/base-schema
  {:vase.norm/txes [#vase/schema-tx [[:something/title :one :string "A simple title"]]]}}

 :vase/apis
 {:example-app/api
   {:vase.api/routes [[ ... ]]
    :vase.api/schemas [:example-app/base-schema ...]}}}
```

# Specs

Specs appear under the `:vase/specs` key. The value of this key is a
map of spec name to spec.

Specs are identical to those that would be written in Clojure source code.

For example, the following might be found in code:

```clojure
(s/def :example.app.v1/age #(> % 21))
```

This would translate into the following spec in a Vase description:

```clojure
{:vase/specs
 {:example.app.v1/age (fn [age] (> age 21))}}
```

APIs can apply specs using the `#vase/validate` action.

# APIs

## API Identity

The service hosts any number of external APIs; each uniquely
identified via namespaced keyword.

## API Roots

Each API will construct routes beneath a common "root". That root is
external to the API and should not appear anywhere within the API
description or code.

## API description

APIs are defined under the `:vase/apis` key. The value of this key is
a map of API names to definitions.

API names are namespaced keywords. The API name becomes part of its
routes' URLs as follows:

 * Every '.' in the keyword is replaced with a '/'.
 * The '/' between the namespace and name is left in place.
 * Non URL characters are URL-encoded.

The definition of an API has the following top-level keys:

 * `:vase.api/routes` - A route map (see below)
 * `:vase.api/schemas` - A collection of schema names. When this API
 is activated, these schemas will be transacted into the database.
 * `:vase.api/forwarded-headers` - A collection of strings. Any
 request headers matching these strings are passed through into the
 response headers.
 * `:vase.api/interceptors` - A collection of interceptors that will
 be prepended to the action interceptors for every route.

An API describes its routes and required schema in a hashmap. See the
example below:

```clojure
{:vase/apis
 {:example.app/v1
  {:vase.api/schemas [:example/base-schema ,,,]
   :vase.api/routes  { ,,, }}}
```

In the example above, we've described a new API called,
`:example.app/v1`.  It also specifies the norms that this API
requires.

## Input and Output formats

All hosted API operations use _JSON_ as the data exchange format.

Each operation defines its own format.

## Response status codes and payload navigation

All HTTP operations return an HTTP status code. The hosted APIs use
the following HTTP status codes:

 * 200 - Success. The response body should have a value.
 * 302 - Redirect returned from an obsoleted route. The response body
   will probably be empty. A "Location" header contains the target URL.
 * 400 - The request was rejected.  There are syntactic/semantic
   errors in the input (malformed, data missing, invalid values, not
   existing ids, ...). The errors body should have a value and the
   response body normally is empty but might contain additional info
   (e.g. partial input processing, ...).
 * 404 - The requested resource is not found. The response body might
   contain a description for the "not found" error.
 * 500 - A system error. The response body might contain a description
   of the internal error/errors that occurred.

### Routing

Routing is described in a hashmap, keyed by `:vase.api/routes`, whose
value is a vector of nested route-verb pairs.  See the example below:

```clojure
{:example.app/v1
 {:vase.api/routes
  {"/home"       {:get  #vase/respond  {:name :example.app.v1/home
                                        :body "Home page"}}
   "/about"      {:get  #vase/redirect {:name :example.app.v1/about
                                        :url "http://www.google.com"}}
   "/check/:age" {:post #vase/validate {:name :example.app.v1/age-check
                                        :spec :example.app.v1/age}}}}}
```

This configuration would produce the URLs:

 * _/api/example/app/v1/home_
 * _/api/example/app/v1/about_
 * _/api/example/app/v1/check_

### Action Map

The action map describes the allowed HTTP verbs for a route and what
actions to invoke for each.  Actions are described using reader
literals.

Each route has exactly one action map. Each action map can have keys
from `#{:get :put :post :delete :head :options :any}` (these are the
standard HTTP verbs from Pedestal routes.)

The value for each verb is either a single action written as a reader
literal or a vector of actions. When the value is a vector of actions,
they will be invoked in the same order as written.

### Actions

Here are the available actions:

 * `#vase/respond` - Return a static response, optionally setting
   headers and the HTTP status code
 * `#vase/redirect` - Redirect the request with an HTTP 302 status;
   You can optionally set 303 status and additional headers
 * `#vase/validate` - Validate a POST body or query string data
 * `#vase/query` - Consume a POST body, URL parameters, or query
   string data and run a Datomic query
 * `#vase/transact` - Consume a POST body, URL parameters, or query
   string data, and transact data into the DB
 * `#vase/intercept` - Execute an interceptor written directly in the
   EDN description file.

### Action-maps

Action-maps are hashmaps that contain Action-specific data.  All action-maps
require a `:name` for the given action, a keyword.  This name is used in logging and URL
generation, and thus should be a namespaced keyword.

See documentation for the [action literals](./action_literals.md) for
the details of their keys and interpretation.

# Operational Attributes

## Failure and reliability

The core service may be scaled horizontally for availability.

APIs defined in the core service access Datomic, so their availability
is constrained to that of the underlying Datomic instance.

Neither the core service nor APIs defined in it can make outcalls to
third parties.

## Authorization and external requests

The core service has no authorization or authentication mechanisms.

Consuming applications may supply interceptors to be placed on every
Vase route. This allows an application to provide authentication and
authorization separately from the Vase API.

## Initialization

Vase services have some initialization:

   * Registering Clojure specs.
   * Transacting Schema into Datomic.

## Monitoring and logging

Vase will use Pedestal's logging facilities. No additional logging,
monitoring, or reporting mechanisms are currently in place for the
core service.


### Norms and schemas

An API uses a top-level key, `:vase/norms` to specify all
acceptable/avaible API schema datoms.  These are called *norms*
because they're transacted with Datomic in an idempotent manner.
