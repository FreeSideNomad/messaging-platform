#!/bin/bash
set -e

echo "=========================================="
echo "Flyway: Running migrations for reliable database..."
echo "=========================================="
/flyway/flyway -configFiles=/etc/flyway/flyway-reliable.conf migrate

echo ""
echo "=========================================="
echo "Flyway: Running migrations for payments database..."
echo "=========================================="
/flyway/flyway -configFiles=/etc/flyway/flyway-payments.conf migrate

echo ""
echo "=========================================="
echo "Flyway: All migrations complete!"
echo "=========================================="
