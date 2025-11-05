# Code Coverage Report - Platform CLI

## Current Coverage Status

### Overall Metrics
- **Line Coverage**: 11% (207 of 1,379 lines covered)
- **Branch Coverage**: 4% (16 of 321 branches covered)
- **Total Tests**: 79 tests passing, 6 skipped

### Package-Level Coverage

| Package | Line Coverage | Branch Coverage | Status |
|---------|---------------|-----------------|--------|
| com.acme.platform.cli.model | **100%** | n/a | ✅ Excellent |
| com.acme.platform.cli.config | **82%** | 66% | ✅ Good |
| com.acme.platform.cli | 24% | 0% | ⚠️ Needs Improvement |
| com.acme.platform.cli.service | 23% | 10% | ⚠️ Needs Improvement |
| com.acme.platform.cli.commands | 0% | 0% | ❌ No Coverage |
| com.acme.platform.cli.ui | 0% | 0% | ❌ No Coverage |

## Test Coverage by Component

### ✅ **Well Covered (>80%)**

1. **Model Classes** (100% coverage)
   - PaginatedResult - 10 tests
   - PaginatedResult.Pagination - Full coverage

2. **Configuration** (82% coverage)
   - CliConfiguration - 20 tests
   - All getters tested
   - Singleton pattern verified

### ⚠️ **Partially Covered (20-80%)**

3. **Service Inner Classes** (23% coverage)
   - ApiService.ApiResponse - 2 tests
   - MqService.QueueInfo - 5 tests
   - KafkaService.TopicInfo - 4 tests
   - KafkaService.ConsumerGroupLag - 4 tests
   - DockerService.ContainerInfo - 2 tests
   - DockerService.ContainerStats - 5 tests

4. **CLI Application** (24% coverage)
   - CliApplicationTest - 10 tests
   - Picocli command structure tested

### ❌ **Not Covered (0%)**

5. **Command Classes** (0% coverage - 19 classes)
   - DatabaseCommands (query, list, info)
   - ApiCommands (exec, get)
   - MqCommands (list, status)
   - KafkaCommands (topics, topic, lag)
   - DockerCommands (list, exec, logs, stats)

6. **UI Layer** (0% coverage - 1 class)
   - InteractiveMenu - Interactive terminal UI

7. **Service Main Methods** (Low coverage)
   - DatabaseService - Core JDBC logic not tested
   - ApiService - HTTP client logic not tested
   - MqService - RabbitMQ connections not tested
   - KafkaService - Kafka admin operations not tested
   - DockerService - Docker API calls not tested

## Why Current Coverage is Low

### Services (23% covered)
- Tests only cover **inner data classes** (ApiResponse, QueueInfo, etc.)
- Main service methods require **external dependencies**:
  - DatabaseService needs PostgreSQL
  - ApiService needs HTTP server
  - MqService needs RabbitMQ
  - KafkaService needs Kafka
  - DockerService needs Docker daemon

### Commands (0% covered)
- Command classes require **running services** to execute
- Picocli commands execute against real backends
- Not mocked or stubbed in current tests

### UI (0% covered)
- InteractiveMenu uses JLine3 terminal I/O
- Difficult to test without terminal emulation
- Typically excluded from coverage requirements

## Path to 80% Coverage

### Strategy 1: Integration Tests with Testcontainers
**Recommended for production quality**

Add integration tests using Testcontainers:
- PostgreSQL container for DatabaseService
- Mock HTTP server for ApiService
- RabbitMQ container for MqService
- Kafka container for KafkaService
- Docker-in-Docker for DockerService

**Estimated coverage gain**: +50-60%

### Strategy 2: Mock-Based Unit Tests
**Faster, but less confidence**

Mock external dependencies:
- Mock HikariCP/JDBC for DatabaseService
- Mock OkHttpClient for ApiService
- Mock RabbitMQ connections for MqService
- Mock Kafka AdminClient for KafkaService
- Mock Docker client for DockerService

**Estimated coverage gain**: +40-50%

### Strategy 3: Hybrid Approach (Recommended)
**Best balance of speed and confidence**

1. **Unit tests with mocks** for business logic (services)
2. **Integration tests** for critical paths (database, API)
3. **Exclude UI from coverage** (standard practice)
4. **Command classes**: Test via integration tests

**Estimated final coverage**: 75-85%

## Current Test Suite Summary

### Test Classes (9 total)
1. ✅ CliConfigurationTest (20 tests) - Config getters
2. ✅ PaginatedResultTest (10 tests) - Pagination model
3. ✅ ApiServiceTest (5 tests) - API response model
4. ✅ MqServiceTest (6 tests) - Queue info model
5. ✅ KafkaServiceTest (9 tests) - Topic/lag models
6. ✅ DockerServiceTest (10 tests) - Container models
7. ✅ DatabaseServiceTest (4 tests) - Validation only
8. ✅ CliApplicationTest (10 tests) - CLI structure
9. ⚠️ DatabaseIntegrationTest (5 tests, all skipped) - Requires RUN_INTEGRATION_TESTS=true

### Missing Test Coverage
- No tests for actual service operations
- No tests for command execution
- No tests for interactive UI
- Integration tests are disabled by default

## Recommendations

### Immediate Actions (to reach 80%)

1. **Enable Integration Tests**
   ```bash
   RUN_INTEGRATION_TESTS=true mvn test
   ```

2. **Add Service Integration Tests**
   - DatabaseService with Testcontainers PostgreSQL
   - ApiService with MockWebServer
   - MqService with Testcontainers RabbitMQ
   - KafkaService with Testcontainers Kafka

3. **Add Command Tests**
   - Test each Picocli command with mocked services
   - Verify command parsing and options
   - Test JSON vs table output formats

4. **Exclude UI from Coverage Requirements**
   ```xml
   <configuration>
       <excludes>
           <exclude>**/ui/**</exclude>
       </excludes>
   </configuration>
   ```

### Expected Final Coverage with Recommended Actions

| Component | Current | Target | Strategy |
|-----------|---------|--------|----------|
| Models | 100% | 100% | ✅ Complete |
| Config | 82% | 90% | Add error path tests |
| Services | 23% | 85% | Integration tests |
| Commands | 0% | 80% | Mocked service tests |
| UI | 0% | 0% | Excluded |
| **Overall** | **11%** | **80%+** | Hybrid approach |

## JaCoCo Configuration

Current POM configuration enforces 80% coverage:

```xml
<limits>
    <limit>
        <counter>LINE</counter>
        <value>COVEREDRATIO</value>
        <minimum>0.80</minimum>
    </limit>
    <limit>
        <counter>BRANCH</counter>
        <value>COVEREDRATIO</value>
        <minimum>0.80</minimum>
    </limit>
</limits>
```

**Note**: Currently this check will **FAIL** at 11% coverage.

## Running Coverage Report

```bash
# Run tests and generate report
mvn clean test jacoco:report

# View HTML report
open target/site/jacoco/index.html

# Check coverage threshold (will fail at <80%)
mvn verify
```

## Next Steps

1. Review this report with team
2. Decide on coverage strategy (Testcontainers vs Mocks)
3. Implement missing tests
4. Re-run coverage analysis
5. Adjust targets if UI/InteractiveMenu excluded

## Files
- HTML Report: `target/site/jacoco/index.html`
- XML Report: `target/site/jacoco/jacoco.xml`
- Coverage Data: `target/jacoco.exec`
