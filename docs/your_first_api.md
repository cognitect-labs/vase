# Building your first API

## Welcome

This is a guide to get you started with Vase - the data-driven
microservice container. It will help you write your first descriptor
file.

## What You Will Learn

After reading this guide, you will be able to:

- Write a Vase API descriptor
- Exchange data with Vase services

## Getting Started

It may help to understand the [design](./design.md) of the system and
the file formats used before you begin. The most important pieces:

* Vase descriptors are usually written in [Fern](https://github.com/cognitect-labs/fern).
* Vase descriptors are just Clojure data in memory. The namespace
  `com.cognitect.vase.api` has clojure.spec definitions of the inputs
  needed.
* Vase services use JSON as their data exchange format.

It also important to note that the terms *service*, *server*,
*container* are all used interchangeably.

## Setting Up

Note that the template project uses Datomic Free and therefore offers
only an in-memory store. Later, you can change to a persistent store.
You'll have get a Datomic license (free versions are available),
launch a Datomic transactor, make minor changes to your project.clj
or build.boot, and update the `:datomic-uri` value in your EDN
descriptor file.  This is explained below, as well as
in [the Datomic docs](http://docs.datomic.com).

Create a new project from the Vase leiningen template:

```
lein new vase your-first-api
```

## Up and running

The exact sequence varies a little bit depending on whether you prefer
Leiningen or Boot.

### With Leiningen

Start the service by running the following command at a terminal:

    lein repl

Once the REPL is running, start a dev mode server like this:

```clojure
(def srv (run-dev))
```

That means your server is up and running, listening on port 8080.

### With Boot

Start the service by running the following command at a terminal:

    boot repl

Once the REPL is running, start a dev mode server like this:

```clojure
(require 'your-first-api.server)
(in-ns 'your-first-api.server)
(def srv (your-first-api.server/run-dev))
```

### Either way

There will be a lot of logging, but it should end with something like
this:

```
INFO  o.e.jetty.server.AbstractConnector - Started ServerConnector@5d04c566{HTTP/1.1,[http/1.1, h2c]}{0.0.0.0:8080}
INFO  org.eclipse.jetty.server.Server - Started @85814ms
```

That means your service is up and running on port 8080.

## A Personal Inventory System

Let's build a system to handle basic user accounts and can track items
they own.

With this simple goal statement, we can already envision some pieces
of the data model and even some URLs that might appear in the API.

Our data model needs to handle:

 * Items (with item IDs, names, and descriptions)
 * Users (with user IDs, emails, and a list of items they own)

And we might have URLs that:

 * Fetch all items in the system
 * Fetch all users in the system
 * Fetch a user given their user ID
 * Fetch a user's owned-item list given their user ID
 * etc.

We put the description into a [`.fern`](https://github.com/cognitect-labs/fern) file that our service loads. This description
tells Vase what API a service offers, what schema it defines, and what
specifications the data must follow.

The Vase template created `resources/your-first-api_service.edn` with
just the minimum you need to get started. Excluding the commented-out
portions, it should look like this:

```clojure
{vase/service  (fern/lit vase/service
                         {:apis []
                          :service-map @http-options})
 http-options  {:io.pedestal.http/port 8080}}
```

This defines a Vase service, but not a very exciting one. It has no
APIs, no schema, and no specs. It gets better in a bit.

On the left, we have `vase/service` as a symbol. That is the only
"magic symbol" on the left. Vase knows to look for this special symbol
when it starts up. On the right, we have a [Fern
literal](https://github.com/cognitect-labs/fern#usage). Vase defines a
bunch of Fern literals. This literal is also called `vase/service` and
it means Fern should create a service record based on the contents of the map.

A `vase/service` record can have the following keys:

| Key               | Type    | Meaning |
|-------------------|---------|---------|
| `:apis`          | Vector of APIs | Routes for these APIs will be created. Schema used by these APIs will be applied. |
| `:service-map`  | Map | These are HTTP parameters for starting a Pedestal service |

The `:apis` key needs a vector of actual APIs. But defining them
directly in the right-hand side of the file would get awkward and hard
to read. Fern lets us use references to locate the APIs elsewhere in
the file:

```
{vase/service  (fern/lit vase/service {})}
```

## Building the data model

We often start with a model of the entities a service must manipulate.

Data models are defined with a schema. You may split your schema up
into logical pieces that map directly to your application domain. In
our example, we'll define one part of the schema for Items, and
another part for Users.

We can define a schema as a symbol whose right-hand side is a
collection of attributes.  The schema name is any symbol you
choose. We recommend using a namespaces for clarity. It's best to
avoid using `vase` as part of your namespace. It's not a reserved
name, but you may confuse parts of your application with parts that
Vase requires.

To start with, let's make space for our two schemas:

```clojure
 accounts/item   (fern/lit vase.datomic/attributes)
 accounts/user   (fern/lit vase.datomic/attributes)
```

This says we're building one schema named `accounts/item` and another
named `accounts/user`. We'll define attributes for Items and Users
inside the Fern literals on the right-hand side.

```clojure
 accounts/item   (fern/lit vase.datomic/attributes
                           [:item/itemId      :one :long   :identity "The unique identifier for an item"]
                           [:item/name        :one :string           "The name of an item"]
                           [:item/description :one :string           "A short description of the item"]])

 accounts/user   (fern/lit vase.datomic/attributes
                           [:user/userId      :one  :long   :identity "The unique identifier for a user"]
                           [:user/email       :one  :string :unique   "The email address for a given user"]
                           [:user/items       :many :ref              "The collection of items a user owns"]])
```

The `vase.datomic/attributes` literal offers a shorthand notation for
attributes. It takes an arbitrary number of vectors, where each vector
defines an attribute. Each vector defines an attribute
name, its cardinality (`:one` or `:many`), its type, some optional
flags, and a doc string.

The allowed flags are translated into parts of the attribute
definition for Datomic:

| Flag | Meaning | Equates to |
|----------------|---------|------------|
| `:unique`      | Only one entity in the DB can have this value for this attribute  | `:db/unique :db.unique/value`    |
| `:identity`    | An entity is uniquely identified by this value. Upsert is enabled | `:db/unique :db.unique/identity` |
| `:index`       | This attribute should be indexed                                  | `:db/index true`                  |
| `:fulltext`    | This attribute should be searchable as text. (Mildly deprecated.) | `:db/fulltext true`               |
| `:component`   | The entity referenced by this attribute should be retracted when this one is. (Cascading delete) | `:db/isComponent true` |
| `:no-history`  | Do not preserve old values of this attribute                      | `:db/noHistory true`             |

Even though the short-schema version covers most of the use cases
you'll need, you're always free to use full Datomic
[schema](http://docs.datomic.com/schema.html) definitions like:

```clojure
 accounts/item   (fern/lit vase.datomic/tx
                           {:db/id          #db/id[:db.part/db]
                            :db/ident       :item/name
                            :db/valueType   :db.type/string
                            :db/cardinality :db.cardinality/one
                            :db/doc         "The name of an item"}]]
```

Notice that this uses the `vase.datomic/tx` literal, whose body is an
arbitrary number of Datomic transaction elements (either entity maps
like this one or datoms.)

Fill in the schema parts like this:

```clojure
 :vase/norms
 {:accounts/item
  {:vase.norm/txes [#vase/schema-tx
                     [[:item/itemId      :one :long   :identity "The unique identifier for an item"]
                      [:item/name        :one :string           "The name of an item"]
                      [:item/description :one :string           "A short description of the item"]]]}

  :accounts/user
  {:vase.norm/requires [:accounts/item]
   :vase.norm/txes [#vase/schema-tx
                     [[:user/userId      :one  :long   :identity "The unique identifier for a user"]
                      [:user/email       :one  :string :unique   "The email address for a given user"]
                      [:user/items       :many :ref              "The collection of items a user owns"]]]}}
```

This says the `:accounts/item` schema has three attributes:
`:item/itemId`, `:item/name`, and `:item/description`. The
`:accounts/user` schema has three more attributes: `:user/userId`,
`:user/email`, and `:user/items`. Furthermore, the `:accounts/user`
schema requires that the `:accounts/item` schema must be in place
first. (It doesn't really... there are no definitions in
`:accounts/user` that depend on `:accounts/item`. This is just for
illustration in this tutorial.)

## Making It Act

With the data model in place, we can build our API.

Vase defines routes defined with URL strings, HTTP verbs (get, post,
etc), and action literals.  The foundation of Vase's routing is based
on [Pedestal's
capabilities](http://pedestal.io/reference/routing-quick-reference),
but with care given to represent routes in an external data file.

Fill in `:vase/apis` with this route that gives some information about our API:

```clojure
 :vase/apis
 {:accounts/v1
  {:vase.api/routes
   {"/about" {:get #vase/respond {:name :accounts.v1/about-response
                                  :body "General User and Item Information"}}}}}
```

Routes are defined as nested maps. Each map defines a single route,
and then a map of HTTP verbs to action literals---in this case
`#vase/respond`. Every action literal requires a unique `:name`. Other
keys and their interpretations are defined per literal.  Here we see
that `GET /api/accounts/v1/about` will respond with a string.

These are the action literals:

| Literal           | Purpose                                                |
| ------------------|--------------------------------------------------------|
| `#vase/respond`   | Respond with static data, optionally formatted as JSON |
| `#vase/redirect`  | Redirect an incoming request to a different URL        |
| `#vase/query`     | Respond with the results of a database query           |
| `#vase/transact`  | Add or update data in the database                     |
| `#vase/validate`  | Validate data against specs                            |
| `#vase/intercept` | Apply a hand-crafted, artisanal interceptor            |

An API usually depends on some amount of schema. In this example,
we've added a dependency from the `:accounts/v1` API to the User
schema. Since the User schema depends on the Item schema, both parts
of schema will be applied to our database.

```clojure
{:vase/apis
 {:accounts/v1
  {:vase.api/routes  ,,, ;; skipping the routes for space
   :vase.api/schemas [:accounts/user]}}}
```

An API can depend on any number of schemas.  You should feel free to
grow and evolve your schema by adding new "norms."

One note: Vase tracks which schemas it has already applied to a
database. Think of each schema like a migration in other database
frameworks: once it's applied to the database you don't change
it. Just add new schemas under `:vase/schemas` and add them to your
APIs' `:vase.api/schemas` dependencies.

## Activating the API

You can use curl to test the new URL, but it won't work yet.

```
curl http://localhost:8080/api/accounts/v1/about
```

If you've been following along in this guide, you got a 404 response
just now. That's because Vase has one more concept about APIs:
activation.

A single EDN file can define many APIs and many schema fragments. It
is up to a service instance to determine which of these to activate
using the top-level key `:activated-apis`. This is in the EDN file for
ease, but could be supplied separately as part of your service
config. (For example, as EC2 instance data.)

To activate the accounts API, modify the top of your EDN file like
this:

```clojure
{:activated-apis [:accounts/v1]
 ,,,
```

Now re-run curl and you'll get back a 200 status code with the body
string from our `#vase/respond` literal.

## Forwarding Headers

Some Javascript and Clojurescript clients provide request IDs that
they use to correlate requests and responses. We can tell Vase to
forward headers from the request through to the client. This is at the
level of the whole API:

```clojure
{:vase/apis
 {:accounts/v1
  {:vase.api/routes          ,,, ;; skipping the routes for space
   :vase.api/schemas         [:accounts/user]
   :vase.api/forward-headers ["vaserequest-id"]}}}
```

This says to forward the `vaserequest-id` header from every
request to every response. This HTTP header is used to trace and
debug requests to the service (and is automatically added if it's not
sent in).

## Handling Parameters

Vase does some code generation to make HTTP parameters available as
Clojure bindings for a route's action literal(s). These are conveyed
from the route to Vase by Pedestal, using the `:params` keyword in the
[request map](http://pedestal.io/reference/request-map).

Params arrive in various forms, extracted from:

- EDN payload for POSTs
- JSON payload for POSTs
- Form data for POSTs
- Query string arguments
- URL (i.e., path) parameters.

Parameters are resolved with an order of precedence, as listed below:

 * EDN POST payloads override
 * JSON POST payloads override
 * POST form data payloads override
 * Query string args override
 * URL parameters

See [Pedestal's docs](http://pedestal.io/reference/parameters) for a
complete reference on parameters.

Let's look at them from the bottom up.

### Path Parameters

A path parameter binds a single value from a URL to a symbol name in your
action literal.

Add a route with a path parameter:

```clojure
 :vase/apis
 {:accounts/v1
  {:vase/routes
   {"/about"            {:get #vase/respond {:name   :accounts.v1/about-response
                                             :body   "General User and Item Information"}}
    "/about/:your-name" {:get #vase/respond {:name   :accounts.v1/about-yourname
                                             :params [your-name]
                                             :body   your-name}}}
   :vase.api/schemas         [:accounts/user]
   :vase.api/forward-headers ["vaserequest-id"]}}
```

In this trivial example we bind part of the URL path to the symbol
`your-name` and return it as the body of our response. A path
parameter value is always a string.

Try it out with curl:

    curl http://127.0.0.1:8080/api/accounts/v1/about/paul

Let's see how we might take multiple parameters for a given route.

### Query Parameters

Query string args are typically used for filtering the result of
returned data.  Here's an example were we'll return a JSON response
for all of our expected query args

```clojure
 :vase/apis
 {:accounts/v1
  {:vase/routes
   {"/about"            {:get #vase/respond {:name   :accounts.v1/about-response
                                             :body   "General User and Item Information"}}
    "/about/:your-name" {:get #vase/respond {:name   :accounts.v1/about-yourname
                                             :params [your-name]
                                             :body   your-name}}
    "/aboutquery"       {:get #vase/respond {:name   :accounts.v1/about-query
                                             :params [one-thing another-thing]
                                             :body   {:first-param  one-thing
                                                      :second-param another-thing}}}}
   :vase.api/schemas         [:accounts/user]
   :vase.api/forward-headers ["vaserequest-id"]}}
```

With curl:

    curl 'http://127.0.0.1:8080/api/accounts/v1/aboutquery?one-thing=hello&another-thing=world'

(Make sure you quote the whole string properly... the '?' and '&' mean
something entirely different to the shell.)

Notice that the response that comes back is JSON, not text like the
other `#vase/respond` action we specified. That's because the new action
returned a Clojure data structure instead of a string. When the body
of a response is not a string, Vase converts it to JSON.

### Parameter Defaults

You can provide a default value for any `:params` binding.
For example, `:params [your-name [age 42]]`. Of course, if a path
parameter is nil, then the route didn't even match. But query and body
parameters can be defaulted this way.

## A Dangerous Truth

So far, we've lead you to believe that action literals are _purely_
data. That's not entirely true.

Many parts of the action literals are evaluated as code, in an
environment where the parameter names are bound. Everything from
`clojure.core` is available. That is, unless you shadow something from
`clojure.core` with a parameter name!

This means we can add a function call directly to our `#vase/respond`
actions.  Let's update our url-param route to print a more interesting
string using `clojure.core/str`.

Modify the action for "/about/:your-name" like this:

```clojure
 :vase/apis
 {:accounts/v1
  {:vase.api/routes
   {"/about"            {:get #vase/respond {:name   :accounts.v1/about-response
                                             :body   "General User and Item Information"}}
    "/about/:your-name" {:get #vase/respond {:name   :accounts.v1/about-yourname
                                             :params [your-name]
                                             :body   (str "You said your name was: " your-name)}}
    "/aboutquery"       {:get #vase/respond {:name   :accounts.v1/about-query
                                             :params [one-thing another-thing]
                                             :body   {:first-param  one-thing
                                                      :second-param another-thing}}}}
   :vase.api/schemas         [:accounts/user]
   :vase.api/forward-headers ["vaserequest-id"]}}
```

With great power comes great responsibility - bending this ability is
dangerous and can cause the container to crash, but in a pinch it can
allow you to shape an API to meet your needs.

Obviously, this means anyone who can alter your descriptor can run
arbitrary code in your server. Don't accept user inputs as
descriptors!

## Getting data in with `transact`

In addition to rendering content, the Vase system also provides a
`#vase/transact` action allowing the storage of incoming POST data.

Vase expects transaction data to arrive as a JSON entity body. The top
level of the body is an object with the single key `payload`. The
payload should be a collection of entity bodies (i.e., maps) to transact.

Add the "/user" route shown here to your descriptor:

```clojure
  :vase/apis
  {:accounts/v1
   {:vase.api/routes
     {"/about"            {:get #vase/respond    {:name       :accounts.v1/about-response
                                                  :body       "General User and Item Information"}}
      "/about/:your-name" {:get #vase/respond    {:name       :accounts.v1/about-yourname
                                                  :params     [your-name]
                                                  :body       (str "You said your name was: " your-name)}}
      "/aboutquery"       {:get #vase/respond    {:name       :accounts.v1/about-query
                                                  :params     [one-thing another-thing]
                                                  :body       {:first-param  one-thing
                                                              :second-param another-thing}}}
       "/user"             {:post #vase/transact {:name       :accounts.v1/user-create
                                                  :properties [:db/id
                                                               :user/userId
                                                               :user/email]}}}
    :vase.api/schemas         [:accounts/user]
    :vase.api/forward-headers ["vaserequest-id"]}}
```

The new route has a single `#vase/transact` literal with a name and
properties. The `:properties` key holds a whitelist of attribute names
that Vase will accept in the incoming POST data. In this case, the
payload should have a sequence of maps that each have `:user/userId`
and `:user/email` keys and values. These will be asserted in Datomic.

### Try a Transaction

We want to POST the following JSON

```json
{"payload":
  [{
    "user/userId" : 42,
    "user/email" : "user@example.com"
  }]
}
```

This is how you would use cURL to POST such a payload:

```
curl -H "Content-Type: application/json" -X POST -d '{"payload": [{"user/userId": 42, "user/email": "user@example.com"}]}' http://localhost:8080/api/accounts/v1/user
```

The quoting is a little different on Windows:

```
curl -H "Content-Type: application/json" -X POST -d "{\"payload\":[{\"user/userId\":42,\"user/email\":\"user@example.com\"}]}" http://localhost:8080/api/accounts/v1/user
```

Give it a try!

### Insert, Assert, Upsert

The properties `:user/userId` and `:user/userEmail` are fairly
self-explanatory, but the `:db/id` property is handled specially.
That is, the `:db/id` key signifies if the incoming data refers to
existing entities in the Vase database, or to new entities.

In the JSON packet above, the entity did not contain a `:db/id`
field. Vase treats it as a new entity and attaches a tempid before
asserting it.

At this point, one of three things will happen:

1. The entity has an `:identity` attribute whose value matches an
   existing entity in the database. This becomes an "upsert" on that
   entity: the new attribute values are _merged_ with the existing
   entity.
2. The entity has a `:unique` value that already exists in the
   database. Because it has a tempid, Datomic will reject the
   transaction and Vase will return an error to the client.
3. Neither of the above are true, and Datomic will create a new
   entity.

It is possible to supply a db/id directly, like this:

```json
{"payload":
  [{
    "db/id" : 100,
    "user/userId" : 9,
    "user/email" : "user9@example.com"
  }]
}
```

Because the `:db/id` field is set to a value, Vase will try to
resolve the entity in the database before transacting the
data. Obviously, if no such entity exists then a failure will occur,
thus notifying the calling client.

One final way to refer to existing entities is to set the value at the
`:db/id` field to correspond to a unique value for the entity in
question. For example:

```json
{"payload":
  [{
    "db/id" : ["user/userId", 9],
    "user/email" : "user9@example.com"
  }]
}
```

We declared `:user/userId` as an `:identity` attribute. That means we
can use it in lookup refs as well as doing upsert with it.

See Datomic's
[lookup refs](http://docs.datomic.com/identity.html#lookup-refs) for
more details.

## Getting data out with `query`

The `#vase/query` action provides a way to define service routes that
return data based on Datalog queries.

Go ahead and add a `:get` action to the "/user" route like this:

```clojure
  :vase/apis
  {:accounts/v1
   {:vase.api/routes
     {"/about"            {:get #vase/respond    {:name       :accounts.v1/about-response
                                                  :body       "General User and Item Information"}}
      "/about/:your-name" {:get #vase/respond    {:name       :accounts.v1/about-yourname
                                                  :params     [your-name]
                                                  :body       (str "You said your name was: " your-name)}}
      "/aboutquery"       {:get #vase/respond    {:name       :accounts.v1/about-query
                                                  :params     [one-thing another-thing]
                                                  :body       {:first-param  one-thing
                                                              :second-param another-thing}}}
       "/user"             {:post #vase/transact {:name       :accounts.v1/user-create
                                                  :properties [:db/id
                                                               :user/userId
                                                               :user/email]}
                            :get #vase/query     {:name       :accounts.v1/user-page
                                                  :params     [email]
                                                  :query      [:find ?e
                                                               :in $ ?email
                                                               :where [?e :user/email ?email]]}}}
    :vase.api/schemas         [:accounts/user]
    :vase.api/forward-headers ["vaserequest-id"]}}
```

The "/user" route now supports both POST and GET requests.  The POST
request hits the `#vase/transact` the same as before. Now a GET runs
the `#vase/query` action. The query looks up an entity based on a
query string parameter. The two main keys of interest in the
`#vase/query` action are `:params` and `:query`. (We'll discuss an
optional third property a bit later.)

The `:params` property defines
the accepted keyed data names that are used as external arguments to
the query to resolve those listed parameters to the incoming
values. The `#vase/query` action passes these as extra arguments to
`datomic.api/q`, following the database value itself.

If the `:params` field is empty or missing, they query doesn't accept
any arguments. In that case all parameters in the URL, query string,
form, etc. will be ignored.

The `:query` property contains a Datomic
[datalog query](http://docs.datomic.com/query.html).

One limitation of providing query parameters as URL arguments or path
parameters is that only string types are shuttled across to the
server. Often it's useful to refer to arguments that are other useful
types, numbers are a common case. Here's a new route to illustrate
that conversion:

```clojure
     "/user/:id" {:get #vase/query {:name :accounts-v1/user-id-page
                                    :params [id]
                                    :edn-coerce [id]
                                    :query
                                    [:find ?e
                                     :in $ ?id
                                     :where [?e :user/userId ?id]]}}
```

(We're going to stop repeating all the routes in the interest of
saving space and helping focus on the new stuff. It should be clear by
now where this goes in the EDN file.)

The new route `/user/:id` allows the a similar information lookup, but
with the argument as a path parameter
(e.g. http://example.com/user/id). By default the `:id` parameter
would be a string value, but by using the `:edn-coerce` property of
the `#query` action we tell Vase to attempt to parse the string as a
valid EDN data type.  Therefore, when clients hit the URL bound to
that query the proper types will match (i.e. the DB expects integer
IDs, not string IDs).

### Querying `or` and other constants

It's often useful to model a query that match against anything within
a given data set, for example, "Give me all users whose email is in
`["jane@domain.com", "bill@domain.com"]`"

In Datomic this is called a "parameterized in/or query," and it's achieved by
binding the names with the `:in` clause.  To do this, we supply additional
constant data to the Vase query with the `:constants` option.
This can also be used with parameter binding (covered above), in which case parameters
are passed in to the query before the constants.

An example of simple constants follows - observe our last change to the schema below:

```clojure
     "/special-users" {:get #vase/query {:name :accounts.v1/special-users
                                         :params []
                                         :constants [["jane@domain.com" "bill@domain.com"]]
                                         :query
                                         [:find ?e
                                          :in $ [?email ...]
                                          :where
                                          [?e :user/email ?email]]}}
```

## Be Persistent

So far, we've used an in-memory URI for Datomic. That means just what it sounds like:
values are only stored in memory. To make it persistent, you need to pick
a [storage engine](http://docs.datomic.com/storage.html#storage-services) and update the
`:datomic-uri` value. For your initial efforts,
Datomic's [dev storage protocol](http://docs.datomic.com/dev-setup.html) may suffice.

By default, Vase uses the free version of Datomic. In order to configure dev or
other persistent stores, you will need to first
[obtain a Datomic Starter or Datomic Pro license](http://www.datomic.com/get-datomic.html)
and install the software on your machine.

You'll also need to reference datomic-pro in your dependencies.

You will then need to change your project's dependencies to reference the
correct version of Datomic (in the project's `project.clj` or `build.boot`
file). This is slightly subtle because the template actually did not add any
direct reference to Datomic in your project. Instead, it included a dependency
on Vase which, in turn, depends on datamic-free.

So, you will need to add an explicit dependency on datamic-pro, and neutralize Vase's
inclusion of datomic-free:

Look for the existing Vase dependency, e.g.,
```
[com.cognitect/pedestal.vase "0.9.1-SNAPSHOT"]
```

and change it to

```
[com.datomic/datomic-pro "0.9.NNNN" :exclusions [[com.fasterxml.jackson.core/jackson-core]
                                                 [com.fasterxml.jackson.core/jackson-databind]
                                                 [joda-time]]]

[com.cognitect/pedestal.vase "0.9.1-SNAPSHOT" :exclusions [com.datomic/datomic-free]]
```

where `NNNN` is the version of Datomic you've installed.

You can see an example of this in the samples at
[../samples/petstore-full/project.clj-with-datomic-pro](../samples/petstore-full/project.clj-with-datomic-pro).

## Wrapping Up

We've covered most of the action literals. The examples in this guide
created a real, if somewhat quirky, API for an accounts system.

The next step is to read the [Action Literals](./action_literals.md)
reference.
