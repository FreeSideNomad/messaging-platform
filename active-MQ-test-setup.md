# Embedded ActiveMQ Testing Setup for Micronaut

## Overview

This guide provides detailed instructions for implementing an in-memory embedded ActiveMQ testing environment for your Micronaut messaging platform. This allows for comprehensive integration testing of message flow across modules **without requiring Docker, external message brokers, or Testcontainers**.

### Context

- **Framework**: Micronaut 4.10.0
- **Current Broker**: IBM MQ 9.4.3 (production)
- **Test Approach**: Embedded ActiveMQ with VM transport (in-process)
- **CI/CD Constraint**: No Docker/Testcontainers available
- **Existing Setup**: Project uses `@Requires` annotations for conditional bean registration

### Key Benefit

Real JMS semantics and message flow testing without external infrastructure. When component A sends a message, it will actually be queued and component B will actually receive it—all in-memory.

---

## Architecture

### How It Works

Your current setup uses Micronaut's `@Requires` annotation to conditionally load the IBM MQ factory:

```java
@Requires(notEnv = "test")  // Loaded in production/default
@Factory
public class IbmMqFactoryProvider { ... }
```

The testing approach mirrors this pattern with a complementary factory:

```java
@Requires(env = "test")  // Loaded only in test environment
@Factory
public class TestMqFactoryProvider { ... }
```

### Message Flow During Tests

```
Test Execution (env = "test")
    ↓
Micronaut loads TestMqFactoryProvider
    ↓
ConnectionFactory → ActiveMQConnectionFactory("vm://...")
    ↓
Creates embedded broker (in-process, non-persistent)
    ↓
@JmsListener consumers connect to same broker
    ↓
JmsTemplate producers send to same broker
    ↓
Messages actually queue and deliver across modules
```

### Key Components

| Component | File | Purpose |
|-----------|------|---------|
| **Production Factory** | `IbmMqFactoryProvider.java` | Creates IBM MQ ConnectionFactory |
| **Test Factory** | `TestMqFactoryProvider.java` | Creates embedded ActiveMQ ConnectionFactory |
| **Conditional Loading** | `@Requires` annotation | Micronaut activates correct factory based on environment |
| **Test Configuration** | `application-test.yml` | Test-specific settings (H2 DB, disable flyway, etc.) |

---

## Implementation Steps

### Step 1: Add ActiveMQ Dependencies

Edit: `msg-platform-messaging-ibmmq/pom.xml`

Find the `<dependencies>` section and add the following **before the closing `</dependencies>` tag** (near the other test dependencies):

```xml
    <!-- ActiveMQ Embedded for Testing -->
    <dependency>
        <groupId>org.apache.activemq</groupId>
        <artifactId>activemq-broker</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.apache.activemq</groupId>
        <artifactId>activemq-client</artifactId>
        <scope>test</scope>
    </dependency>
```

**Complete context** (what it should look like):

```xml
  <dependencies>
    <!-- Core module (domain + SPI) -->
    <dependency>
      <groupId>com.acme</groupId>
      <artifactId>msg-platform-core</artifactId>
    </dependency>

    <!-- ... existing dependencies ... -->

    <!-- Testing -->
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter-api</artifactId>
      <scope>test</scope>
    </dependency>

    <!-- ... other test dependencies ... -->

    <!-- ActiveMQ Embedded for Testing -->
    <dependency>
        <groupId>org.apache.activemq</groupId>
        <artifactId>activemq-broker</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.apache.activemq</groupId>
        <artifactId>activemq-client</artifactId>
        <scope>test</scope>
    </dependency>
  </dependencies>
```

### Step 2: Create Test Message Broker Factory Provider

Create new file: `msg-platform-messaging-ibmmq/src/test/java/com/acme/reliable/mq/TestMqFactoryProvider.java`

```java
package com.acme.reliable.mq;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSConnectionFactory;
import jakarta.jms.ConnectionFactory;
import org.apache.activemq.ActiveMQConnectionFactory;

/**
 * Test-only factory providing embedded ActiveMQ for integration testing.
 *
 * Activated only in test environment via @Requires annotation.
 * Uses VM transport (vm://...) for in-process messaging with no persistence.
 *
 * This allows testing of message flow across modules without:
 * - External message broker
 * - Docker containers
 * - Testcontainers
 * - External processes
 *
 * The VM transport creates an embedded broker in the same JVM process.
 * Multiple connections to the same vm:// URI share the same broker instance.
 */
@Requires(env = "test")
@Factory
public class TestMqFactoryProvider {

    /**
     * Provides ActiveMQ ConnectionFactory using VM transport.
     *
     * @return ConnectionFactory configured for embedded, non-persistent messaging
     */
    @JMSConnectionFactory("mqConnectionFactory")
    public ConnectionFactory mqConnectionFactory() {
        // VM transport: vm://localhost?broker.persistent=false
        // - vm://localhost: Creates/connects to embedded broker in same JVM
        // - ?broker.persistent=false: No disk persistence (memory only)
        //
        // Benefits:
        // - No external process needed
        // - No Docker required
        // - Messages are real JMS (not mocked)
        // - Automatic cleanup on JVM shutdown
        return new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
    }
}
```

### Step 3: Verify/Update Test Configuration

Check if `application-test.yml` exists in **each module** that has tests. The file should be located at:
- `msg-platform-api/src/test/resources/application-test.yml`
- `msg-platform-worker/src/test/resources/application-test.yml`
- `msg-platform-processor/src/test/resources/application-test.yml`
- `msg-platform-payments-worker/src/test/resources/application-test.yml`

**Minimal test configuration** (add to each module's `application-test.yml`):

```yaml
micronaut:
  application:
    name: ${module-name}-test
  server:
    port: -1  # Random port for integration tests

# Disable unnecessary startup during unit tests
test:
  active: true

# Optional: Configure logging for JMS if needed
logger:
  levels:
    org.apache.activemq: WARN
    jakarta.jms: DEBUG
```

### Step 4: Create Integration Test Example

Create: `msg-platform-api/src/test/java/com/acme/reliable/e2e/CrossModuleMessagingTest.java`

This test demonstrates actual message flow without external infrastructure:

```java
package com.acme.reliable.e2e;

import io.micronaut.jms.annotations.JmsListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.listener.JmsListenerRegistry;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Session;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test demonstrating embedded ActiveMQ message flow.
 *
 * Tests that messages sent by one component are received by another
 * using the in-memory embedded broker (no external infrastructure).
 *
 * Test environment activated via: MICRONAUT_ENVIRONMENTS=test
 */
@MicronautTest(environments = {"test"})
class CrossModuleMessagingTest {

    private static final String TEST_QUEUE = "APP.TEST.Q";
    private static final CountDownLatch messageReceived = new CountDownLatch(1);
    private static String receivedMessage;

    private final ConnectionFactory connectionFactory;

    CrossModuleMessagingTest(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @BeforeEach
    void setup() {
        // Reset for each test
        messageReceived.countDown();  // drain any previous count
    }

    @Test
    void testEmbeddedActiveMQMessageFlow() throws Exception {
        // Verify we're using embedded ActiveMQ (not IBM MQ)
        assertInstanceOf(ActiveMQConnectionFactory.class, connectionFactory,
            "Should use embedded ActiveMQ in test environment");

        // Send test message
        sendTestMessage("Hello from test");

        // Wait for consumption (should be immediate in-process)
        boolean received = messageReceived.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Message should be consumed within 5 seconds");

        // Verify message content
        assertEquals("Hello from test", receivedMessage);
    }

    /**
     * Sends a message to the test queue using the embedded broker.
     */
    private void sendTestMessage(String content) throws JMSException {
        try (var connection = connectionFactory.createConnection();
             var session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            var queue = session.createQueue(TEST_QUEUE);
            var producer = session.createProducer(queue);
            var message = session.createTextMessage(content);

            connection.start();
            producer.send(message);
        }
    }

    /**
     * Simulated message consumer (in real app, this would be a separate module).
     * In tests, it runs in same process and actually receives from embedded broker.
     */
    @JmsListener(concurrency = "1")
    @Queue(TEST_QUEUE)
    void onTestMessage(String message) {
        receivedMessage = message;
        messageReceived.countDown();
    }
}
```

### Step 5: Update Existing Tests (If Using Mocks)

If you have existing unit tests that mock `ConnectionFactory`, they should continue to work unchanged. The test environment will:

1. **For integration tests** annotated with `@MicronautTest(environments = {"test"})`:
   - Loads `TestMqFactoryProvider`
   - Uses embedded ActiveMQ
   - Real message flow is tested

2. **For unit tests** without special annotation:
   - Can still use Mockito/mocks
   - Can still inject `ConnectionFactory` if needed
   - Can coexist with integration tests

### Step 6: Configure Maven for Test Execution

Edit: `pom.xml` (root project)

Ensure the test profile activates the test environment. Add or update the `<build>` section:

```xml
  <build>
    <plugins>
      <!-- ... existing plugins ... -->

      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <configuration>
          <!-- Set test environment for all test runs -->
          <environmentVariables>
            <MICRONAUT_ENVIRONMENTS>test</MICRONAUT_ENVIRONMENTS>
          </environmentVariables>
          <!-- Exclude E2E tests from default runs -->
          <excludedGroups>e2e</excludedGroups>
        </configuration>
      </plugin>
    </plugins>
  </build>
```

---

## Detailed Configuration Reference

### VM Transport URI Options

The embedded broker is configured via the URI: `vm://localhost?broker.persistent=false`

**Key parameters:**

| Parameter | Value | Purpose |
|-----------|-------|---------|
| `vm://` | Protocol | Creates/connects to embedded broker in same JVM |
| `localhost` | Hostname | Can use any hostname; localhost is conventional |
| `broker.persistent` | false | Messages not written to disk (memory only) |
| `broker.useShutdownHook` | true (default) | Cleans up broker on JVM shutdown |
| `broker.transportConnectors.server.transport` | vm | Use only VM transport (recommended for testing) |

**Optional advanced options:**

```java
// If you want to customize broker behavior:
String brokerUri = "vm://localhost?broker.persistent=false"
    + "&broker.useShutdownHook=true"
    + "&broker.brokerName=TestBroker";

return new ActiveMQConnectionFactory(brokerUri);
```

### Micronaut @Requires Annotation

The conditional factory loading uses:

```java
@Requires(env = "test")  // Only loads when "test" environment is active
@Factory
public class TestMqFactoryProvider { ... }
```

**How environments are activated:**

1. **Maven/Gradle**:
   ```bash
   MICRONAUT_ENVIRONMENTS=test mvn test
   ```

2. **System property**:
   ```bash
   -Dmicronaut.environments=test
   ```

3. **application-test.yml**:
   ```yaml
   micronaut:
     environments:
       active: test
   ```

4. **@MicronautTest annotation**:
   ```java
   @MicronautTest(environments = {"test"})
   class MyTest { ... }
   ```

### ConnectionFactory Isolation

**Important**: Each JVM process gets its own embedded broker instance when connecting to `vm://localhost?broker.persistent=false`.

- **Same test JVM**: All connections share one broker (messages flow correctly)
- **Different test JVM**: Separate brokers (messages don't flow between)

This is fine for standard unit/integration testing since tests run in the same JVM.

---

## Testing Scenarios

### Scenario 1: Unit Test with Mocks (Existing)

```java
@ExtendWith(MockitoExtension.class)
class CommandPublisherUnitTest {

    @Mock
    private ConnectionFactory connectionFactory;

    @Mock
    private JmsTemplate jmsTemplate;

    // Tests publish behavior without real messages
}
```

**Verdict**: ✅ Works unchanged. Uses mocks.

---

### Scenario 2: Integration Test with Real Message Flow

```java
@MicronautTest(environments = {"test"})
class CommandFlowIntegrationTest {

    private final CommandPublisher publisher;
    private final CommandConsumer consumer;

    CommandFlowIntegrationTest(CommandPublisher publisher, CommandConsumer consumer) {
        this.publisher = publisher;
        this.consumer = consumer;
    }

    @Test
    void testCommandPublishAndConsume() throws Exception {
        // Actual message flow through embedded ActiveMQ
        publisher.publish(new Command("test"));

        // Consumer receives message in same process
        assertTrue(consumer.awaitMessage(5, TimeUnit.SECONDS));
    }
}
```

**Verdict**: ✅ Uses embedded ActiveMQ. Messages really flow.

---

### Scenario 3: Cross-Module Integration Test

```java
// In msg-platform-api tests, send message to command queue
// In msg-platform-worker tests, verify consumer processes it
//
// Since both use same embedded broker (vm://localhost?...),
// the message actually flows between them
```

**Verdict**: ✅ Works if tests run in sequence. Same broker instance.

---

## Verification Steps

### 1. Verify Dependencies Are Added

```bash
cd /Users/igormusic/code/messaging-platform/msg-platform-messaging-ibmmq
mvn dependency:tree | grep activemq
```

Expected output:
```
[INFO] org.apache.activemq:activemq-broker:jar:5.18.3:test
[INFO] org.apache.activemq:activemq-client:jar:5.18.3:test
```

### 2. Verify TestMqFactoryProvider Is Created

```bash
ls -la msg-platform-messaging-ibmmq/src/test/java/com/acme/reliable/mq/TestMqFactoryProvider.java
```

Should exist and be readable.

### 3. Run a Single Test

```bash
cd /Users/igormusic/code/messaging-platform

# Run test with test environment activated
MICRONAUT_ENVIRONMENTS=test mvn test -Dtest=TestMqFactoryProvider -pl msg-platform-messaging-ibmmq
```

Or in your IDE:
- Set environment variable: `MICRONAUT_ENVIRONMENTS=test`
- Run any `@MicronautTest(environments = {"test"})` test

### 4. Verify Correct Factory Is Loaded

Add logging to see which factory loads:

In `TestMqFactoryProvider.java`:
```java
@JMSConnectionFactory("mqConnectionFactory")
public ConnectionFactory mqConnectionFactory() {
    System.out.println("✓ Loaded TestMqFactoryProvider (EMBEDDED ACTIVEMQ)");
    return new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
}
```

In `IbmMqFactoryProvider.java`:
```java
@JMSConnectionFactory("mqConnectionFactory")
public jakarta.jms.ConnectionFactory mqConnectionFactory() throws Exception {
    System.out.println("✓ Loaded IbmMqFactoryProvider (IBM MQ)");
    // ... existing code ...
}
```

When running tests, you should see:
```
✓ Loaded TestMqFactoryProvider (EMBEDDED ACTIVEMQ)
```

---

## Troubleshooting

### Problem: "No factory bean named 'mqConnectionFactory' found"

**Cause**: Neither `IbmMqFactoryProvider` nor `TestMqFactoryProvider` was loaded.

**Solution**:
- Verify `MICRONAUT_ENVIRONMENTS=test` is set
- Verify `@Requires(env = "test")` is on `TestMqFactoryProvider`
- Check Micronaut logs for bean loading info

### Problem: Tests are trying to connect to real IBM MQ

**Cause**: `IbmMqFactoryProvider` loaded instead of `TestMqFactoryProvider`.

**Solution**:
```bash
# Explicitly set test environment
export MICRONAUT_ENVIRONMENTS=test
mvn test

# Or in IDE run configuration:
# Environment variables: MICRONAUT_ENVIRONMENTS=test
```

### Problem: "Connection refused" or socket errors

**Cause**: Code is trying to reach external IBM MQ instead of using embedded broker.

**Solution**:
1. Verify test environment is active
2. Verify `TestMqFactoryProvider` exists
3. Verify `@Requires(env = "test")` annotation is present
4. Check test class has `@MicronautTest(environments = {"test"})`

### Problem: Messages not flowing between test modules

**Cause**: Tests running in different JVM processes.

**Solution**:
- Ensure tests run in same process (standard Maven behavior)
- Check if parallel test execution is enabled; if so, message isolation might be expected

### Problem: "ClassNotFoundException: ActiveMQConnectionFactory"

**Cause**: ActiveMQ dependencies not in classpath.

**Solution**:
```bash
# Add to msg-platform-messaging-ibmmq/pom.xml as shown in Step 1
mvn clean install
```

---

## How Message Flow Works in Tests

### Single Module Test (e.g., msg-platform-processor)

```
Test runs in JVM
    ↓
@MicronautTest(environments = "test") activates
    ↓
TestMqFactoryProvider loads
    ↓
ActiveMQ embedded broker starts in-process
    ↓
@JmsListener connects to broker
    ↓
Test sends message via JmsTemplate
    ↓
Message queued in embedded broker (memory)
    ↓
@JmsListener receives and processes
    ↓
Test verifies result
```

### Cross-Module Test Scenario

```
Test Suite runs (same JVM)
    ↓
Both modules load TestMqFactoryProvider
    ↓
Both connect to SAME embedded broker (vm://localhost)
    ↓
Module A sends: publisher.send(message)
    ↓
Message in broker queue
    ↓
Module B's @JmsListener receives it
    ↓
Cross-module communication successful
```

---

## Performance Characteristics

| Aspect | Embedded ActiveMQ | External IBM MQ |
|--------|------------------|-----------------|
| **Startup** | < 100ms | 5-30s (network) |
| **Message latency** | < 1ms (in-process) | 10-50ms (network) |
| **Memory per test** | ~10-20MB | None (external) |
| **Test isolation** | Via shutdown hook | Via external cleanup |
| **Parallel tests** | Possible (separate brokers) | Possible (but shared) |
| **CI/CD friendly** | ✅ Yes (no Docker) | ❌ No (needs broker) |

---

## Advanced: JUnit Extension for Broker Control

If you want to explicitly start/stop the broker per test class:

```java
package com.acme.reliable.e2e;

import org.apache.activemq.broker.BrokerService;
import org.junit.jupiter.api.extension.*;

/**
 * JUnit 5 extension that manages embedded ActiveMQ broker lifecycle.
 * Optional: Use if you need explicit control over broker start/stop.
 */
public class EmbeddedActiveMQExtension implements BeforeAllCallback, AfterAllCallback {

    private static BrokerService brokerService;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (brokerService == null) {
            brokerService = new BrokerService();
            brokerService.setUseJmx(false);
            brokerService.setPersistent(false);
            brokerService.setBrokerName("TestBroker");
            brokerService.addConnector("vm://0");  // VM transport only
            brokerService.start();
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        if (brokerService != null) {
            brokerService.stop();
            brokerService = null;
        }
    }
}
```

Usage:
```java
@MicronautTest(environments = {"test"})
@ExtendWith(EmbeddedActiveMQExtension.class)
class MyIntegrationTest {
    // Broker explicitly managed by extension
}
```

---

## Comparison: Test Approaches

| Approach | Dependencies | CI/CD | Real JMS | Cross-Module |
|----------|--------------|-------|----------|--------------|
| **Mocking** | Mockito | ✅ | ❌ | ❌ |
| **Embedded ActiveMQ** | ActiveMQ jars | ✅ | ✅ | ✅ |
| **Testcontainers** | Docker | ❌ | ✅ | ✅ |
| **External IBM MQ** | Network | ❌ | ✅ | ✅ |

**Recommendation for your constraint**: Embedded ActiveMQ (✅ all columns except Docker-specific)

---

## References

- [Apache ActiveMQ VM Transport](https://activemq.apache.org/vm-transport-reference)
- [Micronaut JMS Guide](https://micronaut-projects.github.io/micronaut-jms/snapshot/guide/index.html)
- [Micronaut Conditional Beans](https://docs.micronaut.io/latest/guide/#conditionalBeans)
- [Jakarta JMS API](https://jakarta.ee/specifications/messaging/)
- [Micronaut Test Documentation](https://micronaut-projects.github.io/micronaut-test/latest/guide/)

---

## Next Steps

1. **Execute Step 1-2**: Add dependencies and create `TestMqFactoryProvider`
2. **Execute Step 3**: Verify test configurations exist in each module
3. **Execute Step 4**: Create sample integration test
4. **Execute Step 5**: Run verification checks
5. **Update existing tests**: Annotate integration tests with `@MicronautTest(environments = {"test"})`
6. **Document results**: Add any project-specific notes

---

## Summary

You now have:
- ✅ In-memory JMS broker for testing (no Docker needed)
- ✅ Real message flow testing across modules
- ✅ CI/CD compatible (runs in JVM only)
- ✅ Conditional factory loading via Micronaut `@Requires`
- ✅ Coexistence with production IBM MQ setup
- ✅ Drop-in replacement for integration testing
