# Pet Store Example

This is a simple version of a back end for the well-known Pet Store
app. This app should help Vase newcomers see how to create an API with
Vase.

# Running the sample

1. Start it up with `clj -Mrun`
2. Go to [localhost:8080/index.html](http://localhost:8080/index.html)
   to see the Swagger UI.
3. Click `default`
    - GET /pets will get all pets

      click "Try it out!" button.

    - POST /pets will transact new pet(s)

      Click "Example Value" and edit the name and tag values, then click "Try it out!".

    - GET /pet/{id} will get a single pet by ID

      Input a pet ID, then click "Try it out!" button

# Datomic Setup

This sample uses Datomic with an in-memory database. Since Vase
already depends on "datomic-free," you don't need to add anything
specific for the in-memory case.

If you want to use on-disk storage with Datomic Pro, two steps are
needed:

1. In `project.clj`, uncomment the dependency for `com.datomic/datomic-pro`
2. Change the connection defined in `resources/petstore.fern` as
   described in comments in that file.

```clojure
:vase.descriptor/datomic-uri "datomic:dev://localhost:4334/pet-store"
```
