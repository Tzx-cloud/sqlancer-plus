#!/bin/bash

current_dir=$(pwd)


# Run trino
# docker stop trino-test
# docker rm trino-test
# docker run --name trino-test -d -p 10001:8080 trinodb/trino:latest

# Run ignite
docker stop ignite-test
docker rm ignite-test
docker run --name ignite-test -v $current_dir/scripts/configs/ignite-config.xml:/config.xml  -e CONFIG_URI=/config.xml -d -p 10002:10800 apacheignite/ignite