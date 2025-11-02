#!/bin/bash
set -e

echo "========================================="
echo "Starting Endurance Test Execution"
echo "========================================="

# Start infrastructure
echo "Starting Docker infrastructure..."
docker-compose up -d

# Wait for services
echo "Waiting for services to be healthy..."
sleep 120

# Run endurance test (long-running)
echo "Running endurance test (10 minutes)..."
echo "This will take approximately 15 minutes..."
mvn test -Pendurance -Dtest=EnduranceTest

# Collect metrics
echo "Collecting final metrics..."
docker stats --no-stream > endurance-docker-stats.txt
docker-compose logs > endurance-logs.txt

# Cleanup
echo "Stopping infrastructure..."
docker-compose down

echo "========================================="
echo "Endurance Tests Completed"
echo "Metrics saved to: endurance-docker-stats.txt"
echo "Logs saved to: endurance-logs.txt"
echo "========================================="
