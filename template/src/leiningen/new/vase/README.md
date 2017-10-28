# {{name}}

FIXME

## Getting Started

1. Start the application: `lein run resources/{{namespace}}_service.fern`
2. Read your app's source code at src/{{sanitized}}/service.clj. Explore the docs of functions
   that define routes and responses.
3. See your Vase API Specification at `resources/{{namespace}}_service.fern`.
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
2. Build a Docker image: `sudo docker build -t {{name}} .`
3. Run your Docker image: `docker run -p 8080:8080 {{name}}`

### [OSv](http://osv.io/) unikernel support with [Capstan](http://osv.io/capstan/)

1. Build and run your image: `capstan run -f "8080:8080"`

Once the image it built, it's cached.  To delete the image and build a new one:

1. `capstan rmi {{name}}; capstan build`


## Links

 * [Pedestal examples](https://github.com/pedestal/samples)
 * [Vase examples](https://github.com/cognitect-labs/vase/samples)
