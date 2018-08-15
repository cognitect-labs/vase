# Rationale

[Fern](https://github.com/cognitect-labs/fern) is an alternative way
to write Vase descriptors. It does basically the same things as EDN,
but with a couple of changes to make it easier for humans to write the
descriptors.

First, it "flattens out" the deeply nested structure from the EDN
descriptors. Instead of nesting, Fern lets you incorporate data by
reference. That lets you remove some duplication, but more importantly
it means you don't have to keep track of where you are in the
descriptor nearly as much.

Second, the EDN files have a mix of "magic" keys (`:vase/norms`,
`:vase/apis`, `:vase/specs`) and user-defined keys (for API names,
Spec names, etc.) Fern changes that model quite a bit. There's only
one reserved key name: `vase/service`. After that you get to define
all the keys.

# Sample

So what does a Fern version of a Vase descriptor look like? Here's a
"Hello, World" sample:

```
{vase/service            (fern/lit vase/service
                                   {:apis        [@hello]
                                    :service-map @http-options})

 http-options            {:io.pedestal.http/port 8080}

 hello                   (fern/lit vase/api
                                   {:path   "/example/v1"
                                    :routes @hello-routes})

 hello-routes            #{["/hello" :get @hello-response]}

 hello-response          (fern/lit vase/respond
                                   {:body "Hello world"})
}
```

Vase uses this by "pulling on" the `vase/service` key. You can
[read more](https://github.com/cognitect-labs/fern/#usage) about how
Fern evaluation works. The short version is that `fern/lit` is kind of
like a Clojure reader literal, but Fern decides when to dereference
symbols so we can enhance the environment and produce nicer errors
when something goes wrong.

The `vase/service` literal has one argument, which is a map with
`:apis` and `:service-map`. See how the `:apis` key has `[@hello]` as
a value? When Fern evaluates that literal, it treats `@hello` as a
reference to the value of `hello` at the top level. So Fern follows
that reference to evaluate the `vase/api` literal. Evaluation follows
through to `@hello-routes` and eventually reaches the `vase/respond`
literal.

If you take a closer look at `http-options` and `hello-routes`, you
might notice that these are just ordinary Clojure data
structures. That's one of the nice things about Fern.

# Fern lits

Fern lets us define new literals by adding `defmethod`s to any
namespace. Vase provides a set of Fern literals out of the box. All
the normal Vase reader literals have equivalent Fern forms. But Vase
also adds some new ones like the `vase/service` in the "Hello World"
sample.

| Literal | Purpose |
|---------|---------|
| vase/plugins | Namespaces to load before evaluation. |
| vase/service | Define a service. |
| vase/api | Define an API. |
| vase/respond | Define an interceptor with the respond action. |
| vase/redirect | Define an interceptor with the redirect action. |
| vase/validate | Define an interceptor with the validate action. |
| vase/conform  | Define an interceptor with the conform action. |
| vase.datomic/connection | Connect to a Datomic database _and_ act as an interceptor to attach the connection to a request. |
| vase.datomic/query | Define an interceptor with the Datomic query action. |
| vase.datomic/transact | Define an interceptor with the Datomic transaction action. |
| vase.datomic/attributes | Define an interceptor that creates Datomic schema from short vector form. |
| vase.datomic/tx | Define an interceptor that executes an arbitrary Datomic transaction. |

# `:on-request` and `:on-startup`

With the EDN format, Vase forces certain things to happen on your
behalf. When it starts up, it looks for schema definitions and loads
those into Datomic. It also puts an interceptor into every route that
will attach a Datomic connection to the [request map](http://pedestal.io/reference/request-map).

When you use Fern, that magic is under your control. A `vase/api` has
two keys that let you define what it needs to do at startup time and
what needs to happen on every request. These are cleverly called
`:on-startup` and `:on-request`. The value for each of them should be
a vector of interceptors.

Here is an example of an API that uses Datomic:

```
 example/v1              (fern/lit vase/api
                                   {:on-startup    [@connection @base-attributes @user-attributes @loan-attributes @sample-users]
                                    :on-request    [@connection]
                                    :routes        @v1/routes})

 connection              (fern/lit vase.datomic/connection @datomic-uri)
 datomic-uri             "datomic:mem://example"
```

You can find the whole file in the source repo under `test/resources/test_descriptor.fern`

Notice that `:on-startup` has a reference to `connection`, which is a
`vase.datomic/connection`. At startup time, that literal serves to
connect to (and optionally create) a database.

On request, the connection attaches the connection and current
database value to the request, for use by later interceptors. All the
`:on-request` interceptors get run before Vase dispatches to a route.

# Datomic schema

Notice how the `:on-startup` from our last example vector has some
other references in it?  Those are how we transact schema. Instead of
using `:vase/norms` as a top-level key, we use
interceptors. (Interceptors everywhere!) A couple of these look like:

```
 base-attributes         (fern/lit vase.datomic/tx
                                   {:db/ident       :company/name
                                    :db/unique      :db.unique/value
                                    :db/valueType   :db.type/string
                                    :db/cardinality :db.cardinality/one})

 user-attributes         (fern/lit vase.datomic/attributes
                                   [:user/userId    :one :long   :identity "A Users unique identifier"]
                                   [:user/userEmail :one :string :unique   "The users email"]
                                   [:user/userBio   :one :string :fulltext "A short blurb about the user"])
```

There are two different things going on here. `vase.datomic/tx` says
"transact this data." It's arguments are exactly the kind of entities
and datoms that you can read about in the
[Datomic docs](http://docs.datomic.com/transactions.html).

The second literal is `vase.datomic/attributes`. It uses a kind of
shorthand notation for building schema. This is the same vector format
that the old `#vase/schema-tx` reader literal used. The format is this:

| Field | Allowed values | Maps to |
|-------|----------------|---------|
| Attribute name | Any keyword | :db/ident |
| Cardinality | :one or :many | :db/cardinality |
| Value type | [Datomic value types](http://docs.datomic.com/schema.html#required-schema-attributes) | :db/valueType |
| Flags | Any of :index, :identity, :unique, :component, :fulltext, :no-history | :db/index true, :db.unique/identity, :db.unique/value, :db/isComponent true, :db/fulltext true, :db/no-history true |
| Docstring | String | :db/doc |

Multiple flags are allowed. Add as many as you like before the
mandatory docstring.

# Adding Your Own Literals

One of our goals with the Fern format was to make Vase easier to
extend. Fern calls these
[plugins](https://github.com/cognitect-labs/fern/#plugins). Any
namespaces in the `vase/plugins` key get loaded before Vase evaluates
the rest of the environment. If those namespaces define new
multimethods for
[`fern/literal`](https://github.com/cognitect-labs/fern/blob/master/src/fern.clj),
then you can freely mix those with Vase's pre-defined literals.

Suppose you want to aggregate data from an existing API into a Vase
app. There are just two steps to make it happen.

## Provide a Multimethod for fern/literal

Your literal can return anything. But if you want to use it in an
`:on-startup`, `:on-request`, or route chain, then it should return an
interceptor.

```
(ns example
  (:require [fern :as f]))

(defmethod f/literal 'example/api-call
  [_ target-uri]
  ;; return an interceptor)
```

The first argument to the multimethod will just be the symbol you
define. Since we've already dispatched on that symbol, it's not very
interesting and we ignore it in the function body.

The remaining arguments are whatever you want them to be. A lot of our
examples above use maps. Most of the built in literals that Vase
provides take maps. But Fern does allow you to use any arguments you
like.

## Add the Namespace to `vase/plugins`

Your defmethod has to get evaluated before you create the
service. Vase can do this for you if you add the namespace name to the
`vase/plugins` key like this:

```
{vase/plugins [example]
 vase/service (fern/lit vase/service ,,,)
}
```

# Using Fern version of Vase

One way to use Fern descriptors is to use the new Vase `-main`. Just
give Vase a path to your descriptor file when you start Clojure. This
is most useful when you don't want to create a whole project
structure. Just use vase.jar directly.

The other way to use it when you embed Vase in your project. In that
case, the new API functions are
`com.cognitect.vase.fern/load-from-file` and
`com.cognitect.vase.fern/prepare-service`. You can use them like this:

```
      (when-let [prepared-service-map (try
                                        (-> filename
                                            (load-from-file)
                                            (prepare-service))
                                        (catch Throwable t
                                          (fe/print-evaluation-exception t)
                                          nil))]
        (try
          (a/start-service prepared-service-map)
          (catch Throwable t
            (fe/print-other-exception t filename))))
```

The nesting gets a little weird because we need to catch exceptions at
two different times. If an exception happens while evaluating the
`vase/service`, then we can use Fern's `print-evaluation-exception`
function, which prints a kind of trace of the chain of values leading
up to the error. On the other hand, if an exception happens while
starting the service, we want to call `print-other-exception` which
produces nicely readable output of ordinary exceptions.

There's a macro called `try->` that makes this look a little nicer:

```
    (try->
     filename
     load-from-file
     (:! java.io.IOException ioe (fe/print-other-exception ioe filename))

     prepare-service
     (:! Throwable t (fe/print-evaluation-exception t))

     api/start-service
     (:! Throwable t (fe/print-other-exception t filename)))
```

It's purely syntactic sugar.

Of course, if you're already happy with your error handling, then just let the functions throw:

```
    (->
     filename
     load-from-file
     prepare-service
     api/start-service)
```
