#!/bin/bash
set -e

echo "========================================="
echo "Starting Performance Test Execution"
echo "========================================="

# Start infrastructure
echo "Starting Docker infrastructure..."
docker-compose up -d

# Wait for services
echo "Waiting for services to be healthy..."
sleep 120

# Check health
docker-compose ps

# Run performance tests
echo "Running performance tests..."
mvn test -Pperformance -Dtest=ThroughputTest

# Generate report
echo "Generating performance report..."
mvn surefire-report:report

# Cleanup
echo "Stopping infrastructure..."
docker-compose down

echo "========================================="
echo "Performance Tests Completed"
echo "View report: target/site/surefire-report.html"
echo "========================================="
