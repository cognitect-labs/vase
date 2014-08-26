#!/bin/sh

# This is development only, Please use the Datomic Console script directly
# in any other scenario.

cd datomic
./bin/console -p 9090 dev datomic:dev://localhost:4334/

