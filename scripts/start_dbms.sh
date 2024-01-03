#!/bin/bash

current_dir=$(pwd)
dbms=$1
image_tag=$2
date=$(date --date="yesterday" '+%Y%m%d')

# check if image tag is set, else set it to 'latest'
if [ -z "$image_tag" ]; then
    image_tag="latest"
fi

# check if dbms is set
if [ -z "$dbms" ]; then
    echo "Please set dbms"
    exit 1
fi

# Run crate
if [ "$dbms" == "crate" ]; then
    docker stop crate-test
    docker rm crate-test
    docker run --name crate-test -p 10003:4200 -p 10004:5432 crate-source-build -Cdiscovery.type=single-node
fi
# docker build -t crate-source-build scripts/Docker/crate
# docker stop crate-test
# docker rm crate-test
# docker run --name crate-test -d -p 10003:4200 -p 10004:5432 crate-source-build  -Cdiscovery.type=single-node 

# Run dolt-server
if [ "$dbms" == "dolt" ]; then
    cd $current_dir/databases
    rm -rf dolt
    mkdir -p dolt
    cd dolt
    $HOME/go/bin/dolt sql-server -P 10007
fi
# docker stop dolt-test
# docker rm dolt-test
# docker run --name dolt-test -d -p 10007:3306 dolthub/dolt-sql-server:latest

# Run risingwave
if [ "$dbms" == "risingwave" ]; then
    docker stop risingwave-test
    docker rm risingwave-test
    docker run --name risingwave-test -p 10008:4566 risingwavelabs/risingwave:nightly-$date
fi
# docker stop risingwave-test
# docker rm risingwave-test
# docker run --name risingwave-test -d -p 10008:4566 risingwavelabs/risingwave:latest


# Run Firebird
if [ "$dbms" == "firebird" ]; then
    docker stop firebird-test
    docker rm firebird-test
    docker run --name firebird-test -p 10009:3050 firebird-source-build
fi
# reproduce: /usr/local/firebird/bin/isql
# docker stop firebird-test
# docker rm firebird-test
# # docker run --name firebird-test -e ISC_PASSWORD='masterkey' -e FIREBIRD_DATABASE='default' -d -p 10009:3050 jacobalberty/firebird
# docker build -t firebird-source-build scripts/Docker/firebird
# docker run --name firebird-test -d -p 10009:3050 firebird-source-build

# Run Postgres
if [ "$dbms" == "postgresql" ]; then
    docker stop postgresql-test
    docker rm postgresql-test
    docker run --name postgresql-test -e POSTGRES_PASSWORD=postgres -p 10010:5432 postgres:latest
fi
# docker stop postgres-test
# docker rm postgres-test
# docker run --name postgres-test -e POSTGRES_PASSWORD=postgres -d -p 10010:5432 postgres

# Run CockroachDB
if [ "$dbms" == "cockroachdb" ]; then
    docker stop cockroachdb-test
    docker rm cockroachdb-test
    docker run --name cockroachdb-test -p 10011:26257 -p 10012:8080 cockroachdb/cockroach:latest start-single-node --insecure
fi
# docker stop cockroach-test
# docker rm cockroach-test
# docker run --name cockroach-test -d -p 10011:26257 -p 10012:8080 cockroachdb/cockroach:latest start-single-node --insecure

# Run TiDB
if [ "$dbms" == "tidb" ]; then
    docker stop tidb-test
    docker rm tidb-test
    docker run --name tidb-test -p 10013:4000 -p 10014:10080 pingcap/tidb:nightly
fi
# docker stop tidb-test
# docker rm tidb-test
# docker run --name tidb-test -d -p 10013:4000 -p 10014:10080 pingcap/tidb:nightly

# Run Umbra
if [ "$dbms" == "umbra" ]; then
    cd $current_dir/resources/umbra
    mkdir -p databases
    bin/sql --createdb databases/test $current_dir/scripts/Docker/umbra/init.sql
    bin/server databases/test --port=10015 
fi

# docker stop umbra-test
# docker rm umbra-test
# docker run --name umbra-test -d -p 10015:5432 umbra-build:latest

# Run MariaDB
if [ "$dbms" == "mariadb" ]; then
    docker stop mariadb-test
    docker rm mariadb-test
    docker run --name mariadb-test -e MYSQL_ROOT_PASSWORD=root -p 10016:3306 mariadb:latest
fi
# docker stop mariadb-test
# docker rm mariadb-test
# docker run --name mariadb-test -e MYSQL_ROOT_PASSWORD=root -d -p 10016:3306 mariadb:latest

if [ "$dbms" == "mysql" ]; then
    docker stop mysql-test
    docker rm mysql-test
    docker run --name mysql-test -e MYSQL_ROOT_PASSWORD=root -p 20036:3306 mysql:latest
fi

if [ "$dbms" == "percona" ]; then
    docker stop percona-test
    docker rm percona-test
    docker run -d --name percona-test -p 10022:3306 -e MYSQL_ROOT_PASSWORD=root percona/percona-server:latest --character-set-server=utf8 --collation-server=utf8_general_ci
fi

if [ "$dbms" == "virtuoso" ]; then
    docker stop virtuoso-test
    docker rm virtuoso-test
    docker run --name virtuoso-test -p 10020:1111 -e DBA_PASSWORD=dba pkleef/virtuoso-opensource-7
fi

if [ "$dbms" == "monetdb" ]; then
    docker stop monetdb-test
    docker rm monetdb-test
    docker run --name monetdb-test -p 10021:50000 -e MDB_DB_ADMIN_PASS=monetdb monetdb/monetdb:latest
fi