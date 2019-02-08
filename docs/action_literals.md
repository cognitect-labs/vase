# Action Literals

This page describes the action literals and their parameters. For the
full scoop, you should definitely look at
src/com/cognitect/vase/literals.clj.

All the literals can be chained together in a single route, _except_
for `#vase/respond` and `#vase/redirect`, which end a chain. To chain literals, put them in a vector.


## #vase/respond

Immediately send an HTTP response. This cannot be chained together
with any other literals.

| Param       | Meaning                                                   |
|-------------|-----------------------------------------------------------|
| :name       | Name of the interceptor. Required.                        |
| :params     | Symbols to bind from `:params`.                           |
| :headers    | Any extra headers to merge with the defaults.             |
| :edn-coerce | Symbols that should be parsed as EDN data before binding. |
| :body       | An expression that produces the body of the response      |
| :status     | The HTTP status code to return                            |

## #vase/redirect

Immediately send a 302 redirect response to the client. This cannot be
chained together with any other literals.

| Param    | Meaning                                                                                   |
|----------|-------------------------------------------------------------------------------------------|
| :name    | Name of the interceptor. Required.                                                        |
| :params  | Symbols to bind from `:params`.                                                           |
| :headers | Any extra headers to merge with the defaults.                                             |
| :body    | The body of the response                                                                  |
| :status  | The HTTP status code to return                                                            |
| :url     | An expression that produces a URL. This will be the "Location" header sent to the client. |

## #vase/validate

Apply specs to the inputs.

| Param                | Meaning                                                                                                 |
|----------------------|---------------------------------------------------------------------------------------------------------|
| :name                | Name of the interceptor. Required.                                                                      |
| :params              | Symbols to bind from `:params`.                                                                         |
| :headers             | Any extra headers to merge with the defaults.                                                           |
| :spec                | The spec to apply. Must be registered before this action executes.                                      |
| :request-params-path | Instead of locating parameters directly in the :request, use this path to navigate through nested maps. |

If validate is the last interceptor _or_ if any of the specs fail, it will
generate a response.

## #vase/conform

Apply specs to data on the context, reattaching the conformed data to
the context.

| Param       | Meaning                                                                                                                                  |
|-------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| :name       | Name of the interceptor.                                                                                                                  |
| :from       | The context map key where data will be found.                                                                                             |
| :spec       | The spec to apply. Must be registered before this action executes.                                                                        |
| :to         | The context map key where the conformed data will be attached.                                                                            |
| :explain-to | The context map key where explain-data will be attached if the data fails the spec. Defaults to :com.cognitect.vase.actions/explain-data. |

## #vase/query

Run a datalog query using the [Datomic API](https://docs.datomic.com/on-prem/clojure/index.html)
and return the results as JSON.

| Param       | Meaning                                                                                                           |
|-------------|------------------------------------------------------------------------------------------------------------------ |
| :name       | Name of the interceptor. Required.                                                                                |
| :params     | Symbols to bind from `:params`.                                                                                   |
| :headers    | Any extra headers to merge with the defaults.                                                                     |
| :edn-coerce | Symbols that should be parsed as EDN data before binding.                                                         |
| :query      | A datalog query expression. Has access to symbols from `params`, in order, in the `:in` clause.                   |
| :constants  | Additional values to be passed to the query, following all the parameters.                                        |
| :to         | The context map key where the query results will be attached. Defaults to :com.cognitect.vase.actions/query-data  |

If this is the last interceptor in the chain, it generates a response.

If this is not the last interceptor in the chain, it attaches the
query results to the context map at the `:to` key

## #vase.datomic/query

Same as `#vase/query`.

## #vase.datomic.cloud/query

Like `#vase.datomic/query` but runs a datalog query using the [Datomic Cloud Client API](https://docs.datomic.com/client-api/datomic.client.api.html).


## #vase/transact

Execute a Datomic transaction [DatomicAPI](https://docs.datomic.com/on-prem/clojure/index.html).
Return the results (the tx-result) as JSON. The new database value
will be used for any subsequent datalog queries.

| Param       | Meaning                                                                                                                    |
|-------------|--------------------------------------------------------------------------------------------------------------------------- |
| :name       | Name of the interceptor. Required.                                                                                         |
| :properties | Whitelist of parameters to accept from the client                                                                          |
| :headers    | Any extra headers to merge with the defaults.                                                                              |
| :db-op      | Either :vase/assert-entity or :vase/retract-entity. Defaults to :vase/assert-entity.                                       |
| :to         | The context map key where the transaction results will be attached. Defaults to :com.cognitect.vase.actions/transact-data  |

If this is the last interceptor in the chain, it generates a response.

If this is not the last interceptor in the chain, it attaches the
transaction result to the context map at the `:to` key.

## #vase.datomic/transact

Same as `#vase/transact`.

## #vase.datomic.cloud/transact

Like `#vase.datomic/transact` but runs a Datomic transaction using the [Datomic Client API](https://docs.datomic.com/client-api/datomic.client.api.html).

## #vase/intercept

Define an interceptor, given code in the descriptor file. The form
immediately after the literal is used directly as the interceptor
body.
