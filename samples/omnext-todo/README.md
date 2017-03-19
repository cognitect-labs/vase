# omnext-todo

This is a simple "TODO" example using Vase.
An [Om.next](https://github.com/omcljs/om/wiki/Documentation-(om.next)) demand-driven API is also integrated.


## Getting Started

1. Start the application: `lein run`
2. Go to [localhost:8080](http://localhost:8080/) to see: `Hello World!`
3. You can see [all available API endpoints](http://localhost:8080/api)
   and the [current list of TODOs](http://localhost:8080/api/omnext-todo/main/todos).
   You can [optionally filter that list](http://localhost:8080/api/omnext-todo/main/todos?selector=[:todo/title]) too.
3. Read your app's source code at `src/omnext_todo/service.clj`. Explore the docs of functions
   that define routes and responses.
4. See your Vase API Specification at `resources/omnext-todo_service.edn`.
5. Run your app's tests with `lein test`. Read the tests at `test/omnext_todo/service_test.clj`.

### POSTing data to the API

You can POST new TODOs with curl:

`curl -H "Content-Type: application/json" -X POST -d '{"payload": [{"todo/title": "Write a test for TODO example"}]}' http://localhost:8080/api/omnext-todo/main/todos`

You can also see the spec/validation response if you try to transact and incomplete TODO

`curl -H "Content-Type: application/json" -X POST -d '{"payload": [{}]}' http://localhost:8080/api/omnext-todo/main/todos`


## Configuration

To configure logging see config/logback.xml. By default, the app logs to stdout and logs/.
To learn more about configuring Logback, read its [documentation](http://logback.qos.ch/documentation.html).


## Developing your service

1. Start a new REPL: `lein repl`
2. Start your service in dev-mode: `(def dev-serv (run-dev))`
3. Connect your editor to the running REPL session.
   Re-evaluated code will be seen immediately in the service.
4. All changes to your Vase Service Descriptor will be loaded - no re-evaluation
   needed.

### [Docker](https://www.docker.com/) container support

1. Build an uberjar of your service: `lein uberjar`
2. Build a Docker image: `sudo docker build -t omnext-todo .`
3. Run your Docker image: `docker run -p 8080:8080 omnext-todo`

### [OSv](http://osv.io/) unikernel support with [Capstan](http://osv.io/capstan/)

1. Build and run your image: `capstan run -f "8080:8080"`

Once the image it built, it's cached.  To delete the image and build a new one:

1. `capstan rmi omnext-todo; capstan build`


## Links

 * [Pedestal examples](https://github.com/pedestal/samples)
 * [Vase examples](https://github.com/cognitect-labs/vase/tree/master/samples)


