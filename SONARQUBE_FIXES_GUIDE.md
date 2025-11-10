# SonarQube Issues - Fixes Guide

## Top Issues by Frequency (391 total on main branch)

### 1. **S1130** - Remove declaration of thrown exception

**Count:** 40+ issues
**Severity:** Critical
**Issue:** Test methods declare `throws Exception` but don't actually throw checked exceptions.

**Problem:**

```java
@Test
void testSomething() throws Exception {  // Unnecessary
    // code that doesn't throw Exception
}
```

**Solution:**

```java
@Test
void testSomething() {  // Remove throws Exception
    // code that doesn't throw Exception
}
```

**When NOT to remove:**

- Methods that use `Thread.sleep()` (throws InterruptedException)
- Methods with SQL operations (throws SQLException)
- Methods with IO operations (throws IOException)

**Fix Status:** ✅ Applied to AutoCommandHandlerRegistryTest.java

---

### 2. **S1612** - Replace lambda with method reference

**Count:** 17 issues
**Severity:** Major
**Issue:** Lambdas can be simplified to method references for better readability.

**Problem:**

```java
list.forEach(item -> System.out.println(item));
```

**Solution:**

```java
list.forEach(System.out::println);
```

**Common patterns:**

- `x -> x.method()` → `Object::method`
- `x -> staticMethod(x)` → `Class::staticMethod`
- `x -> x.toString()` → `Object::toString`

---

### 3. **S2925** - Remove Thread.sleep() in tests

**Count:** 10 issues
**Severity:** Major
**Issue:** `Thread.sleep()` creates flaky, slow tests with unpredictable timing.

**Problem:**

```java
@Test
void testAsync() throws InterruptedException {
    service.doAsync();
    Thread.sleep(500);  // Noncompliant - brittle timing
    assertThat(result).isEqualTo(expected);
}
```

**Solution 1 - Use Awaitility library:**

```java
@Test
void testAsync() {
    service.doAsync();
    await().atMost(2, SECONDS).until(() -> service.isComplete());
    assertThat(result).isEqualTo(expected);
}
```

**Solution 2 - Use CountDownLatch:**

```java
CountDownLatch latch = new CountDownLatch(1);
service.onComplete(() -> latch.countDown());
service.doAsync();
latch.await(2, SECONDS);
assertThat(result).isEqualTo(expected);
```

**Solution 3 - Use Testcontainers or @Test(timeout=):**

```java
@Test(timeout = 2000)
void testAsync() {
    // test with timeout instead of sleep
}
```

---

### 4. **S6213** - Restricted Identifiers

**Count:** 7 issues
**Severity:** Major
**Issue:** Variables named with Java reserved words like `record`, `var`, `yield`.

**Problem:**

```java
ConsumerRecord<String, String> record = records.iterator().next();
assertThat(record.key()).isEqualTo(expectedKey);
```

**Solution:**

```java
ConsumerRecord<String, String> consumerRecord = records.iterator().next();
assertThat(consumerRecord.key()).isEqualTo(expectedKey);
```

**Reserved/Restricted keywords to avoid:**

- `record` → Use `item`, `element`, `consumerRecord`, `kafkaRecord`
- `var` → Use full type or `variable`, `value`
- `yield` → Use `result`, `output`, `value`
- `sealed` → Use `sealed_` or describe what's sealed

**Fix Status:** ✅ Applied to MnKafkaPublisherIntegrationTest.java

---

### 5. **S1068** - Remove unused private fields

**Count:** 3 issues
**Severity:** Major
**Issue:** Private fields declared but never used in the class.

**Problem:**

```java
class NotifyPublisherTest {
    @Mock
    private static final LoggerFactory LENIENT = LoggerFactory.getDefault();  // Unused

    @Mock
    private static final String allowSubscription = "allow";  // Unused
}
```

**Solution:**

```java
class NotifyPublisherTest {
    // Remove unused fields entirely
}
```

---

### 6. **S5838** - Use isEmpty() instead of size()

**Count:** 8 issues
**Severity:** Major
**Issue:** Checking if collection is empty using `size() == 0` instead of `isEmpty()`.

**Problem:**

```java
if (list.size() == 0) { }
if (map.size() > 0) { }
```

**Solution:**

```java
if (list.isEmpty()) { }
if (!map.isEmpty()) { }
```

**Benefits:** More readable, O(1) for most collections, clearer intent

---

### 7. **S5853** - Combine multiple assertions

**Count:** 7 issues
**Severity:** Major
**Issue:** Multiple independent assertions should be combined into one assert block.

**Problem:**

```java
assertThat(result).isNotNull();
assertThat(result).isInstanceOf(String.class);
assertThat(result).contains("expected");
```

**Solution - AssertJ:**

```java
assertThat(result)
    .isNotNull()
    .isInstanceOf(String.class)
    .contains("expected");
```

**Solution - JUnit5 assertAll():**

```java
assertAll(
    () -> assertThat(result).isNotNull(),
    () -> assertThat(result).isInstanceOf(String.class),
    () -> assertThat(result).contains("expected")
);
```

---

### 8. **S4144** - Remove duplicate methods

**Count:** 7 issues
**Severity:** Major
**Issue:** Methods with identical implementations should be consolidated.

**Problem:**

```java
@Test
void testParseCommandWithJson() { /* implementation */ }

@Test
void testParseCommandWithPayload() { /* same implementation */ }
```

**Solution - Use Parameterized Tests:**

```java
@ParameterizedTest(name = "{index} - {0}")
@ValueSource(strings = {"json", "payload"})
void testParseCommand(String format) {
    // Single implementation for all cases
}
```

---

### 9. **S5976** - Replace duplicate tests with Parameterized tests

**Count:** 4 issues
**Severity:** Major
**Issue:** Multiple similar test methods should use `@ParameterizedTest`.

**Problem:**

```java
@Test
void testWithThreeItems() { }

@Test
void testWithFiveItems() { }

@Test
void testWithTenItems() { }
```

**Solution:**

```java
@ParameterizedTest
@ValueSource(ints = {3, 5, 10})
void testWithItems(int count) { }
```

---

### 10. **S125** - Remove commented code

**Count:** 3 issues
**Severity:** Major
**Issue:** Commented-out code blocks should be removed.

**Problem:**

```java
// List<String> oldApproach = processData();
// String result = oldApproach.get(0);
List<String> newApproach = processDataNew();
String result = newApproach.get(0);
```

**Solution:**

```java
// Remove entire commented block
List<String> newApproach = processDataNew();
String result = newApproach.get(0);
```

**Note:** Use git history if you need to recover old code.

---

## Batch Progress

### ✅ Batch 1 - COMPLETED & PUSHED

1. **AutoCommandHandlerRegistryTest.java** - S1130 fix (24 issues)
2. **MnKafkaPublisherIntegrationTest.java** - S6213 fix (6 issues)

### ⏳ Batch 2 - Pending CI/CD Update

- Wait for SonarCloud to refresh
- Next 5 classes to fix (pending updated issue list)

### 🔄 Workflow

1. Identify top 5 classes by issue count from fresh SonarQube scan
2. Apply fixes based on rules above
3. Run `mvn clean verify` locally
4. Commit and push (triggers CI/CD)
5. Wait for SonarCloud update
6. Repeat with next 5 classes

---

## Quick Reference

| Rule  | Fix                                                   | Effort           |
|-------|-------------------------------------------------------|------------------|
| S1130 | Remove throws Exception (if safe)                     | 5 min per file   |
| S1612 | Convert lambda to method reference                    | 5 min per issue  |
| S2925 | Replace Thread.sleep() with Awaitility/CountDownLatch | 20 min per issue |
| S6213 | Rename restricted identifier variables                | 2 min per issue  |
| S1068 | Remove unused private fields                          | 1 min per issue  |
| S5838 | Use isEmpty() instead of size()                       | 1 min per issue  |
| S5853 | Chain assertions with AssertJ                         | 5 min per issue  |
| S4144 | Consolidate duplicate methods                         | 10 min per issue |
| S5976 | Use @ParameterizedTest                                | 15 min per issue |
| S125  | Remove commented code                                 | 2 min per block  |
