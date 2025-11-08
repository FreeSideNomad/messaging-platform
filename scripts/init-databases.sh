#!/bin/bash
set -e

echo "PostgreSQL Init Script: Creating databases..."

# Connect to default postgres database and create target databases
psql -v ON_ERROR_STOP=0 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
    CREATE DATABASE reliable;
    CREATE DATABASE payments;

    -- Grant all privileges to postgres user
    GRANT ALL PRIVILEGES ON DATABASE reliable TO $POSTGRES_USER;
    GRANT ALL PRIVILEGES ON DATABASE payments TO $POSTGRES_USER;
EOSQL

echo "PostgreSQL Init Script: Databases created successfully!"
