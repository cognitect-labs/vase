#!/bin/sh

LEIN=`which lein`
: ${LEIN:="./resources/lein"}

MODE=$1

$LEIN run$MODE

