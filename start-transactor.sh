cd datomic
# Make sure we have the peer lib
./bin/maven-install
# Start the transactor
./bin/transactor ./config/dev-transactor.properties

