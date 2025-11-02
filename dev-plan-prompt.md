# Development Plan: Docker-Based Testing Infrastructure
## Messaging Platform v2.0 - Performance & E2E Testing Strategy

**Project**: messaging-platform
**Goal**: Build Docker-based testing infrastructure with nginx load balancer for e2e performance and endurance testing
**Reference**: Inspired by /Users/igormusic/code/ref-app/reliable-messaging performance testing capabilities

---

## Executive Summary

This plan outlines the implementation of a comprehensive Docker-based testing infrastructure for the messaging platform. The focus is on containerizing all components (API, Worker, PostgreSQL, IBM MQ, Kafka), adding nginx as a load balancer, and implementing e2e test scenarios that validate the entire system under load.

**Key Deliverables**:
1. Dockerfiles for API and Worker services
2. Docker Compose orchestration for entire platform
3. Nginx load balancer configuration
4. E2E test suite with database verification
5. Performance testing framework (throughput, latency, endurance)
6. Automated test execution scripts

---

## Phase 1: Dockerization of Services

### Objective
Create production-ready Docker images for API and Worker services.

### Tasks

#### 1.1 Create Dockerfile for msg-platform-api
**Location**: `msg-platform-api/Dockerfile`

```dockerfile
# Multi-stage build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy parent pom and module poms
COPY pom.xml .
COPY msg-platform-core/pom.xml msg-platform-core/
COPY msg-platform-messaging-ibmmq/pom.xml msg-platform-messaging-ibmmq/
COPY msg-platform-api/pom.xml msg-platform-api/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B -pl msg-platform-api -am

# Copy source code
COPY msg-platform-core/src msg-platform-core/src
COPY msg-platform-messaging-ibmmq/src msg-platform-messaging-ibmmq/src
COPY msg-platform-api/src msg-platform-api/src

# Build
RUN mvn clean package -DskipTests -pl msg-platform-api -am

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy JAR
COPY --from=build /app/msg-platform-api/target/msg-platform-api-*.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Run
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Build Command**:
```bash
docker build -f msg-platform-api/Dockerfile -t messaging-platform-api:latest .
```

#### 1.2 Create Dockerfile for msg-platform-worker
**Location**: `msg-platform-worker/Dockerfile`

```dockerfile
# Multi-stage build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy parent pom and module poms
COPY pom.xml .
COPY msg-platform-core/pom.xml msg-platform-core/
COPY msg-platform-messaging-ibmmq/pom.xml msg-platform-messaging-ibmmq/
COPY msg-platform-events-kafka/pom.xml msg-platform-events-kafka/
COPY msg-platform-worker/pom.xml msg-platform-worker/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B -pl msg-platform-worker -am

# Copy source code
COPY msg-platform-core/src msg-platform-core/src
COPY msg-platform-messaging-ibmmq/src msg-platform-messaging-ibmmq/src
COPY msg-platform-events-kafka/src msg-platform-events-kafka/src
COPY msg-platform-worker/src msg-platform-worker/src

# Build
RUN mvn clean package -DskipTests -pl msg-platform-worker -am

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy JAR
COPY --from=build /app/msg-platform-worker/target/msg-platform-worker-*.jar app.jar

# Expose port (variable per worker)
EXPOSE 9090

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${WORKER_PORT:-9090}/health || exit 1

# Run
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Build Command**:
```bash
docker build -f msg-platform-worker/Dockerfile -t messaging-platform-worker:latest .
```

#### 1.3 Create .dockerignore
**Location**: `.dockerignore`

```
target/
.git/
.gitignore
*.md
.env
.env.*
*.iml
.idea/
.vscode/
*.log
```

**Deliverables**:
- [ ] API Dockerfile created and tested
- [ ] Worker Dockerfile created and tested
- [ ] .dockerignore file created
- [ ] Build scripts validated
- [ ] Images successfully built locally

---

## Phase 2: Nginx Load Balancer Configuration

### Objective
Add nginx as a lightweight load balancer in front of the API to distribute traffic across multiple API instances.

### Tasks

#### 2.1 Create nginx Configuration
**Location**: `nginx/nginx.conf`

```nginx
events {
    worker_connections 4096;
}

http {
    upstream api_backend {
        # Round-robin load balancing
        least_conn;

        # API instances
        server api-1:8080 max_fails=3 fail_timeout=30s;
        server api-2:8080 max_fails=3 fail_timeout=30s;
        server api-3:8080 max_fails=3 fail_timeout=30s;

        # Keep-alive connections
        keepalive 32;
    }

    server {
        listen 80;
        server_name localhost;

        # Request buffering
        client_body_buffer_size 128k;
        client_max_body_size 10m;

        # Timeouts
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;

        # Logging
        access_log /var/log/nginx/access.log;
        error_log /var/log/nginx/error.log;

        # Health check endpoint (nginx itself)
        location /nginx-health {
            access_log off;
            return 200 "nginx ok\n";
            add_header Content-Type text/plain;
        }

        # Proxy to API backend
        location / {
            proxy_pass http://api_backend;

            # Headers
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;

            # Keep-alive
            proxy_http_version 1.1;
            proxy_set_header Connection "";

            # Buffering
            proxy_buffering on;
            proxy_buffer_size 4k;
            proxy_buffers 8 4k;
        }

        # API health checks (pass-through)
        location /health {
            proxy_pass http://api_backend/health;
            proxy_set_header Host $host;
        }
    }
}
```

#### 2.2 Create nginx Dockerfile
**Location**: `nginx/Dockerfile`

```dockerfile
FROM nginx:1.25-alpine

# Copy custom configuration
COPY nginx/nginx.conf /etc/nginx/nginx.conf

# Expose port
EXPOSE 80

# Health check
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost/nginx-health || exit 1
```

**Deliverables**:
- [ ] nginx.conf created with load balancing config
- [ ] nginx Dockerfile created
- [ ] Health check endpoint configured
- [ ] Load balancing strategy validated

---

## Phase 3: Docker Compose Orchestration

### Objective
Create a complete Docker Compose setup that orchestrates all services: PostgreSQL, IBM MQ, Kafka, 3x API instances, 3x Worker instances, and nginx load balancer.

### Tasks

#### 3.1 Create Main Docker Compose File
**Location**: `docker-compose.yml`

```yaml
version: '3.8'

services:
  # PostgreSQL Database
  postgres:
    image: postgres:16-alpine
    container_name: messaging-postgres
    environment:
      POSTGRES_DB: reliable
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - messaging-network

  # IBM MQ
  ibmmq:
    image: icr.io/ibm-messaging/mq:latest
    container_name: messaging-ibmmq
    environment:
      LICENSE: accept
      MQ_QMGR_NAME: QM1
      MQ_APP_PASSWORD: passw0rd
      MQ_ADMIN_PASSWORD: passw0rd
    ports:
      - "1414:1414"
      - "9443:9443"
    volumes:
      - ibmmq_data:/mnt/mqm
    healthcheck:
      test: ["CMD", "/opt/mqm/bin/dspmq"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
    networks:
      - messaging-network

  # Kafka
  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: messaging-kafka
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_LOG_DIRS: /var/lib/kafka/data
      CLUSTER_ID: messaging-cluster-001
    ports:
      - "9092:9092"
    volumes:
      - kafka_data:/var/lib/kafka/data
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9092"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
    networks:
      - messaging-network

  # API Instance 1
  api-1:
    build:
      context: .
      dockerfile: msg-platform-api/Dockerfile
    container_name: messaging-api-1
    environment:
      API_PORT: 8080
      NETTY_WORKER_THREADS: 200
      HIKARI_MAX_POOL_SIZE: 100
      JMS_CONSUMERS_ENABLED: false
      SYNC_WAIT_DURATION: 0s
      DATASOURCES_DEFAULT_URL: jdbc:postgresql://postgres:5432/reliable
      DATASOURCES_DEFAULT_USERNAME: postgres
      DATASOURCES_DEFAULT_PASSWORD: postgres
      IBM_MQ_QUEUEMANAGER: QM1
      IBM_MQ_CHANNEL: DEV.APP.SVRCONN
      IBM_MQ_CONNNAME: ibmmq(1414)
      IBM_MQ_USER: app
      IBM_MQ_PASSWORD: passw0rd
    depends_on:
      postgres:
        condition: service_healthy
      ibmmq:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:8080/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 90s
    networks:
      - messaging-network

  # API Instance 2
  api-2:
    build:
      context: .
      dockerfile: msg-platform-api/Dockerfile
    container_name: messaging-api-2
    environment:
      API_PORT: 8080
      NETTY_WORKER_THREADS: 200
      HIKARI_MAX_POOL_SIZE: 100
      JMS_CONSUMERS_ENABLED: false
      SYNC_WAIT_DURATION: 0s
      DATASOURCES_DEFAULT_URL: jdbc:postgresql://postgres:5432/reliable
      DATASOURCES_DEFAULT_USERNAME: postgres
      DATASOURCES_DEFAULT_PASSWORD: postgres
      IBM_MQ_QUEUEMANAGER: QM1
      IBM_MQ_CHANNEL: DEV.APP.SVRCONN
      IBM_MQ_CONNNAME: ibmmq(1414)
      IBM_MQ_USER: app
      IBM_MQ_PASSWORD: passw0rd
    depends_on:
      postgres:
        condition: service_healthy
      ibmmq:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:8080/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 90s
    networks:
      - messaging-network

  # API Instance 3
  api-3:
    build:
      context: .
      dockerfile: msg-platform-api/Dockerfile
    container_name: messaging-api-3
    environment:
      API_PORT: 8080
      NETTY_WORKER_THREADS: 200
      HIKARI_MAX_POOL_SIZE: 100
      JMS_CONSUMERS_ENABLED: false
      SYNC_WAIT_DURATION: 0s
      DATASOURCES_DEFAULT_URL: jdbc:postgresql://postgres:5432/reliable
      DATASOURCES_DEFAULT_USERNAME: postgres
      DATASOURCES_DEFAULT_PASSWORD: postgres
      IBM_MQ_QUEUEMANAGER: QM1
      IBM_MQ_CHANNEL: DEV.APP.SVRCONN
      IBM_MQ_CONNNAME: ibmmq(1414)
      IBM_MQ_USER: app
      IBM_MQ_PASSWORD: passw0rd
    depends_on:
      postgres:
        condition: service_healthy
      ibmmq:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:8080/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 90s
    networks:
      - messaging-network

  # Worker Instance 1
  worker-1:
    build:
      context: .
      dockerfile: msg-platform-worker/Dockerfile
    container_name: messaging-worker-1
    environment:
      WORKER_ID: worker-1
      WORKER_PORT: 9090
      NETTY_WORKER_THREADS: 50
      HIKARI_MAX_POOL_SIZE: 200
      JMS_CONSUMERS_ENABLED: true
      JMS_CONCURRENCY: 10
      OUTBOX_SWEEP_INTERVAL: 1s
      DATASOURCES_DEFAULT_URL: jdbc:postgresql://postgres:5432/reliable
      DATASOURCES_DEFAULT_USERNAME: postgres
      DATASOURCES_DEFAULT_PASSWORD: postgres
      IBM_MQ_QUEUEMANAGER: QM1
      IBM_MQ_CHANNEL: DEV.APP.SVRCONN
      IBM_MQ_CONNNAME: ibmmq(1414)
      IBM_MQ_USER: app
      IBM_MQ_PASSWORD: passw0rd
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    depends_on:
      postgres:
        condition: service_healthy
      ibmmq:
        condition: service_healthy
      kafka:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:9090/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 90s
    networks:
      - messaging-network

  # Worker Instance 2
  worker-2:
    build:
      context: .
      dockerfile: msg-platform-worker/Dockerfile
    container_name: messaging-worker-2
    environment:
      WORKER_ID: worker-2
      WORKER_PORT: 9090
      NETTY_WORKER_THREADS: 50
      HIKARI_MAX_POOL_SIZE: 200
      JMS_CONSUMERS_ENABLED: true
      JMS_CONCURRENCY: 10
      OUTBOX_SWEEP_INTERVAL: 1s
      DATASOURCES_DEFAULT_URL: jdbc:postgresql://postgres:5432/reliable
      DATASOURCES_DEFAULT_USERNAME: postgres
      DATASOURCES_DEFAULT_PASSWORD: postgres
      IBM_MQ_QUEUEMANAGER: QM1
      IBM_MQ_CHANNEL: DEV.APP.SVRCONN
      IBM_MQ_CONNNAME: ibmmq(1414)
      IBM_MQ_USER: app
      IBM_MQ_PASSWORD: passw0rd
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    depends_on:
      postgres:
        condition: service_healthy
      ibmmq:
        condition: service_healthy
      kafka:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:9090/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 90s
    networks:
      - messaging-network

  # Worker Instance 3
  worker-3:
    build:
      context: .
      dockerfile: msg-platform-worker/Dockerfile
    container_name: messaging-worker-3
    environment:
      WORKER_ID: worker-3
      WORKER_PORT: 9090
      NETTY_WORKER_THREADS: 50
      HIKARI_MAX_POOL_SIZE: 200
      JMS_CONSUMERS_ENABLED: true
      JMS_CONCURRENCY: 10
      OUTBOX_SWEEP_INTERVAL: 1s
      DATASOURCES_DEFAULT_URL: jdbc:postgresql://postgres:5432/reliable
      DATASOURCES_DEFAULT_USERNAME: postgres
      DATASOURCES_DEFAULT_PASSWORD: postgres
      IBM_MQ_QUEUEMANAGER: QM1
      IBM_MQ_CHANNEL: DEV.APP.SVRCONN
      IBM_MQ_CONNNAME: ibmmq(1414)
      IBM_MQ_USER: app
      IBM_MQ_PASSWORD: passw0rd
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    depends_on:
      postgres:
        condition: service_healthy
      ibmmq:
        condition: service_healthy
      kafka:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:9090/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 90s
    networks:
      - messaging-network

  # Nginx Load Balancer
  nginx:
    build:
      context: .
      dockerfile: nginx/Dockerfile
    container_name: messaging-nginx
    ports:
      - "8080:80"
    depends_on:
      api-1:
        condition: service_healthy
      api-2:
        condition: service_healthy
      api-3:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost/nginx-health"]
      interval: 10s
      timeout: 3s
      retries: 3
      start_period: 20s
    networks:
      - messaging-network

networks:
  messaging-network:
    driver: bridge

volumes:
  postgres_data:
  ibmmq_data:
  kafka_data:
```

#### 3.2 Create Docker Compose Commands Script
**Location**: `scripts/docker-commands.sh`

```bash
#!/bin/bash
set -e

case "$1" in
  build)
    echo "Building all images..."
    docker-compose build --no-cache
    ;;
  up)
    echo "Starting all services..."
    docker-compose up -d
    echo "Waiting for services to be healthy (90s)..."
    sleep 90
    docker-compose ps
    ;;
  down)
    echo "Stopping all services..."
    docker-compose down
    ;;
  clean)
    echo "Stopping and removing all containers, networks, and volumes..."
    docker-compose down -v
    ;;
  logs)
    docker-compose logs -f ${2:-}
    ;;
  ps)
    docker-compose ps
    ;;
  restart)
    echo "Restarting services..."
    docker-compose restart ${2:-}
    ;;
  *)
    echo "Usage: $0 {build|up|down|clean|logs|ps|restart}"
    exit 1
    ;;
esac
```

**Deliverables**:
- [ ] docker-compose.yml created with all services
- [ ] Health checks configured for all services
- [ ] Environment variables properly configured
- [ ] Networks and volumes defined
- [ ] Docker commands script created
- [ ] Successfully start entire stack

---

## Phase 4: E2E Test Framework

### Objective
Build comprehensive E2E test suite that validates API → Worker → Database flow with proof-of-work verification.

### Tasks

#### 4.1 Create E2E Test Base Class
**Location**: `msg-platform-api/src/test/java/com/acme/reliable/e2e/E2ETestBase.java`

```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("e2e")
public abstract class E2ETestBase {

    protected HttpClient httpClient;
    protected Connection dbConnection;

    protected static final String API_BASE_URL = "http://localhost:8080";
    protected static final String DB_URL = "jdbc:postgresql://localhost:5432/reliable";
    protected static final String DB_USER = "postgres";
    protected static final String DB_PASSWORD = "postgres";

    @BeforeAll
    void setUp() throws Exception {
        // Wait for services
        waitForService(API_BASE_URL + "/health", Duration.ofMinutes(3));

        // Setup HTTP client
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        // Setup DB connection
        dbConnection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

        // Clean test data
        cleanupTestData();
    }

    @AfterAll
    void tearDown() throws Exception {
        if (dbConnection != null) {
            dbConnection.close();
        }
    }

    protected void waitForService(String url, Duration timeout) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
                HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (Exception e) {
                Thread.sleep(5000);
            }
        }
        throw new TimeoutException("Service not available: " + url);
    }

    protected HttpResponse<String> submitCommand(String commandName,
                                                   String idempotencyKey,
                                                   String payload) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_BASE_URL + "/commands/" + commandName))
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", idempotencyKey)
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected void cleanupTestData() throws Exception {
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.execute("DELETE FROM command WHERE idempotency_key LIKE 'e2e-test-%'");
            stmt.execute("DELETE FROM outbox WHERE key LIKE 'e2e-test-%'");
            stmt.execute("DELETE FROM inbox WHERE message_id LIKE 'e2e-test-%'");
        }
    }

    protected void waitForCommandStatus(UUID commandId, String expectedStatus,
                                         Duration timeout) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            try (PreparedStatement ps = dbConnection.prepareStatement(
                "SELECT status FROM command WHERE id = ?::uuid")) {
                ps.setString(1, commandId.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String status = rs.getString("status");
                    if (expectedStatus.equals(status)) {
                        return;
                    }
                }
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Command did not reach expected status: " + expectedStatus);
    }
}
```

#### 4.2 Create E2E Functional Tests
**Location**: `msg-platform-api/src/test/java/com/acme/reliable/e2e/FunctionalE2ETest.java`

```java
@Tag("e2e")
class FunctionalE2ETest extends E2ETestBase {

    @Test
    @DisplayName("E2E: Submit command → Worker processes → Database updated")
    void testFullFlow_ApiToWorkerToDatabase() throws Exception {
        // Given
        String idempotencyKey = "e2e-test-" + UUID.randomUUID();
        String payload = "{\"username\":\"testuser\"}";

        // When - Submit command via API
        HttpResponse<String> response = submitCommand("CreateUser", idempotencyKey, payload);

        // Then - Should return 202 Accepted
        assertThat(response.statusCode()).isEqualTo(202);
        String commandId = response.headers().firstValue("X-Command-Id").orElseThrow();

        // And - Command should be created in PENDING status
        try (PreparedStatement ps = dbConnection.prepareStatement(
            "SELECT status, name, business_key FROM command WHERE id = ?::uuid")) {
            ps.setString(1, commandId);
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("status")).isEqualTo("PENDING");
            assertThat(rs.getString("name")).isEqualTo("CreateUser");
        }

        // And - Command should be processed by worker within 10s
        waitForCommandStatus(UUID.fromString(commandId), "SUCCEEDED", Duration.ofSeconds(10));

        // And - Outbox should have reply and event
        try (PreparedStatement ps = dbConnection.prepareStatement(
            "SELECT category, status FROM outbox WHERE payload::text LIKE ? ORDER BY created_at")) {
            ps.setString(1, "%testuser%");
            ResultSet rs = ps.executeQuery();

            List<String> categories = new ArrayList<>();
            while (rs.next()) {
                categories.add(rs.getString("category"));
            }

            assertThat(categories).contains("reply", "event");
        }
    }

    @Test
    @DisplayName("E2E: Idempotency - Duplicate keys rejected")
    void testIdempotency_DuplicateKeysRejected() throws Exception {
        // Given - Submit first command
        String idempotencyKey = "e2e-test-" + UUID.randomUUID();
        HttpResponse<String> response1 = submitCommand("CreateUser", idempotencyKey, "{}");
        assertThat(response1.statusCode()).isEqualTo(202);

        // When - Submit duplicate
        HttpResponse<String> response2 = submitCommand("CreateUser", idempotencyKey, "{}");

        // Then - Should return 409 Conflict
        assertThat(response2.statusCode()).isEqualTo(409);

        // And - Only one command in database
        try (PreparedStatement ps = dbConnection.prepareStatement(
            "SELECT COUNT(*) FROM command WHERE idempotency_key = ?")) {
            ps.setString(1, idempotencyKey);
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("E2E: Load balancing - Multiple API instances handle requests")
    void testLoadBalancing_RequestsDistributed() throws Exception {
        // Given - Submit 30 commands rapidly
        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String key = "e2e-test-lb-" + System.currentTimeMillis() + "-" + i;
            CompletableFuture<HttpResponse<String>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return submitCommand("CreateUser", key, "{\"username\":\"user" + key + "\"}");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }

        // When - Wait for all
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Then - All should succeed (load distributed by nginx)
        long successCount = futures.stream()
            .map(CompletableFuture::join)
            .filter(r -> r.statusCode() == 202)
            .count();

        assertThat(successCount).isEqualTo(30);

        // And - All processed within reasonable time
        Thread.sleep(15000); // Wait for processing

        try (PreparedStatement ps = dbConnection.prepareStatement(
            "SELECT COUNT(*) FROM command WHERE idempotency_key LIKE 'e2e-test-lb-%' AND status = 'SUCCEEDED'")) {
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(30);
        }
    }

    @Test
    @DisplayName("E2E: Worker pool - Multiple workers process in parallel")
    void testWorkerPool_ParallelProcessing() throws Exception {
        // Given - Submit 50 commands
        List<String> commandIds = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            String key = "e2e-test-worker-" + System.currentTimeMillis() + "-" + i;
            HttpResponse<String> response = submitCommand("CreateUser", key, "{}");
            String commandId = response.headers().firstValue("X-Command-Id").orElseThrow();
            commandIds.add(commandId);
        }

        // When - Wait for processing
        Thread.sleep(20000);

        // Then - All commands should be processed
        try (PreparedStatement ps = dbConnection.prepareStatement(
            "SELECT COUNT(*) FROM command WHERE idempotency_key LIKE 'e2e-test-worker-%' AND status = 'SUCCEEDED'")) {
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(50);
        }

        // And - Work distributed across workers
        try (PreparedStatement ps = dbConnection.prepareStatement(
            "SELECT claimed_by, COUNT(*) as cnt FROM outbox " +
            "WHERE key LIKE 'e2e-test-worker-%' " +
            "GROUP BY claimed_by")) {
            ResultSet rs = ps.executeQuery();

            Map<String, Integer> workerDistribution = new HashMap<>();
            while (rs.next()) {
                String worker = rs.getString("claimed_by");
                int count = rs.getInt("cnt");
                workerDistribution.put(worker, count);
            }

            // Should have work distributed (at least 2 workers used)
            assertThat(workerDistribution.size()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    @DisplayName("E2E: Outbox pattern - Reliable event publishing")
    void testOutboxPattern_ReliablePublishing() throws Exception {
        // Given
        String key = "e2e-test-outbox-" + UUID.randomUUID();
        HttpResponse<String> response = submitCommand("CreateUser", key, "{\"username\":\"outboxtest\"}");
        String commandId = response.headers().firstValue("X-Command-Id").orElseThrow();

        // When - Wait for processing
        waitForCommandStatus(UUID.fromString(commandId), "SUCCEEDED", Duration.ofSeconds(10));

        // Then - Outbox entries should be published
        try (PreparedStatement ps = dbConnection.prepareStatement(
            "SELECT id, category, status, topic, published_at FROM outbox " +
            "WHERE payload::text LIKE ? ORDER BY created_at")) {
            ps.setString(1, "%outboxtest%");
            ResultSet rs = ps.executeQuery();

            int publishedCount = 0;
            while (rs.next()) {
                String status = rs.getString("status");
                Timestamp publishedAt = rs.getTimestamp("published_at");

                assertThat(status).isIn("PUBLISHED", "NEW", "CLAIMED");
                if ("PUBLISHED".equals(status)) {
                    assertThat(publishedAt).isNotNull();
                    publishedCount++;
                }
            }

            // At least reply should be published
            assertThat(publishedCount).isGreaterThanOrEqualTo(1);
        }
    }
}
```

**Deliverables**:
- [ ] E2ETestBase class created
- [ ] Functional E2E tests implemented
- [ ] Database verification included
- [ ] Load balancing validated
- [ ] Worker pool distribution verified
- [ ] All E2E tests passing

---

## Phase 5: Performance Testing Framework

### Objective
Build performance testing framework to measure throughput, latency, and endurance under sustained load.

### Tasks

#### 5.1 Create Performance Test Base Class
**Location**: `msg-platform-api/src/test/java/com/acme/reliable/performance/PerformanceTestBase.java`

```java
@Tag("performance")
public abstract class PerformanceTestBase extends E2ETestBase {

    protected PerformanceMetrics submitCommandsWithMetrics(int totalRequests,
                                                            int concurrency) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch latch = new CountDownLatch(totalRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalRequests; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    long reqStart = System.nanoTime();
                    String key = "perf-test-" + System.currentTimeMillis() + "-" + index;
                    HttpResponse<String> response = submitCommand("CreateUser", key, "{}");
                    long reqEnd = System.nanoTime();

                    latencies.add((reqEnd - reqStart) / 1_000_000); // ms

                    if (response.statusCode() == 202) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.MINUTES);

        long endTime = System.currentTimeMillis();
        long durationMs = endTime - startTime;

        Collections.sort(latencies);

        return new PerformanceMetrics(
            totalRequests,
            successCount.get(),
            failureCount.get(),
            durationMs,
            calculatePercentile(latencies, 0.50),
            calculatePercentile(latencies, 0.95),
            calculatePercentile(latencies, 0.99),
            calculateAverage(latencies)
        );
    }

    private long calculatePercentile(List<Long> values, double percentile) {
        if (values.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile * values.size()) - 1;
        return values.get(Math.max(0, index));
    }

    private double calculateAverage(List<Long> values) {
        if (values.isEmpty()) return 0;
        return values.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    protected record PerformanceMetrics(
        int totalRequests,
        int successCount,
        int failureCount,
        long durationMs,
        long p50LatencyMs,
        long p95LatencyMs,
        long p99LatencyMs,
        double avgLatencyMs
    ) {
        public double throughputTPS() {
            return (double) totalRequests / (durationMs / 1000.0);
        }

        public double successRate() {
            return (double) successCount / totalRequests;
        }

        @Override
        public String toString() {
            return String.format(
                "Performance Metrics:%n" +
                "  Total Requests: %d%n" +
                "  Success: %d (%.2f%%)%n" +
                "  Failures: %d%n" +
                "  Duration: %d ms%n" +
                "  Throughput: %.2f TPS%n" +
                "  Latency - P50: %d ms, P95: %d ms, P99: %d ms, Avg: %.2f ms",
                totalRequests, successCount, successRate() * 100, failureCount,
                durationMs, throughputTPS(),
                p50LatencyMs, p95LatencyMs, p99LatencyMs, avgLatencyMs
            );
        }
    }
}
```

#### 5.2 Create Throughput Performance Tests
**Location**: `msg-platform-api/src/test/java/com/acme/reliable/performance/ThroughputTest.java`

```java
@Tag("performance")
class ThroughputTest extends PerformanceTestBase {

    @Test
    @DisplayName("PERF: API should handle 500 TPS with 50 concurrent clients")
    void testThroughput_500TPS_50Concurrent() throws Exception {
        // Given
        int totalRequests = 5000;
        int concurrency = 50;

        // When
        PerformanceMetrics metrics = submitCommandsWithMetrics(totalRequests, concurrency);

        // Then - Print metrics
        System.out.println(metrics);

        // Assertions
        assertThat(metrics.successRate()).isGreaterThan(0.99); // >99% success
        assertThat(metrics.throughputTPS()).isGreaterThan(400); // >400 TPS
        assertThat(metrics.p95LatencyMs()).isLessThan(200); // P95 < 200ms
        assertThat(metrics.p99LatencyMs()).isLessThan(500); // P99 < 500ms
    }

    @Test
    @DisplayName("PERF: API should handle 1000 TPS with 100 concurrent clients")
    void testThroughput_1000TPS_100Concurrent() throws Exception {
        // Given
        int totalRequests = 10000;
        int concurrency = 100;

        // When
        PerformanceMetrics metrics = submitCommandsWithMetrics(totalRequests, concurrency);

        // Then
        System.out.println(metrics);

        assertThat(metrics.successRate()).isGreaterThan(0.98); // >98% success
        assertThat(metrics.throughputTPS()).isGreaterThan(800); // >800 TPS
        assertThat(metrics.p95LatencyMs()).isLessThan(300); // P95 < 300ms
    }

    @Test
    @DisplayName("PERF: System should process all commands end-to-end")
    void testEndToEndProcessing_AllCommandsCompleted() throws Exception {
        // Given - Submit 1000 commands
        int commandCount = 1000;
        PerformanceMetrics submitMetrics = submitCommandsWithMetrics(commandCount, 50);

        System.out.println("Submit Metrics:");
        System.out.println(submitMetrics);

        // When - Wait for processing (with timeout)
        Thread.sleep(60000); // 60s for workers to process

        // Then - All should be processed
        try (PreparedStatement ps = dbConnection.prepareStatement(
            "SELECT status, COUNT(*) as cnt FROM command " +
            "WHERE idempotency_key LIKE 'perf-test-%' " +
            "GROUP BY status")) {
            ResultSet rs = ps.executeQuery();

            Map<String, Integer> statusCounts = new HashMap<>();
            while (rs.next()) {
                statusCounts.put(rs.getString("status"), rs.getInt("cnt"));
            }

            System.out.println("Command Status Distribution: " + statusCounts);

            int succeededCount = statusCounts.getOrDefault("SUCCEEDED", 0);
            assertThat(succeededCount).isGreaterThan((int) (commandCount * 0.95)); // >95% succeeded
        }
    }
}
```

#### 5.3 Create Endurance Test
**Location**: `msg-platform-api/src/test/java/com/acme/reliable/performance/EnduranceTest.java`

```java
@Tag("endurance")
class EnduranceTest extends PerformanceTestBase {

    @Test
    @DisplayName("ENDURANCE: System should handle sustained 200 TPS for 10 minutes")
    void testEndurance_200TPS_10Minutes() throws Exception {
        // Given
        int targetTPS = 200;
        int durationMinutes = 10;
        int totalRequests = targetTPS * durationMinutes * 60;

        System.out.println("Starting endurance test: " + totalRequests + " requests over " +
                           durationMinutes + " minutes");

        // When - Submit at steady rate
        ExecutorService executor = Executors.newFixedThreadPool(20);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

        long startTime = System.currentTimeMillis();
        long intervalMs = 1000 / targetTPS; // Time between requests

        for (int i = 0; i < totalRequests; i++) {
            final int index = i;
            long expectedTime = startTime + (index * intervalMs);

            executor.submit(() -> {
                try {
                    // Wait until expected time (rate limiting)
                    long now = System.currentTimeMillis();
                    if (now < expectedTime) {
                        Thread.sleep(expectedTime - now);
                    }

                    long reqStart = System.nanoTime();
                    String key = "endurance-" + System.currentTimeMillis() + "-" + index;
                    HttpResponse<String> response = submitCommand("CreateUser", key, "{}");
                    long reqEnd = System.nanoTime();

                    latencies.add((reqEnd - reqStart) / 1_000_000);

                    if (response.statusCode() == 202) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }

                    // Log progress every 1000 requests
                    if (index % 1000 == 0) {
                        System.out.printf("Progress: %d/%d (%.1f%%)%n",
                            index, totalRequests, (double) index / totalRequests * 100);
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(durationMinutes + 5, TimeUnit.MINUTES);

        long endTime = System.currentTimeMillis();
        long durationMs = endTime - startTime;

        // Then - Calculate metrics
        Collections.sort(latencies);
        PerformanceMetrics metrics = new PerformanceMetrics(
            totalRequests,
            successCount.get(),
            failureCount.get(),
            durationMs,
            calculatePercentile(latencies, 0.50),
            calculatePercentile(latencies, 0.95),
            calculatePercentile(latencies, 0.99),
            calculateAverage(latencies)
        );

        System.out.println("Endurance Test Results:");
        System.out.println(metrics);

        // Assertions
        assertThat(metrics.successRate()).isGreaterThan(0.98); // >98% success over 10 min
        assertThat(metrics.throughputTPS()).isGreaterThan(targetTPS * 0.9); // Within 90% of target
        assertThat(metrics.p99LatencyMs()).isLessThan(1000); // P99 < 1s

        // Verify database health
        try (PreparedStatement ps = dbConnection.prepareStatement(
            "SELECT COUNT(*) FROM command WHERE idempotency_key LIKE 'endurance-%'")) {
            ResultSet rs = ps.executeQuery();
            rs.next();
            int dbCount = rs.getInt(1);
            System.out.println("Commands in database: " + dbCount);
            assertThat(dbCount).isGreaterThan((int) (totalRequests * 0.95));
        }
    }

    private long calculatePercentile(List<Long> values, double percentile) {
        if (values.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile * values.size()) - 1;
        return values.get(Math.max(0, index));
    }

    private double calculateAverage(List<Long> values) {
        if (values.isEmpty()) return 0;
        return values.stream().mapToLong(Long::longValue).average().orElse(0);
    }
}
```

**Deliverables**:
- [ ] PerformanceTestBase class created
- [ ] Throughput tests (500 TPS, 1000 TPS)
- [ ] Endurance test (10 min sustained load)
- [ ] Metrics collection (latency, throughput, success rate)
- [ ] Database verification for completed processing
- [ ] Performance tests passing with acceptable metrics

---

## Phase 6: Test Automation & Execution Scripts

### Objective
Create automated scripts for running different test suites and generating reports.

### Tasks

#### 6.1 Create Test Execution Scripts

**Location**: `scripts/run-e2e-tests.sh`

```bash
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
```

**Location**: `scripts/run-performance-tests.sh`

```bash
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
```

**Location**: `scripts/run-endurance-tests.sh`

```bash
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
```

#### 6.2 Create Maven Profiles

Add to `pom.xml`:

```xml
<profiles>
    <!-- E2E Testing Profile -->
    <profile>
        <id>e2e</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <configuration>
                        <groups>e2e</groups>
                        <excludedGroups>performance,endurance</excludedGroups>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>

    <!-- Performance Testing Profile -->
    <profile>
        <id>performance</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <configuration>
                        <groups>performance</groups>
                        <excludedGroups>e2e,endurance</excludedGroups>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>

    <!-- Endurance Testing Profile -->
    <profile>
        <id>endurance</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <configuration>
                        <groups>endurance</groups>
                        <excludedGroups>e2e,performance</excludedGroups>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

#### 6.3 Create README for Testing

**Location**: `TESTING.md`

```markdown
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

## Test Categories

### Functional E2E Tests
- API → Worker → Database flow
- Idempotency verification
- Load balancing validation
- Worker pool distribution
- Outbox pattern reliability

### Performance Tests
- 500 TPS with 50 concurrent clients
- 1000 TPS with 100 concurrent clients
- End-to-end processing verification

### Endurance Tests
- Sustained 200 TPS for 10 minutes
- System stability under prolonged load
- Memory and resource leak detection

## Metrics

### Success Criteria
- **Success Rate**: >98%
- **Throughput**: >400 TPS (for 500 TPS test)
- **Latency P95**: <200ms
- **Latency P99**: <500ms

### Monitoring During Tests
```bash
# Watch Docker stats
docker stats

# Watch database connections
docker exec messaging-postgres psql -U postgres -d reliable -c \
  "SELECT COUNT(*) FROM pg_stat_activity WHERE datname='reliable';"

# Watch command processing
docker exec messaging-postgres psql -U postgres -d reliable -c \
  "SELECT status, COUNT(*) FROM command GROUP BY status;"
```

## Troubleshooting

### Tests Failing to Connect
- Check all services are healthy: `docker-compose ps`
- Check logs: `docker-compose logs [service]`
- Increase wait time in scripts

### Performance Degradation
- Check resource usage: `docker stats`
- Check database connections
- Review worker logs for errors

### Endurance Test Timeout
- Increase Maven timeout
- Check for memory leaks
- Review database query performance
```

**Deliverables**:
- [ ] E2E test execution script created
- [ ] Performance test execution script created
- [ ] Endurance test execution script created
- [ ] Maven profiles configured
- [ ] TESTING.md documentation created
- [ ] All scripts tested and working

---

## Phase 7: CI/CD Integration (Optional)

### Objective
Integrate testing into CI/CD pipeline (GitHub Actions / Jenkins).

### Tasks

#### 7.1 Create GitHub Actions Workflow

**Location**: `.github/workflows/test.yml`

```yaml
name: Testing Pipeline

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  e2e-tests:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v3

    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: maven

    - name: Build Docker images
      run: docker-compose build

    - name: Start infrastructure
      run: docker-compose up -d

    - name: Wait for services
      run: sleep 120

    - name: Check service health
      run: docker-compose ps

    - name: Run E2E tests
      run: mvn test -Pe2e

    - name: Stop infrastructure
      if: always()
      run: docker-compose down

    - name: Upload test results
      if: always()
      uses: actions/upload-artifact@v3
      with:
        name: e2e-test-results
        path: '**/target/surefire-reports/*.xml'

  performance-tests:
    runs-on: ubuntu-latest
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'

    steps:
    - uses: actions/checkout@v3

    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: maven

    - name: Build Docker images
      run: docker-compose build

    - name: Start infrastructure
      run: docker-compose up -d

    - name: Wait for services
      run: sleep 120

    - name: Run performance tests
      run: mvn test -Pperformance

    - name: Stop infrastructure
      if: always()
      run: docker-compose down

    - name: Upload performance results
      if: always()
      uses: actions/upload-artifact@v3
      with:
        name: performance-test-results
        path: '**/target/surefire-reports/*.xml'
```

**Deliverables**:
- [ ] GitHub Actions workflow created
- [ ] E2E tests running in CI
- [ ] Performance tests running on main branch
- [ ] Test results uploaded as artifacts

---

## Summary: Sequence of Activities

### Phase 1: Dockerization (Week 1)
1. Create Dockerfile for API
2. Create Dockerfile for Worker
3. Test Docker builds locally
4. Create .dockerignore

### Phase 2: Load Balancer (Week 1)
1. Create nginx configuration
2. Create nginx Dockerfile
3. Test load balancing locally

### Phase 3: Docker Compose (Week 2)
1. Create docker-compose.yml with all services
2. Configure environment variables
3. Test full stack startup
4. Create helper scripts

### Phase 4: E2E Tests (Week 2-3)
1. Create E2ETestBase class
2. Implement functional E2E tests
3. Add database verification
4. Validate load balancing and worker distribution

### Phase 5: Performance Tests (Week 3-4)
1. Create PerformanceTestBase class
2. Implement throughput tests (500 TPS, 1000 TPS)
3. Implement endurance test (10 min)
4. Add metrics collection and reporting

### Phase 6: Automation (Week 4)
1. Create test execution scripts
2. Add Maven profiles
3. Create TESTING.md documentation
4. Validate all scripts

### Phase 7: CI/CD (Week 5 - Optional)
1. Create GitHub Actions workflow
2. Test CI/CD pipeline
3. Configure artifact uploads

---

## Success Criteria

### Functional
- [ ] All services containerized
- [ ] nginx load balancer operational
- [ ] Full stack starts with single command
- [ ] E2E tests pass (100%)
- [ ] Database verification working

### Performance
- [ ] Throughput: >400 TPS achieved
- [ ] Success rate: >98%
- [ ] Latency P95: <200ms
- [ ] Latency P99: <500ms
- [ ] Endurance test: 10 min sustained load passes

### Operational
- [ ] Docker images build successfully
- [ ] Health checks configured for all services
- [ ] Test automation scripts working
- [ ] Documentation complete
- [ ] CI/CD pipeline functional (optional)

---

## Next Steps After Completion

1. **Monitoring & Observability**: Add Prometheus + Grafana for metrics
2. **Advanced Load Testing**: Add k6 or JMeter for complex scenarios
3. **Chaos Engineering**: Test resilience with random failures
4. **Resource Optimization**: Tune Docker resource limits
5. **Kubernetes Deployment**: Migrate to K8s for production

---

**Document Version**: 1.0
**Created**: 2025-11-02
**Status**: Ready for Implementation
**Estimated Duration**: 4-5 weeks
