## Pet Store Example

This example contains Swagger UI and Vase example

## Datomic Setup

This example uses Datomic Pro dev transactor. To start dev transactor,
follow the document, "Running the transactor with the dev storage protocol" section in
[Getting Started](http://docs.datomic.com/getting-started.html). 

If you prefer to use memory database, change `:datomic-uri` in `resources/pet-store.edn`.
 

## Getting Started

1. Start the application: `lein run`
2. Go to [localhost:8080/index.html](http://localhost:8080/index.html) to see Swagger UI.
3. Click `default`
    - GET /pets will get all pets
    
      click "Try it out!" button.
  
    - POST /pets will transact new pet(s)
    
      click "Examble Value" and edit id, name and tag values, then click "Try it out!" button.
  
    - GET /pets/{id} will get a single pet by id
  
      input id, then click "Try it out!" button

4. Read your app's spec at resources/pet-store.edn and source code at src/pet_store/service.clj.


## Configuration (Pedestal)

To configure logging see config/logback.xml. By default, the app logs to stdout and logs/.
To learn more about configuring Logback, read its [documentation](http://logback.qos.ch/documentation.html).

