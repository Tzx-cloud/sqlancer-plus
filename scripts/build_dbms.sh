#!/bin/bash

current_dir=$(pwd)
dbms=$1
image_tag=$2
# current date 
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

# Build crate
if [ "$dbms" == "crate" ]; then
    docker build -t crate-source-build scripts/Docker/crate
fi

# Build dolt
if [ "$dbms" == "dolt" ]; then
    cd $current_dir/resources
    rm -rf dolt
    git clone --depth 1 https://github.com/dolthub/dolt.git
    cd dolt/go
    go install ./cmd/dolt
fi

# Build firebird
if [ "$dbms" == "firebird" ]; then
    docker build -t firebird-source-build scripts/Docker/firebird
fi

# Pull risingwave
if [ "$dbms" == "risingwave" ]; then
    docker pull risingwavelabs/risingwave:nightly-$date
fi

# download umbra
if [ "$dbms" == "umbra" ]; then
    cd $current_dir/resources
    rm -rf umbra
    rm umbra.tar.xz*
    wget https://db.in.tum.de/\~neumann/umbra.tar.xz
    tar -xf umbra.tar.xz
fi

# Pull tidb
if [ "$dbms" == "tidb" ]; then
    docker pull pingcap/tidb:nightly
fi

# Pull cockroachdb
if [ "$dbms" == "cockroachdb" ]; then
    docker pull cockroachdb/cockroach:latest
fi

# Pull postgres
if [ "$dbms" == "postgres" ]; then
    docker pull postgres:latest
fi


# Pull mariadb
if [ "$dbms" == "mariadb" ]; then
    docker pull mariadb:latest
fi

# Pull mysql
if [ "$dbms" == "mysql" ]; then
    docker pull mysql:latest
fi

# Download and install duckdb
if [ "$dbms" == "duckdb" ]; then
    # The URL of the repository
    REPO_URL="https://oss.sonatype.org/content/repositories/snapshots/org/duckdb/duckdb_jdbc/0.10.0-SNAPSHOT/"

    # Use curl to fetch the contents of the repository
    # This example assumes the repository lists files in a way that can be parsed with grep and sort
    # This part of the script will likely need to be customized based on the actual repository structure
    LATEST_JAR_URL=$(curl -s $REPO_URL | grep -oP 'href=".*[0-9]+\.jar"' | sort -r | head -1 | cut -d '"' -f 2)

    # Full URL of the latest jar file
    FULL_URL="$LATEST_JAR_URL"

    # Download the latest jar file with wget
    cd $current_dir/resources
    wget $FULL_URL

    echo "Downloaded latest JAR file from $FULL_URL"
    cd ..

    # Install the jar file
    mv resources/duckdb_jdbc*.jar $current_dir/target/duckdb_jdbc.jar
    mvn install:install-file -Dfile=target/duckdb_jdbc.jar -DgroupId=org.duckdb -DartifactId=duckdb_jdbc -Dversion=0.9.2 -Dpackaging=jar
    mvn package -DskipTests
fi

# Install sqlite
if [ "$dbms" == "sqlite" ]; then
    cd $current_dir/resources/sqlite-jdbc
    make clean
    JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64/ make all
    mv target/sqlite-jdbc*.jar $current_dir/target/sqlite-jdbc.jar
    cd ../../
    mvn install:install-file -Dfile=target/sqlite-jdbc.jar -DgroupId=org.xerial -DartifactId=sqlite-jdbc -Dversion=3.40.0.0 -Dpackaging=jar
fi

if [ "$dbms" == "percona" ]; then
    docker pull percona/percona-server:latest
fi

if [ "$dbms" == "monetdb" ]; then
    cd $current_dir/resources
    wget https://clojars.org/repo/monetdb/monetdb-jdbc/3.3/monetdb-jdbc-3.3.jar
    mv monetdb-jdbc-3.3.jar $current_dir/target/monetdb-jdbc.jar
    cd ..
    mvn install:install-file -Dfile=target/monetdb-jdbc.jar -DgroupId=monetdb -DartifactId=monetdb-jdbc -Dversion=3.3 -Dpackaging=jar
    mvn package -DskipTests
    docker pull monetdb/monetdb-r:latest
fi