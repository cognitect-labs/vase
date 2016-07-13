# Pet store full version example

This is a pet store full version app, Swagger ui on the client side and Vase app on the backend.
The app is based on Swagger example, http://petstore.swagger.io/ with some tweaks to fit to Vase.
It is an example of OpenAPI, https://openapis.org/, a specification for RESTful API.

This full version of pet store app is not like a simple version app, close to a real app.
It may be complicated if it is the first app to look at.
Just for understanding Vase concept, a simple version of pet store app is a better one.

There are multiple data models, many routes and data conversions for pre/post processes defined in descriptors.
The descriptors are devided into five since a single descriptor would be quit long and hard to write and read.
Each descriptor defines each model and routes to manage those, for example pet, order or user.
Vase has an ability to handle multiple descriptors. There's an extra code to read five, but that's it.
Vase users don't need to add any more code for multiple descriptors.

When a Vase app gets started, it transacts all schemas defined in the descriptors to Datomic database(s).
Datomic uris are also defined in the descriptors. Those can be the same or different ones.
If the app doesn't need an extra logic other than sending transact data or making queries,
the app is ready to use with descriptor(s) only.
If the app needs extra logics such as a conversion from RFC3339 datetime string to datomic friendly form,
the app needs custom interceptors. Definitions of interceptor chains are also done in the descriptors.
This full version of pet store app has a few interceptors for such pre/post processes.


## Datomic preparation

If you are going to use Datomic's memory database, there's nothing to do.
Vase pulls Datomic from its dependency.
If you want to use Datomic's transactor, you should complete a couple of preparations.

1. Download Datomic Pro archive and extract.
2. Go to Datomic Pro's top directoy and type `bin/maven-install` to install Datomic to a local maven repo.
3. Edit `project.clj` to use Datomic pro, also exclude Datomic free from Vase dependency.
   See, [project.clj](project.clj) for details.
4. Start up Datomic transactor.


## Getting started

1. Start the application: `lein run`
2. Go to [localhost:8888](http://localhost:8888/) to see Swagger UI.

## How to use the app

Usages are the same for all models.
Here's are basic usage:

1. Click a model (category, tag, pet, store, or user)
2. Click a route ("POST /categories" or other)
3. Fill body or write parameter(s)
4. Click "Try it out!" button

Depend on the models, there are slight differences.
The category and tag's model is simple, so Swagger UI fits well.
You can use an example value as is. So, click example value, then edit in body window.

The pet and store(order) models have reference values.
On Datomic, those are entity references and expressed in array forms.
The first value of the array must be an attribute ident.
Swagger UI doesn't fit well for this kind of data.

For this reason, many operations have an example data in "Implementation Notes" section.
Copy and paste a map of example data in body window then edit the data.

For all models, id should be unique in each model.
For example, you can't add more than one of category id 0, but category id 0 and tag id 0 don't conflict.


### Category and Tag

Before adding a pet, at least one of category and tag should be added
These two models are quit simple. Operations are POST and GET only.
You can add multiple categories/tags at the same time by POST.
GET will return all categories or tags.


### Pet

Once more than one categories and tags are added, you can add pets.
If you add at least one pet, you can update(PUT), delete or make a queries.

- POST /pets

      You can add multiple pets at the same time.
      References to categories and tags should be entity reference in array form.
      See "Implementation Notes" section for example data.

- PUT /pets

      You can update pets data multiple attributes and multiple pets at the same time.
      See "Implementation Notes" section for example data.

- DELETE /pets

      You can delete pets by IDs. See "Implementation Notes" section for example data.

- GET /pets/findByStatus

      You can find pets by statuses.
      Statues should be given by an array whose elements are idents such as petstore.pet.status/available.
      See "Implementation Notes" section for example data.

- Get /pet/{petId}

      You can find a single pet by ID.
      Input id value in the field.


#### What to see at pet section

The pet schema has three reference types:

- petstore.pet/category: cardinality one, reference to entity
- petstore.pet/tags: cardinality many, reference to entity
- petstore.pet/status: cardinality one, reference to ident

Like normal Datomic transact data, entity references are expressed by arrays.

A schema of petstore.pet/photoUrls is cardinality many, string type.
So, this data is given by an array of string.


The pet section has two kinds of queries: findByStatus and find by an id.
The former returns multiple pets while the latter returns one.
The routes are slightly different: `/pets` and `/pet`.


### Store

Once more than one pets are added, you can add orders.
If you add at least one pet, you can update(PUT), delete or make a queries.


- POST /store/orders

      You can add multiple orders at the same time.
      A reference to pet should be entity reference in array form.
      See "Implementation Notes" section for example data.

- PUT /store/orders

      You can update pets data multiple attributes and multiple pets at the same time.
      See "Implementation Notes" section for example data.

- DELETE /store/orders

      You can delete pets by IDs. See "Implementation Notes" section for example data.

- Get /store/orders/{orderId}

      You can find a single pet by ID.
      Input id value in the field.


#### What to see at store section

Other than reference type, you should look at the value of petstore.order/shipDate.
As in OpenAPI specification, date should be written in the RFC3339 format.
While Datomic uses another datetime format or `java.util.Date` object.
A data conversion should be done before the data is transacted.
This data conversion is done in a custom interceptor, `petstore-full.interceptors/date-conversion`.
See [interceptors.clj](src/petstore_full/interceptors.clj) for details.
The date-conversion interceptor is included in the interceptor chain and works as pre-processor before transaction.
The interceptor chain is defined in the descriptor, [petstore-full-order.edn](resources/petstore-full-order.edn).


Additionally, be aware that paths to order have an extra layer.
There's no real layer. This is done by route definitions in the descriptor.


### User

A user model doesn't have any reference to other models. You can add users without adding any other models.
In this app, whether a user is logged in or not doesn't affect on other operations.


- POST /users

      You can add multiple users at the same time.
      Both id and username must be unique. The username is used to make a query and delete/update.
      See "Implementation Notes" section for example data.

- PUT /users

      You can update users data multiple attributes and multiple pets at the same time.
      See "Implementation Notes" section for example data.

- DELETE /users

      You can delete users by username. See "Implementation Notes" section for example data.

- GET /users/login

      You can authnticate a user by username and password.

- GET /users/logout

      You should be log out user by this path. But this app doesn't have any logic for logout.

- Get /user/{username}

      You can find a single user by username.
      Input username in the field.


#### What to see at user section

A couple of pre/post processes are done before/after transactions or queries.

- When a user is registered, a given password will be encrypted (pre-process), then saved in the database.
- When a transaction or query result will be returned to the client,
  a password is replaced by a sequence of * not to show the real data in the database (post-process).
- During the user authentication, the password in the query result will be decrypted and compared to the input (post-process).


This user management section shows how Vase users can add pre/post processes before/after transactions or queries.
See [petstore-full-user.edn](resources/petstore-full-user.edn) and [interceptors.clj](src/petstore_full/interceptors.clj) for details.
Vase's core actions such as #vase/transact or #vase/query are also interceptors.
Given that, to add another custom interceptors to the interceptor chain, list those in a vector including Vase's actions.
This is done in the descriptor(s).


This app's login/logout does nothing for other operations unlike original Swagger example.
Those exists for two purposes:

1. route conflict solution
2. pre/post processes example

From the nature of user login, RESTful path may be `/user/login` rather than `/users/login`(plural).
However, there's a path definition `/user/{username}`, which makes a query based on a unique username.
Still, we can define the path `/user/login` in the Vase descriptor,
however, Pedestal sees `login` as a username when a client requests this path.
There may be another better solution for this sort of route conflict.



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
* [Pedestal docs](https://github.com/pedestal/guides)

