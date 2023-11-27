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

# Run dolt-server
# docker stop dolt-test
# docker rm dolt-test
# docker run --name dolt-test -d -p 10007:3306 dolthub/dolt-sql-server:latest

# Run risingwave
# docker stop risingwave-test
# docker rm risingwave-test
# docker run --name risingwave-test -d -p 10008:4566 risingwavelabs/risingwave:latest

# reproduce: /usr/local/firebird/bin/isql
# docker stop firebird-test
# docker rm firebird-test
# # docker run --name firebird-test -e ISC_PASSWORD='masterkey' -e FIREBIRD_DATABASE='default' -d -p 10009:3050 jacobalberty/firebird
# docker build -t firebird-source-build scripts/Docker/firebird
# docker run --name firebird-test -d -p 10009:3050 firebird-source-build

# Run Postgres
# docker stop postgres-test
# docker rm postgres-test
# docker run --name postgres-test -e POSTGRES_PASSWORD=postgres -d -p 10010:5432 postgres

# Run CockroachDB
# docker stop cockroach-test
# docker rm cockroach-test
# docker run --name cockroach-test -d -p 10011:26257 -p 10012:8080 cockroachdb/cockroach:latest start-single-node --insecure

# Run TiDB
# docker stop tidb-test
# docker rm tidb-test
# docker run --name tidb-test -d -p 10013:4000 -p 10014:10080 pingcap/tidb:nightly

# Run Umbra
# umbra_path=$current_dir/resources/umbra
# pid=$(ps -ef | grep "/home/suyang/Projects/sqlancer-scale/resources/umbra/bin/server" | grep -v grep | awk '{print $2}')
# echo $pid
# kill -9 $pid
# rm -f $umbra_path/database/*
# rm -f $umbra_path/database/.test.db.lock
# nohup $umbra_path/bin/server --createdb $umbra_path/database/test.db --port=10015 > /dev/null 2>&1 &
# sleep 1
# psql -h /tmp -p 10015 -U postgres -c "ALTER USER postgres with password 'postgres';"

# Run MariaDB
# docker stop mariadb-test
# docker rm mariadb-test
# docker run --name mariadb-test -e MYSQL_ROOT_PASSWORD=root -d -p 10016:3306 mariadb:latest

# Run Immudb
# docker stop immudb-test
# docker rm immudb-test
# docker run --name immudb-test -d -p 10017:5432 codenotary/immudb:latest