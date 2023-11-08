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
docker stop crate-test
docker rm crate-test
docker run --name crate-test -d -p 10003:4200 -p 10004:5432 crate  -Cdiscovery.type=single-node