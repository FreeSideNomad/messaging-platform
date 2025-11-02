#!/bin/bash
set -e

echo "========================================="
echo "Starting E2E Test Execution"
echo "========================================="

# Start infrastructure
echo "Starting Docker infrastructure..."
docker-compose up -d

# Wait for all services to be healthy
echo "Waiting for services to be healthy..."
sleep 120

# Check health
echo "Checking service health..."
docker-compose ps

# Run E2E tests
echo "Running E2E tests..."
mvn test -Pe2e -Dtest=FunctionalE2ETest

# Cleanup
echo "Stopping infrastructure..."
docker-compose down

echo "========================================="
echo "E2E Tests Completed"
echo "========================================="
