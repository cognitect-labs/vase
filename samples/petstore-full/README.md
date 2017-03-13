# Pet store full version example

This is a full implementation of the common "pet store" server side
application. It uses an automatic Swagger UI for an easy API
client. This app is based on the
[Swagger example](http://petstore.swagger.io/) with some tweaks to fit
in Vase.  The pet store is an example of using
[OpenAPI](https://openapis.org/) as a specification to describe and
document RESTful APIs.

This full version of pet store app is not like a
[simple version app](../pet-store), it is closer to what a real
application would need.  This full version might not be the best place
to start if you're just looking at Vase for the first time.

This app has multiple data models, many routes and custom interceptors
for pre/post processing.  The models, routes and interceptor chains
are defined in descriptors under `/resources`.  The descriptors for
this app could have all been written in a single file, but the
descriptor would have gotten really big. A big descriptor file can
make it hard to track a nested structure, not to mention error prone
to add a new definition in the right place. So for this sample, we
divided the descriptor into five modules.

Each descriptor defines a type of entity with its schema and routes:

   - Tag
   - Category
   - Pet
   - Order
   - User

Because each of the descriptors comes in as data, `service.clj` does a
bit of manipulation to glom all the descriptors together into one data
structure. That difference is confined to `service.clj`.  Vase users
don't need to add any more code because of multiple descriptors.

Each descriptor consists of a bit of schema and some route
definitions. The route definitions map to interceptor chains. When the
service starts up, it transacts all the schemas defined in the
descriptors into Datomic database.

Each EDN file also defines a Datomic URI. Those can be the same or
different ones, for example, descriptor A, B and C have uri1 while
descriptor D has uri2. Vase recognizes what schema should be
transacted to what database. This service puts all the modules into
the same database so it's easier to run. In production you might shard
these out to separate Datomic transactors.

This app has a few custom interceptors for pre- and post-processing
around transactions and queries. For example, datetime fields need to
be converted from RFC3339 datetime string to a Datomic-friendly form.
You can see these custom interceptors in the interceptor chains in the
descriptors. For example, an HTTP PUT on
`/petstore-full/v1/store/orders` puts the
`petstore-full.interceptors/date-conversion` interceptor just ahead of
the `#vase/transact` interceptor.

## Datomic preparation

The EDN files are written to use Datomic Free. If you want to use
Datomic Pro, you will need to do a bit of prep work:

1. Download Datomic Pro from my.datomic.com and extract it.
2. Go to Datomic Pro's top directoy and type `bin/maven-install` to
   install libraries to a local maven repo.
3. Edit `project.clj` to use Datomic pro or swap it out for
   [project.clj-with-datomic-pro](project.clj-with-datomic-pro).
4. Start up Datomic transactor.
5. Change all the `:datomic-uri` settings under `resources/*.edn` to
   use `datomic:pro://localhost:4334/petstore-full`


If you want to use the in-memory Datomic database, packaged in Vase,
then instead:

1. Change all the `:datomic-uri` settings under `resources/*.edn` to
use `datomic:mem://petstore-full`.`


## Getting started

1. Start the application: `lein run`
2. Go to [localhost:8888](http://localhost:8888/) to see Swagger UI.

## How to use the app

Usages are the same for all models.  Here is a basic usage:

1. Click a model (category, tag, pet, store, or user)
2. Click a route ("POST /categories" or other)
3. Fill body or input parameter(s)
4. Click "Try it out!" button

Depend on the models, there are some odds to match Swagger UI and
Vase.  The category and tag's models are simple, so Swagger UI fits
well.  You can use an example value on the ui as is.  So, click a text
portion of example value, then edit values in body window.

The pet and store(order) models have reference values.  On Datomic,
those are entity references and expressed in array forms.  The first
value of the array must be an attribute ident.  Swagger UI doesn't fit
well for this kind of data.

Because of this, many operations have an example data in
"Implementation Notes" section.  Copy and paste a map of example data
in body window then edit values.

For all models, id should be unique in each model.  For example, you
can't add more than one of category id 0, but category id 0 and tag id
0 don't conflict.


### Category and Tag

#### Usage

Before adding a pet, at least one category and tag should be added.

- POST /categories
- POST /tags

      You can add multiple categories/tags at the same time.  Input
      data format is the exactly the same as Example Value.

- GET /categories
- GET /tags

      You can find all categories/tags

#### What to see in category/tag section

Those sections are very basic. It's good to see how Swagger UI works.
Also, if you look at the descriptor, you can easily figure out how
Vase works.


### Pet

#### Usage

Once at least one category and one tag are added, you can add pets.
If you add at least one pet, you can update(PUT), delete or make a
queries.

- POST /pets

      You can add multiple pets at the same time.  References to
      categories and tags should be entity reference in array form.
      See "Implementation Notes" section for example data.

- PUT /pets

      You can update pets data multiple attributes and multiple pets
      at the same time.  See "Implementation Notes" section for
      example data.

- DELETE /pets

      You can delete pets by IDs. See "Implementation Notes" section
      for example data.

- GET /pets/findByStatus

      You can find pets by statuses.  Statues should be given by an
      array whose elements are idents such as
      petstore.pet.status/available.  See "Implementation Notes"
      section for example data.

- Get /pet/{petId}

      You can find a single pet by ID.  Input id value in the field.


#### What to see at pet section

The pet schema has three reference types:

- petstore.pet/category: cardinality one, reference to entity
- petstore.pet/tags: cardinality many, reference to entity
- petstore.pet/status: cardinality one, reference to ident

Like normal Datomic transact data, entity references are expressed by
arrays.

A schema of petstore.pet/photoUrls is cardinality many, string type.
So, this data expects an array of string in input.


The pet section has two kinds of queries: findByStatus and find by an
id.  The former returns multiple pets while the latter returns one.
The routes are slightly different: `/pets` and `/pet`.


### Store

#### Usage

Once more than one pets are added, you can add orders.  If you add at
least one pet, you can update(PUT), delete or make a queries.


- POST /store/orders

      You can add multiple orders at the same time.  A reference to
      pet should be entity reference in array form.  See
      "Implementation Notes" section for example data.

- PUT /store/orders

      You can update pets data multiple attributes and multiple pets
      at the same time.  See "Implementation Notes" section for
      example data.

- DELETE /store/orders

      You can delete pets by IDs. See "Implementation Notes" section
      for example data.

- Get /store/orders/{orderId}

      You can find a single pet by ID.
      Input id value in the field.


#### What to see at store section

Other than reference type, you should look at the value of
petstore.order/shipDate.  As in OpenAPI specification, date should be
written in the RFC3339 format.  While Datomic uses another datetime
format or `java.util.Date` object.  A data conversion should be done
before the data is transacted.  This data conversion is done in a
custom interceptor, `petstore-full.interceptors/date-conversion`.  See
[interceptors.clj](src/petstore_full/interceptors.clj) for details.
The date-conversion interceptor is included in the interceptor chain
and works as pre-processor before transaction.  The interceptor chain
is defined in the descriptor,
[petstore-full-order.edn](resources/petstore-full-order.edn).


Additionally, be aware that paths to orders have an extra layer.
There's no real layer such as store.  This layer setting is done by
route definitions in the descriptor.


### User

#### Usage

A user model doesn't have any reference to other models. You can add
users without adding any other models.  In this app, whether a user is
logged in or not doesn't affect on other operations.


- POST /users

      You can add multiple users at the same time.  Both id and
      username must be unique. The username is used to make a query
      and delete/update.  See "Implementation Notes" section for
      example data.

- PUT /users

      You can update users data multiple attributes and multiple pets
      at the same time.  See "Implementation Notes" section for
      example data.

- DELETE /users

      You can delete users by username. See "Implementation Notes"
      section for example data.

- GET /users/login

      You can authenticate a user by username and password.

- GET /users/logout

      You should log a user out by this path. But this app doesn't
      have any logic for logout.

- Get /user/{username}

      You can find a single user by username.  Input username in the
      field.


#### What to see at user section

A couple of pre/post processes are done before/after transactions or
queries.

- When a user is registered, a given password will be encrypted
  (pre-process), then saved in the database.
- When a transaction or query result will be returned to the client, a
  password is replaced by a sequence of * not to show the real data in
  the database (post-process).
- During the user authentication, the password in the query result
  will be decrypted and compared to the input (post-process).


These pre/post processes are done by custom interceptors.  See
[petstore-full-user.edn](resources/petstore-full-user.edn) and
[interceptors.clj](src/petstore_full/interceptors.clj) for details.
Vase's core actions such as `#vase/transact` or `#vase/query` are also
interceptors.  Given that, to add another custom interceptors to the
interceptor chain, list those in a vector including Vase's actions.


This app's login/logout does nothing for other operations unlike
original Swagger example.  Those exists for two purposes:

1. route conflict solution
2. pre/post processes example

From the nature of user login, RESTful path may be `/user/login`
rather than `/users/login` (plural).  However, there's a path
definition `/user/{username}`, which makes a query based on a unique
username.  Still, we can define the path `/user/login` in the Vase
descriptor, however, Pedestal sees `login` as a username when a client
requests this path.  There may be another better solution for this
sort of route conflict.

## Configuration

### Port number

A port number is defined in both Swagger UI and Vase app.
If you want to use other port number:

- Change `"host": "localhost:8888",` in `resources/public/v1/petstore-full.json` for Swagger UI.
- Change `::http/port 8888` in `src/petstore_full/services.clj` for Vase app.


## Read code

1. `src/petstore_full/server.clj` and `src/petstore_full/service.clj`

      Vase is implemented on top of Pedestal, the app has `server.clj` and `service.clj` like other typical Pedestal apps.
      Normally, these don't need to be changed so much for individual Vase app. Those are almost boiler plate.
      The differences to the typical Pedestal app are:

      - Normal and Vase route are merged
      - Descriptor(s) are loaded
      - Interceptor definition file is required in service.clj

2. `src/petstore_full/interceptors.clj`

      All custom interceptors are defined here. There's no specific practice to organize custom interceptors.
      You may write interceptors in service.clj or others.
      If you write custom interceptors other than service.clj, the file should be required in service.clj.

3. Descriptors in `resources` directory

      The descriptor is a core concept of Vase, understanding the descriptor is a key to write a Vase app.
      Read Vase documents to understand what part is doing what.
      All descriptors in pet store full version have three kinds of definitions:

      - meta info (:activated-apis, :datomic-uri)
      - schema definition (:vase/norms)
      - route definition (:vase/apis)

      The pet store app uses shorthand schema definitions as much as possible and some original Datomic schema definitions
      for those shorthand doesn't cover.
      Some of route definitions have custom interceptor definitions in individual routes.
      Vase also allows user to add custom interceptors which applied to to all routes.

4. Learn more! See the [Links section below](#links).


## Links
* [Vase docs](../../docs)
* [Vase example descriptor](../../test/resources/test_descriptor.edn)
* [Pedestal docs](https://github.com/pedestal/pedestal/tree/master/guides)
