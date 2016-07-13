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


### Add some categories and tags.

This should be done before addin pets.

1. Click category or tag, then POST.
2. Click on a text of example value then template will be pasted on body window.
   Edit only values not keys. The id must be unique among categories/tags.
3. Click "Try it out!" button.
4. Check what you added so far. Click GET of category to tag. then "Try it out!" button.

### Add some pets

Once more than one categories and tags are added, try adding some pets.
To add pets, an example value shown in the window doesn't express precise transact data.
Example value shows only JavaScript data structure.
While transaction data needs an entity expression in array form. The first value must be an attribute ident.
For this problem, the example data is shown in "Implementation Notes" section.

1. Click pet, then POST
2. Copy the example transact data map in "Implementation Notes" section and past it in body value text area.
   You may add multiple pets at the same time.
   To add another pet, add more map(s) in the value (array) of "payload" key. 
3. Edit values. The id must be unique among pets.
   Values of petstore.category/id and petstore.tag/id are what you added beforehand.
4. Click "Try it out!" button.

### Other operations on pets

The pet section has more operations such as delete, update(put) and query.
Example values of delete and update are in "Implemetation Notes" section, so copy&paste these then edit.

The pet section has two kinds of queries: findByStatus and find by an id.
The former returns multiple pets while the latter returns one.
The routes are slightly different: `/pets` and `/pet`.


### Add, delete, update and make queries on orders

All operations are basically the same as the pet section.
Transaction related delete, post and put example data is shown in "Implementation Notes" section.
Copy&pasting those then edit would be easier to write from scratch.

One difference to mention about is the value of petstore.order/shipDate.
As in OpenAPI specification, date should be written in the RFC3339 format.
While Datomic uses another datetime format or `java.util.Date` object.
A data conversion is done in a custom interceptor, `petstore-full.interceptors/date-conversion`.
See [interceptors.clj](src/petstore_full/interceptors.clj) for details.
The date-conversion interceptor is included in the interceptor chain and works as pre-processor before transaction.
The interceptor chain is defined in the descriptor, [petstore-full-order.edn](resources/petstore-full-order.edn).

Additionally, be aware that paths to order have an extra layer.
There's no real layer. This is done by route definitions in the descriptor.


### Manage users

Database related operations, post, put, delete and get by username are same as other models.
Like other sections, you'll see example data in "Implementation Notes" section.
However, both pre/post processes are done before/after transaction or query for the user managements.

- When a user is registered, a given password will be encrypted (pre-process), then saved in the database.
- When a transaction or query result will be returned to the client,
  a password is replaced by a sequence of * not to show the real data in the database (post-process).
  After that, the result will be returned to the client.
- During the user authentication, the password in the query result will be decrypted and compared to the input (post-process).


This user management section shows how Vase users can add pre/post processes before/after transactions or queries.
See [petstore-full-user.edn](resources/petstore-full-user.edn) and [interceptors.clj](src/petstore_full/interceptors.clj) for details.
Vase's core actions such as #vase/transact or #vase/query are also interceptors.
Given that, to add another custom interceptors to the interceptor chain, list those in a vector including Vase's actions.
This is done in the descriptor(s).

Additionally, this app's login/logout does nothing for other operations unlike original Swagger example.
Those exists for two purposes:

1. route conflict solution
2. pre/post processes example

From the nature of user login, RESTful path may be `/user/login` rather than `/users/login`(plural).
There's a path definition `/user/{username}`, which makes a query based on a unique username.
Still, we can define the path `/user/login`, however, Pedestal sees `login` is a username when a client requests this path.
There may be another better solution for this sort of route conflict.

As for pre/post processes example, login/logout are the same as other interceptors in the user managements.


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
* [Vase docs](../docs)
* [Vase example descriptor](../test/resources/test_descriptor.edn)
* [Pedestal docs](https://github.com/pedestal/guides)

