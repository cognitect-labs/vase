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

* Vase descriptors are usually stored in [Extensible Data Notation](https://github.com/edn-format/edn).
* Vase descriptors are just Clojure data in memory, so the EDN format is a convenience not a requirement.
* Vase services use JSON as their data exchange format.

It also important to note that the terms *service*, *server*, *container* are all used interchangeably.

## Setting Up

First, be sure you have a Datomic transactor running. See
[the Datomic docs](http://docs.datomic.com) to get set up. Note that the
template project uses Datomic Pro. (You can change the project.clj
dependency to use Datomic Free. Be sure to make the corresponding
change in the `:datomic-uri` later.)

Create a new project from the Vase leiningen template:

```
lein new vase your-first-api
```

## Up and running

In a dedicated console/terminal window, start the service by typing
the following command:

    lein repl

If you prefer to use Boot, then run

    boot repl

Once a REPL is running, start a dev mode server:

```clojure
boot.user=> (require 'your-first-api.server)
,,, ;; Lots of logging elided for clarity.
INFO  org.eclipse.jetty.util.log - Logging initialized @75260ms to org.eclipse.jetty.util.log.Slf4jLog
nil
boot.user=> (def s (your-first-api.server/run-dev))

Creating your [DEV] server...
INFO  org.eclipse.jetty.server.Server - jetty-9.4.0.v20161208
INFO  o.e.j.server.handler.ContextHandler - Started o.e.j.s.ServletContextHandler@20f01b95{/,null,AVAILABLE}
INFO  o.e.jetty.server.AbstractConnector - Started ServerConnector@5d04c566{HTTP/1.1,[http/1.1, h2c]}{0.0.0.0:8080}
INFO  org.eclipse.jetty.server.Server - Started @85814ms
#'boot.user/s
```

## An Accounts System

Let's build a system to handle basic user accounts and items those users own.

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

## Building the data model

Data models are defined with a schema.  You may split your schema up
into logical pieces that map directly to your application domain.  In
our example, we'll define part of the schema for Items, and another
part for Users.

We can put the description into a `.edn` file that our service can
load. The description will tell Vase how a single application works
(potentially with multiple API versions.) Later, we will combine that
with some deployment configuration to pick out which applications and
which versions we want to activate, along with the Datomic URI to
operate against.

The Vase template created `resources/your-first-api_service.edn` with
a lot of examples. Locate the section with the key `:vase/norms`. The
value of `:vase/norms` is a map of schema ID to schema definition. The
schema ID is a namespaced keyword that you get to pick. (But don't use
`vase` as the namespace or you will confuse parts of your application
with parts of Vase itself.)

Thanks to the template, we have a lot of stuff inside
`:vase/norms`. Start by clearing out the current value of
`:vase/norms` and replace it with this:

```clojure
 :vase/norms
  {:accounts/item {}
   :accounts/user {}
  }
```

(This goes inside the map attached to `:descriptor`.)

This says we're building one schema named `:accounts/item` and another
named `:accounts/user`. The first component of a schema is defining
its normalized, master form.  That's done using the `:vase/norms`
entry. In this map, we'll define attributes for Items and Users.
You'll notice that the Users schema requires the Items schema, so that
we can describe a user's owned items. You'll also notice that
attributes of a given entity are defined in terms of database
transactions (*txes*) that describe them.

```clojure
 :vase/norms
 {:accounts/item
  {:vase.norm/txes [#vase/schema-tx
                     [[:item/itemId      :one :long   :unique   "The unique identifier for an item"]
                      [:item/name        :one :string           "The name of an item"]
                      [:item/description :one :string :fulltext "A short description of the item"]]]}

  :accounts/user
  {:vase.norm/requires [:accounts/item]
   :vase.norm/txes [#vase/schema-tx
                     [[:user/userId      :one  :long   :unique "The unique identifier for a user"]
                      [:user/email       :one  :string :unique "The email address for a given user"]
                      [:user/items       :many :ref            "The collection of items a user owns"]]]}}

```

Vase offers a shorthand for defining Datomic schema transactions
within `:vase.norms/txes`. This tag marks a vector of vectors.  Each
subvector defines an attribute, its cardinality, its type, an optional
qualifier (unique, index, or fulltext), and a doc string. Attribute
names are *namespaced* with the entity name to which they apply. The
optional qualifiers let you mark attributes that have `:unique`
values, that the DB should `:index`, or that allow for `:fulltext`
search.  `:fulltext` also implies `:index`.  You can also say an
entity's unique `:identity` can be determined by an attribute, which
is useful when you want to ensure an entity can be upserted
(`user_id`), but not when you want to avoiding adding a new user that
already exists (say, given their `email` - a `:unique` attribute).

Even though the short-schema version covers most of the use cases
you'll need, you're always free to use full Datomic
[schema](http://docs.datomic.com/schema.html) definitions like:

```clojure
{:db/id          #db/id[:db.part/db]
 :db/ident       :item/name
 :db/valueType   :db.type/string
 :db/cardinality :db.cardinality/one
 :db/doc         "The name of an item"
 :db.install/_attribute :db.part/db}
```

Just put that entity map in the outer vector, as a sibling to the
"shorthand" vectors.

## Making It Act

We can now begin building out an HTTP API for our data model. This
will let third-party application and other internal services make use
of our data.

Vase defines routes defined with URL strings, HTTP verbs (get, post,
etc), and action literals.  The foundation of Vase's routing is based
on [Pedestal's
capabilities](http://pedestal.io/reference/routing-quick-reference),
but with care given to represent routes in an external data file.

Here is a route that gives some
information about our API:

```clojure
 :vase/apis
 {:accounts/v1
  {:vase.api/routes
   {"/about" {:get #vase/respond {:name :accounts.v1/about-response
                                  :body "General User and Item InformatioN"}}}}}
```

Routes are defined as nested maps. Each map defines a single route,
and then a map of HTTP verbs to action literals. Here we see that *GET
/api/accounts/v1/about* will respond with a text body. Every action
literal requires a unique `:name`. Other keys and their
interpretations are defined per literal.

These are the action literals:

| Literal           | Purpose                                                |
| ------------------|--------------------------------------------------------|
| `#vase/respond`   | Respond with static data, optionally formatted as JSON |
| `#vase/redirect`  | Redirect an incoming request to a different URL        |
| `#vase/query`     | Respond with the results of a database query           |
| `#vase/transact`  | Add or update data in the database                     |
| `#vase/validate`  | Validate data against specs                            |
| `#vase/intercept` | Apply a hand-crafted, artisinal interceptor            |

An API depends on some amount of schema existing. In this example,
we've added a dependency from the `:accounts/v1` API to the User
schema. Since the User schema depends on the Item schema, both parts
of schema will be applied to our database.

```clojure
{:vase/apis
 {:accounts/v1
  {:vase.api/routes  ,,, ;; skipping the routes for space
   :vase.api/schemas [:accounts/user]}}}
```

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


Since our API is only about providing operations for user details and
accounts, we only need to depend on/declare the user schema.  Above
we're saying that version *v1* of our *accounts* API uses the
*user-schema*, and to forward the *vaserequest-id* header from every
request to every response.  This HTTP header is used to trace and
debug requests to the service (and is automatically added if it's not
sent in).

An API can depend on any number of schemas.  You should feel free to
grow and evolve your normalized schema `:norms` and add them to you
APIs `:schema` dependencies. This is one of the major benefits of
Datomic.

## Try it out

You can use curl to test the new URL.

```
curl http://localhost:8080/api/accounts/v1/about
```

If you've been following along in this guide, you got a 404 response
just now. That's because Vase has one more concept about APIs:
activation.

A single EDN file can define many APIs and many schema fragments. It
is up to a service instance to determine which of these to activate
using the top-level key `:activated-apis`. This is in the EDN file for
ease, but can be supplied separately as part of your service
config. (For example, as EC2 instance data.)

To activate the accounts API, modify the top of your EDN file like
this:

```clojure
{:activated-apis [:accounts/v1]
 ,,,
```

Now re-run curl and you'll get back a 200 status code with the body
string from our `#vase/respond` literal.


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

URL parameters bind a single value from a URL to a symbol name in your
action literal. Here's an example with a URL parameter:

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

In this trivial example we bind part of the URL to the symbol
`your-name` and return it as the body of our response.  URL parameters
are always string values.

Try it for [yourself](http://127.0.0.1:8080/api/accounts/v1/about/paul).

Let's see how we might take multiple parameters for a given route.

Query string args are typically used for filtering the result of returned data.
Here's an example were we'll return a JSON response for all of our expected query args

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

Hit [the new
url](http://127.0.0.1:8080/api/accounts/v1/aboutquery?one-thing=hello&another-thing=world),
you'll notice that the response that comes back is JSON, not text like
the other `respond` actions we specified.  When the body of a response
is not a string, it's automatically converted into JSON.

## Working with POST data

All of the POST params work the same way. Vase expects the data to
arrive as a JSON entity body. The `#vase/transact` interceptor gets
the contents of the body's `payload` parameter. That parameter will be
a sequence of maps, where each map gets passed to the `#vase/transact`
action.

Just as an example, suppose you created a JSON payload posted to your
endpoint like:

```json
{"payload":
 [{
  "a" : "Hello",
  "b" : "World"
 }]
}
```

And this request gets routed to a transaction like this:

```clojure
"/do-stuff" {:post #vase/transact {:name :example/do-stuff-to-things
                                   :properties [:a :b]}
```

When the transaction executes, it will create a new entity in Datomic
with the entity map `{:a "Hello" :b "World"}`.

(We haven't defined any attributes like `:a` and `:b` in the
norms... this is just an example.)

This is how you would use cURL to POST such a payload::

```
curl -H "Content-Type: application/json" -X POST -d '{"payload": [{"a": "Hello", "b": "World"}]}' http://localhost:8080/api/example/do-stuff
```

## A dangerous truth

So far, we've lead you to believe that action literals are *purely*
data. That's not entirely true.

During the symbol escaping process, all function that are part of Clojure's core
will correctly resolve, unless you have bound a symbol of the same name in `:params`.

This means we can add some basic functionality to our `#respond`
actions.  Let's update our url-param route to print a more interesting
string using Clojure's `(str ...)` function.

```clojure
 :vase/apis
 {:accounts/v1
  {:vase/routes
   {"/about"            {:get #vase/respond {:name   :accounts.v1/about-response
                                             :body   "General User and Item Information"}}
    "/about/:your-name" {:get #vase/respond {:name   :accounts.v1/about-yourname
                                             :params [your-name]
                                             :body (str "You said your name was: " your-name)}}
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

In addition to rendering content, the Vase system also provides a `#transact`
action allowing the storage of incomming POST data.  Observe the following addition
to the system descriptor:

```clojure
  :vase/apis
  {:accounts/v1
   {:vase.api/routes
    {"/about"            {:get #vase/respond  {:name :accounts.v1/about-response
                                               :body "General User and Item InformatioN"}}
     "/about/:your-name" {:get #vase/respond  {:name   :accounts.v1/about-yourname
                                               :params [your-name]
                                               :body (str "You said your name was: " your-name)}}

     "/aboutquery"       {:get #vase/respond  {:name   :accounts.v1/about-query
                                               :params [one-thing another-thing]
                                               :body   {:first-param  one-thing
                                                        :second-param another-thing}}}
     "/user"             {:post #vase/transact {:name :accounts.v1/user-create
                                                :properties [:db/id
                                                             :user/userId
                                                             :user/email]}}}
   :vase.api/schemas         [:accounts/user]
   :vase.api/forward-headers ["vaserequest-id"]}}
```

The added `#vase/transact` literal shown above consists of a single
element `:properties`. The `:properties` field describes the
whitelisted properties accepted in a set of incoming POST data.  The
properties `:user/userId` and `:user/userEmail` are fairly
self-explanatory, but the `:db/id` property is handled specially.
That is, the `:db/id` key signifies if the incoming data refers to
existing entities in the Vase database, or to new entities.  For
example, consider the following JSON corresponding to incoming POST
data:

```json
{"payload":
  [{
    "user/userId" : 42,
    "user/email" : "user@example.com"
  }]
}
```

The JSON packet above, because it does not contain a `:db/id` field
refers to a new entity and the return value from the Vase service will
return its newly created `:db/id`.  On the other hand, the following
JSON refers to an existing entity in the database:

```json
{"payload":
  [{
    "db/id" : 100,
    "user/userId" : 9,
    "user/email" : "user9@example.com"
  }]
}
```

Because the `:db/id` field is set to a value the Vase system will
attempt to resolve the entity in the database before transacting the
data.  Obviously, if no such entity exists then a failure will occur,
thus notifying the calling client.

One final way to refer to existing entities is to set the value at the
`:db/id` field to correspond to a unique value for the entity in
question.  For example:

```json
{"payload":
  [{
    "db/id" : ["user/userId", 9],
    "user/email" : "user9@example.com"
  }]
}
```

Because the User IDs are unique to each user, they can be used to
demarcate a unique entity in the database for the purposes of
transcting associated data. See Datomic's [lookup
refs](http://docs.datomic.com/identity.html#lookup-refs) for more
details.

## Getting data out with `query`

The `#query` action provides a way to defining service routes that
return data based on Datalog queries. Observe the following addition
to the descriptor:

```clojure
  :vase/apis
  {:accounts/v1
   {:vase.api/routes
    {"/about"            {:get #vase/respond  {:name :accounts.v1/about-response
                                               :body "General User and Item InformatioN"}}
     "/about/:your-name" {:get #vase/respond  {:name   :accounts.v1/about-yourname
                                               :params [your-name]
                                               :body (str "You said your name was: " your-name)}}

     "/aboutquery"       {:get #vase/respond  {:name   :accounts.v1/about-query
                                               :params [one-thing another-thing]
                                               :body   {:first-param  one-thing
                                                        :second-param another-thing}}}
     "/user"             {:post #vase/transact {:name :accounts-v1/user-create
                                                :properties [:db/id
                                                             :user/userId
                                                             :user/email]}
                          :get #vase/query     {:name :accounts.v1/user-page
                                                :params [email]
                                                :query [:find ?e
                                                :in $ ?email
                                                :where [?e :user/email ?email]]}}}
   :vase.api/schemas         [:accounts/user]
   :vase.api/forward-headers ["vaserequest-id"]}}
```

One query route is defined above called `/user`. This route supports
both POST and GET requests. A POST request hits the `#vase/transact`
action, while a GET runs the `#vase/query` action. The query looks up
an entity based on a query string parameter. The two main keys of
interest in the `#vase/query` action are `:params` and
`:query`. (We'll discuss an optional third property a bit later.) The
`:params` property defines the accepted keyed data names that are used
as external arguments to the query to resolve those listed parameters
to the incoming values. You can think of these as the additional
arguments to `datomic.api/q`, after the database value itself. If the
`:params` field is empty or missing, they query doesn't accept any
arguments. In that case all parameters in the URL, query string, form,
etc. will be ignored. The `:query` property contains a Datomic
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

## Querying `or` and other constants

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

## Wrapping Up

We've covered most of the action literals. The examples in this guide
created a real, if somewhat quirky, API for an accounts system.

The next step is to read the [Action Literals](./action_literals.md)
reference.
