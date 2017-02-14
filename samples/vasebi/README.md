# FoodMart: Vase Business Intelligence

This sample shows a simple business intelligence dashboard built on a Vase
service.  The dashboard is a [pivot table](https://en.wikipedia.org/wiki/Pivot_table)
that fetches entities from a demand-driven Datomic query via Vase.

The application data is based around sales from grocery stores in various locations.

## Getting Started

1. Start the application: `lein run`
2. Go to [localhost:8080](http://localhost:8080/) to see the Pivot Dasboard
3. Play around with the graph types, drag-and-drop column and row names, dig into the data.

## Digging deeper

1. Read your app's source code at src/vasebi/service.clj. Explore the docs of functions
   that define routes and responses.
2. See your Vase API Specification at `resources/vasebi_service.edn`.
3. Run your app's tests with `lein test`. Read the tests at test/vasebi/service_test.clj.
4. Learn more! See the [Links section below](#links).


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
2. Build a Docker image: `sudo docker build -t vasebi .`
3. Run your Docker image: `docker run -p 8080:8080 vasebi`

### [OSv](http://osv.io/) unikernel support with [Capstan](http://osv.io/capstan/)

1. Build and run your image: `capstan run -f "8080:8080"`

Once the image it built, it's cached.  To delete the image and build a new one:

1. `capstan rmi vasebi; capstan build`


## Links

 * [Pedestal examples](https://github.com/pedestal/samples)
 * [Vase examples](https://github.com/cognitect-labs/vase/samples)
 * [JS Pivottable](https://github.com/nicolaskruchten/pivottable) - MIT License


