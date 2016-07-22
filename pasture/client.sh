#!/bin/bash

USAGE=$'This script requires an API descriptor file;\nUsage:\tclient.sh DESCRIPTOR_FILE.edn\n'
DFILE=$1
HOST_PATH="http://127.0.01:8080/api"

if [ $# -eq 0 ]; then
    R=`curl -s "$HOST_PATH?sep=\\n"`
    printf " $R\n"
    exit 0
elif [ $# -ne 1 ]; then
    echo "$USAGE"
    exit 1
fi

if [ -r $DFILE ]; then
    echo "Sending: $DFILE"
else
    echo "The descriptor file does not exist or is not readable;"
    echo "$USAGE"
    exit 1
fi

# cURL doesn't null-terminate file contents, I don't know why, so this doesn't work
#curl -X POST -d @$DFILE $HOST_PATH --header "Content-Type:application/edn"

CONTENTS=`cat $DFILE`
CONTENTS="$CONTENTS\0"
curl -X POST -d "$CONTENTS" $HOST_PATH --header "Content-Type:application/edn\0"
echo

