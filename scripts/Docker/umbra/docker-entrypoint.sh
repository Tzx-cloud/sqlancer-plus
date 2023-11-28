#!/bin/bash
sql -createdb test.db init.sql
server -address 0.0.0.0 test.db