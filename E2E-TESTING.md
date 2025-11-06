# E2E Testing Guide

This document explains how to run End-to-End (E2E) tests locally and in CI/CD.

## Overview

E2E tests verify the full application flow from API → Worker → Database. They require:
- PostgreSQL (database)
- IBM MQ (message queue)
- Kafka (event streaming)
- API application (msg-platform-api)

## Quick Start

### Option 1: Using the Helper Script (Recommended)

```bash
# Start E2E services
./scripts/e2e-services.sh start

# Run E2E tests
mvn test -Pe2e

# Stop E2E services
./scripts/e2e-services.sh stop
```

### Option 2: Manual Docker Compose

```bash
# Build application first
mvn clean package -DskipTests

# Start services
docker-compose -f docker-compose.e2e.yml up -d

# Wait for services to be ready
docker-compose -f docker-compose.e2e.yml ps

# Run E2E tests
mvn test -Pe2e

# Clean up
docker-compose -f docker-compose.e2e.yml down -v
```

## E2E Services Management

The `scripts/e2e-services.sh` script provides convenient commands:

### Start Services
```bash
./scripts/e2e-services.sh start
```
Starts all E2E infrastructure services and waits for them to be healthy.

### Stop Services
```bash
./scripts/e2e-services.sh stop
```
Stops and removes all E2E services and volumes (clean slate).

### Restart Services
```bash
./scripts/e2e-services.sh restart
```
Equivalent to stop + start.

### Check Status
```bash
./scripts/e2e-services.sh status
```
Shows current status of all E2E services.

### View Logs
```bash
# All services
./scripts/e2e-services.sh logs

# Specific service
./scripts/e2e-services.sh logs postgres
./scripts/e2e-services.sh logs ibmmq
./scripts/e2e-services.sh logs kafka
./scripts/e2e-services.sh logs api
```

## Service Endpoints

When E2E services are running, you can access:

| Service    | Endpoint                  | Credentials              |
|------------|---------------------------|--------------------------|
| PostgreSQL | localhost:5432            | postgres/postgres        |
| IBM MQ     | localhost:1414            | app/passw0rd             |
| IBM MQ Web | https://localhost:9443    | admin/passw0rd           |
| Kafka      | localhost:9092            | (no auth)                |
| API        | http://localhost:8080     | -                        |

## Docker Compose Files

### docker-compose.yml (Full Stack)
The main compose file for running the complete application stack including multiple workers. Used for:
- Local development
- Performance testing
- Load testing

### docker-compose.e2e.yml (E2E Testing)
Minimal compose file specifically for E2E tests. Includes:
- Infrastructure services (PostgreSQL, IBM MQ, Kafka)
- API application
- No persistent volumes (ephemeral data)
- Optimized for fast startup and teardown

**Key differences from main compose file:**
- Uses smaller resource limits
- No data persistence (no volumes)
- Faster health check intervals
- Only includes services needed for E2E tests
- Uses `e2e-` container name prefix to avoid conflicts

## Running E2E Tests

### All E2E Tests
```bash
mvn test -Pe2e
```

### Specific E2E Test
```bash
mvn test -Pe2e -Dtest=FunctionalE2ETest
```

### E2E Tests with Debug Output
```bash
mvn test -Pe2e -X
```

## CI/CD Integration

The GitHub Actions workflow (`.github/workflows/sonarcloud.yml`) automatically:

1. Builds the application
2. Starts E2E services using `docker-compose.e2e.yml`
3. Runs unit tests with coverage
4. Runs E2E tests
5. Performs SonarCloud analysis
6. Cleans up services (even on failure)

**Workflow steps:**
```yaml
- Build application (mvn package -DskipTests)
- Start E2E services (docker-compose up)
- Run unit tests (mvn test -Pcoverage)
- Run E2E tests (mvn test -Pe2e)
- SonarCloud analysis
- Stop services (docker-compose down -v)
```

## Troubleshooting

### Services not starting
```bash
# Check logs
./scripts/e2e-services.sh logs

# Or check specific service
docker logs e2e-postgres
docker logs e2e-ibmmq
docker logs e2e-kafka
docker logs e2e-api
```

### Port conflicts
If ports 5432, 1414, 9092, or 8080 are already in use:
```bash
# Find process using port
lsof -i :8080

# Kill process or stop other services first
./scripts/e2e-services.sh stop
```

### Tests timing out
E2E tests wait up to 3 minutes for the API to be ready. If tests timeout:
```bash
# Check API health
curl http://localhost:8080/health

# Check API logs
docker logs e2e-api

# Increase wait time in E2ETestBase.java if needed
```

### Database connection issues
```bash
# Verify PostgreSQL is running
docker exec -it e2e-postgres psql -U postgres -c '\l'

# Check database exists
docker exec -it e2e-postgres psql -U postgres -d reliable -c '\dt'
```

### IBM MQ connection issues
```bash
# Check MQ status
docker exec -it e2e-ibmmq dspmq

# Create required queues (if needed)
docker exec -i e2e-ibmmq /opt/mqm/bin/runmqsc QM1 <<EOF
DEFINE QLOCAL('APP.CMD.CreateUser.Q') DEFPSIST(YES) REPLACE
DEFINE QLOCAL('APP.CMD.REPLY.Q') DEFPSIST(YES) REPLACE
EOF
```

### Clean slate
If services are in a bad state, completely reset:
```bash
# Stop all E2E services
./scripts/e2e-services.sh stop

# Remove any orphaned containers
docker ps -a | grep e2e | awk '{print $1}' | xargs docker rm -f

# Start fresh
./scripts/e2e-services.sh start
```

## Best Practices

### Local Development
1. **Keep services running**: Start E2E services once and keep them running during development
2. **Run tests frequently**: Execute E2E tests after significant changes
3. **Clean up**: Stop services when done to free resources

### CI/CD
1. **Ephemeral environments**: Each CI/CD run gets fresh services
2. **Automatic cleanup**: Services always stopped, even on failure
3. **Fast feedback**: Separate unit and E2E test steps

### Writing E2E Tests
1. **Use unique test data**: Prefix with `e2e-test-` for easy cleanup
2. **Clean up after tests**: Tests should clean their own data
3. **Wait for async operations**: Use `waitForCommandStatus()` helper
4. **Tag tests properly**: Use `@Tag("e2e")` annotation

## Performance Considerations

### Local Testing
- E2E services use ~2GB RAM
- Startup time: ~60 seconds
- Recommended: Keep services running between test runs

### CI/CD
- Services start fresh on every run
- Adds ~2-3 minutes to build time
- Runs in parallel with other checks where possible

## Maven Profiles

### -Pcoverage
Runs tests with JaCoCo code coverage.
```bash
mvn test -Pcoverage
```

### -Pe2e
Runs only E2E tests (tagged with `@Tag("e2e")`).
```bash
mvn test -Pe2e
```

### Combined
Run both unit tests with coverage and E2E tests:
```bash
mvn test -Pcoverage
mvn test -Pe2e
```

## Further Reading

- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [JUnit 5 Tags](https://junit.org/junit5/docs/current/user-guide/#writing-tests-tagging-and-filtering)
- [GitHub Actions Docker](https://docs.github.com/en/actions/use-cases-and-examples/publishing-packages/publishing-docker-images)
