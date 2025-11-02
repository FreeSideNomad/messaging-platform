# Testing Guide

## Quick Start

### E2E Tests
```bash
./scripts/run-e2e-tests.sh
```

### Performance Tests
```bash
./scripts/run-performance-tests.sh
```

### Endurance Tests
```bash
./scripts/run-endurance-tests.sh
```

## Manual Execution

### Start Infrastructure
```bash
docker-compose up -d
sleep 120  # Wait for services
docker-compose ps  # Check health
```

### Run Tests
```bash
# E2E tests
mvn test -Pe2e

# Performance tests
mvn test -Pperformance

# Endurance tests
mvn test -Pendurance
```

### Stop Infrastructure
```bash
docker-compose down
```

## Docker Commands

Use the helper script for common Docker operations:

```bash
# Build all images
./scripts/docker-commands.sh build

# Start all services
./scripts/docker-commands.sh up

# Stop all services
./scripts/docker-commands.sh down

# Clean up (remove volumes)
./scripts/docker-commands.sh clean

# View logs
./scripts/docker-commands.sh logs [service-name]

# Check status
./scripts/docker-commands.sh ps

# Restart services
./scripts/docker-commands.sh restart [service-name]
```

## Architecture Overview

The Docker Compose setup includes:

### Infrastructure Services
- **PostgreSQL** (port 5432) - Database
- **IBM MQ** (ports 1414, 9443) - Message queue
- **Kafka** (port 9092) - Event streaming

### Application Services
- **API-1, API-2, API-3** - Three API instances (internal port 8080)
- **Worker-1, Worker-2, Worker-3** - Three worker instances (internal port 9090)
- **Nginx** (port 8080) - Load balancer in front of APIs

### Network
All services communicate via `messaging-network` bridge network.

## Test Categories

### Functional E2E Tests
Tests the complete flow from API through workers to database:
- **API → Worker → Database flow**: Submit command, verify processing, check database
- **Idempotency verification**: Duplicate keys are rejected
- **Load balancing validation**: Requests distributed across API instances
- **Worker pool distribution**: Work distributed across worker instances
- **Outbox pattern reliability**: Events reliably published

**Location**: `msg-platform-api/src/test/java/com/acme/reliable/e2e/FunctionalE2ETest.java`

### Performance Tests
Measures system throughput and latency:
- **500 TPS with 50 concurrent clients**
- **1000 TPS with 100 concurrent clients**
- **End-to-end processing verification**

**Location**: `msg-platform-api/src/test/java/com/acme/reliable/performance/ThroughputTest.java`

### Endurance Tests
Tests system stability under sustained load:
- **Sustained 200 TPS for 10 minutes**
- **Memory and resource leak detection**
- **Long-term database health**

**Location**: `msg-platform-api/src/test/java/com/acme/reliable/performance/EnduranceTest.java`

## Metrics & Success Criteria

### Performance Targets
- **Success Rate**: >98%
- **Throughput**: >400 TPS (for 500 TPS test)
- **Latency P50**: <100ms
- **Latency P95**: <200ms
- **Latency P99**: <500ms

### Monitoring During Tests

#### Watch Docker Stats
```bash
docker stats
```

#### Watch Database Connections
```bash
docker exec messaging-postgres psql -U postgres -d reliable -c \
  "SELECT COUNT(*) FROM pg_stat_activity WHERE datname='reliable';"
```

#### Watch Command Processing
```bash
docker exec messaging-postgres psql -U postgres -d reliable -c \
  "SELECT status, COUNT(*) FROM command GROUP BY status;"
```

#### Watch Outbox Status
```bash
docker exec messaging-postgres psql -U postgres -d reliable -c \
  "SELECT status, COUNT(*) FROM outbox GROUP BY status;"
```

#### Watch Worker Distribution
```bash
docker exec messaging-postgres psql -U postgres -d reliable -c \
  "SELECT claimed_by, COUNT(*) FROM outbox WHERE status='CLAIMED' GROUP BY claimed_by;"
```

#### Check Nginx Access Logs
```bash
docker exec messaging-nginx tail -f /var/log/nginx/access.log
```

#### Check MQ Queue Depth
```bash
echo "DISPLAY QLOCAL('APP.CMD.CreateUser.Q') CURDEPTH" | \
  docker exec -i messaging-ibmmq /opt/mqm/bin/runmqsc QM1
```

## Service Health Checks

### Check All Services
```bash
docker-compose ps
```

### Check Individual Service Health
```bash
# Nginx
curl http://localhost:8080/nginx-health

# API (via nginx)
curl http://localhost:8080/health

# PostgreSQL
docker exec messaging-postgres pg_isready -U postgres

# IBM MQ
docker exec messaging-ibmmq /opt/mqm/bin/dspmq

# Kafka
docker exec messaging-kafka kafka-broker-api-versions --bootstrap-server localhost:9092
```

## Troubleshooting

### Tests Failing to Connect
- Check all services are healthy: `docker-compose ps`
- Check logs: `docker-compose logs [service]`
- Increase wait time in scripts (default: 120s)

### Services Not Starting
- Check Docker resources (memory, CPU)
- Check port conflicts: `lsof -i :8080,5432,1414,9092`
- Clean up old containers: `./scripts/docker-commands.sh clean`

### Performance Degradation
- Check resource usage: `docker stats`
- Check database connections (see monitoring section)
- Review worker logs: `docker-compose logs worker-1 worker-2 worker-3`
- Check for errors in API logs: `docker-compose logs api-1 api-2 api-3`

### IBM MQ Issues
- Wait longer (MQ takes ~60s to start)
- Check MQ logs: `docker-compose logs ibmmq`
- Verify queue manager: `docker exec messaging-ibmmq /opt/mqm/bin/dspmq`

### Endurance Test Timeout
- Increase Maven timeout in pom.xml
- Check for memory leaks: `docker stats`
- Review database query performance
- Check thread pool exhaustion in logs

### Database Connection Issues
- Check connection pool settings in docker-compose.yml
- Verify database is healthy: `docker exec messaging-postgres pg_isready`
- Check active connections: See monitoring section

## Test Development

### Adding New E2E Tests

1. Extend `E2ETestBase` class
2. Add `@Tag("e2e")` annotation
3. Use `submitCommand()` helper method
4. Verify results in database using `dbConnection`
5. Use `waitForCommandStatus()` for async verification

Example:
```java
@Tag("e2e")
class MyE2ETest extends E2ETestBase {
    @Test
    void testMyFeature() throws Exception {
        String key = "e2e-test-" + UUID.randomUUID();
        HttpResponse<String> response = submitCommand("CreateUser", key, "{}");
        assertThat(response.statusCode()).isEqualTo(202);
        // Verify in database...
    }
}
```

### Adding New Performance Tests

1. Extend `PerformanceTestBase` class
2. Add `@Tag("performance")` annotation
3. Use `submitCommandsWithMetrics()` helper
4. Assert on `PerformanceMetrics` results

Example:
```java
@Tag("performance")
class MyPerfTest extends PerformanceTestBase {
    @Test
    void testHighLoad() throws Exception {
        PerformanceMetrics metrics = submitCommandsWithMetrics(1000, 50);
        assertThat(metrics.successRate()).isGreaterThan(0.98);
    }
}
```

## CI/CD Integration

The project includes GitHub Actions workflow at `.github/workflows/test.yml`:

- **E2E tests**: Run on every push and PR
- **Performance tests**: Run on main branch only
- **Test results**: Uploaded as artifacts

### Running Tests in CI
Tests run automatically on:
- Push to `main` or `develop` branches
- Pull requests to `main` branch

### Manual CI Trigger
Use GitHub Actions UI to manually trigger workflow.

## Performance Test Results

After running performance tests, view the report:
```bash
open target/site/surefire-report.html
```

After endurance tests, check collected metrics:
```bash
cat endurance-docker-stats.txt
cat endurance-logs.txt
```

## Clean Up

### Remove All Test Data
```bash
docker exec messaging-postgres psql -U postgres -d reliable -c \
  "DELETE FROM command WHERE idempotency_key LIKE 'e2e-test-%' OR idempotency_key LIKE 'perf-test-%' OR idempotency_key LIKE 'endurance-%';"
```

### Complete Clean Up
```bash
./scripts/docker-commands.sh clean
docker system prune -a --volumes -f
```

## Next Steps

1. **Implement E2E test classes** (FunctionalE2ETest.java)
2. **Implement performance test classes** (ThroughputTest.java)
3. **Implement endurance test class** (EnduranceTest.java)
4. **Add Maven profiles** for test execution
5. **Configure CI/CD** pipeline

---

**For More Information**:
- See `dev-plan-prompt.md` for detailed implementation plan
- See `README.md` for project overview
- See `QUICKSTART.md` for quick development setup
