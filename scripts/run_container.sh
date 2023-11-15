#!/bin/bash

current_dir=$(pwd)


# Run trino
# docker stop trino-test
# docker rm trino-test
# docker run --name trino-test -d -p 10001:8080 trinodb/trino:latest

# Run ignite
# docker stop ignite-test
# docker rm ignite-test
# docker run --name ignite-test -v $current_dir/scripts/configs/ignite-config.xml:/config.xml  -e CONFIG_URI=/config.xml -d -p 10002:10800 apacheignite/ignite

# Run crate
# docker build -t crate-source-build scripts/Docker/crate
# docker stop crate-test
# docker rm crate-test
# docker run --name crate-test -d -p 10003:4200 -p 10004:5432 crate-source-build  -Cdiscovery.type=single-node 


# Run apache/hive
# export HIVE_VERSION=4.0.0-beta-2-SNAPSHOT
# docker stop hive-test
# docker rm hive-test
# docker run -d -p 10005:10000 -p 10006:10002 --env SERVICE_NAME=hiveserver2 --name hive-test apache/hive:${HIVE_VERSION}