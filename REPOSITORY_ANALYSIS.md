# JDBC Persistence Repository Analysis

## Overview

The codebase implements a Template Method pattern for database-agnostic JDBC repositories. Each repository has an
abstract base class (e.g., `JdbcOutboxRepository`) with database-specific implementations for H2 and PostgreSQL (e.g.,
`H2OutboxRepository`, `PostgresOutboxRepository`).

---

## 1. OUTBOX REPOSITORY

### Interface: OutboxRepository

**Location:**
`/Users/igormusic/code/messaging-platform/msg-platform-core/src/main/java/com/acme/reliable/repository/OutboxRepository.java`

#### Key Methods:

- `long insertReturningId(String category, String topic, String key, String type, String payload, String headers)` -
  Insert and return generated ID
- `void markPublished(long id)` - Mark entry as published
- `void markFailed(long id, String error, Instant nextAttempt)` - Mark failed with error and reschedule
- `void reschedule(long id, long backoffMs, String error)` - Reschedule with backoff
- `Optional<Outbox> claimIfNew(long id)` - Claim single entry if in NEW status
- `default Optional<Outbox> claimOne(long id)` - Alias for claimIfNew
- `List<Outbox> sweepBatch(int max)` - Claim batch for processing (NEW, timed-out CLAIMED, FAILED)
- `default List<Outbox> claim(int max, String claimer)` - Alias for sweepBatch
- `int recoverStuck(Duration olderThan)` - Recover entries stuck in SENDING

### Domain Object: Outbox

**Location:**
`/Users/igormusic/code/messaging-platform/msg-platform-core/src/main/java/com/acme/reliable/domain/Outbox.java`

#### Fields:

- `Long id` - Primary key
- `String category` - Message category (command, event, reply)
- `String topic` - Kafka topic or MQ queue name
- `String key` - Message key for partitioning
- `String type` - Message type/class name
- `String payload` - JSON payload
- `Map<String, String> headers` - Message headers
- `String status` - Status (NEW, CLAIMED, PUBLISHED, FAILED)
- `int attempts` - Retry attempt count
- `Instant nextAt` - Next retry time
- `String claimedBy` - Process claiming this entry
- `Instant createdAt` - Creation timestamp
- `Instant publishedAt` - Publication timestamp
- `String lastError` - Last error message

#### Factory Methods:

- `newCommandRequested()` - Create command outbox entry
- `newKafkaEvent()` - Create Kafka event entry
- `newMqReply()` - Create MQ reply entry

### Abstract Base: JdbcOutboxRepository

**Location:**
`/Users/igormusic/code/messaging-platform/msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/outbox/JdbcOutboxRepository.java`

#### Implementation Methods:

- `long insertReturningId()` - Uses RETURN_GENERATED_KEYS
- `Optional<Outbox> claimIfNew()` - Update status to CLAIMED, fetch entry
- `List<Outbox> sweepBatch()` - Select batch with status filtering, update to CLAIMED
- `void markPublished()` - Update status and published_at
- `void markFailed()` - Set error and next_at
- `void reschedule()` - Set next_at and error with backoff calculation
- `int recoverStuck()` - Reset CLAIMED entries to NEW if too old
- `Outbox mapResultSetToOutbox()` - ResultSet to domain object mapping

#### Template Methods (Abstract):

- `getInsertSql()` - INSERT statement
- `getClaimIfNewSql()` - UPDATE with CLAIM logic
- `getSweepBatchSql()` - SELECT batch with claiming
- `getMarkPublishedSql()` - UPDATE published status
- `getMarkFailedSql()` - UPDATE failed status
- `getRescheduleSql()` - UPDATE reschedule
- `getRecoverStuckSql()` - UPDATE stuck recovery

### H2 Implementation: H2OutboxRepository

**Location:**
`/Users/igormusic/code/messaging-platform/msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/outbox/H2OutboxRepository.java`

#### Key Differences from Abstract:

- **Custom sweepBatch()**: Overrides to handle H2 limitations (no RETURNING in UPDATE)
    - Two-phase approach: SELECT first, then UPDATE IN with list of IDs
    - Builds dynamic IN clause for batch updates
- **SQL Dialect**: H2 specific SQL without RETURNING clause

#### Database-Specific SQL:

```sql
-- Insert
INSERT INTO outbox (category, topic, key, type, payload, headers, status, attempts, created_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)

-- Claim if new (UPDATE only)
UPDATE outbox SET status = 'CLAIMED' WHERE id = ? AND status = 'NEW'

-- Sweep batch
SELECT ... FROM outbox 
WHERE (status = 'NEW' OR (status = 'CLAIMED' AND created_at < DATEADD('MINUTE', -5, NOW())))
  AND (next_at IS NULL OR next_at <= NOW())
ORDER BY created_at ASC LIMIT ?
-- Then: UPDATE outbox SET status = 'CLAIMED' WHERE id IN (...)

-- Recover stuck
UPDATE outbox SET status = 'NEW', next_at = NULL
WHERE status = 'CLAIMED' AND created_at < ?
```

### PostgreSQL Implementation: PostgresOutboxRepository

**Location:**
`/Users/igormusic/code/messaging-platform/msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/outbox/PostgresOutboxRepository.java`

#### Key Differences:

- Uses RETURNING clause in UPDATE statements for atomic read-after-write
- Uses CTE (WITH clause) for sweep batch with FOR UPDATE SKIP LOCKED
- Handles headers as JSONB type

#### Database-Specific SQL:

```sql
-- Insert with JSONB
INSERT INTO outbox (category, topic, key, type, payload, headers, status, attempts, created_at)
VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)

-- Claim if new with RETURNING
UPDATE outbox SET status = 'CLAIMED' WHERE id = ? AND status = 'NEW'
RETURNING id, category, topic, key, type, payload, headers, status, attempts, ...

-- Sweep batch with CTE and FOR UPDATE SKIP LOCKED
WITH available AS (
  SELECT id FROM outbox
  WHERE (status = 'NEW' OR (status = 'CLAIMED' AND created_at < now() - interval '5 minutes'))
    AND (next_at IS NULL OR next_at <= now())
  ORDER BY created_at ASC LIMIT ? FOR UPDATE SKIP LOCKED
)
UPDATE outbox o SET status = 'CLAIMED' FROM available
WHERE o.id = available.id
RETURNING o.id, o.category, ...
```

---

## 2. INBOX REPOSITORY

### Interface: InboxRepository

**Location:**
`/Users/igormusic/code/messaging-platform/msg-platform-core/src/main/java/com/acme/reliable/repository/InboxRepository.java`

#### Key Methods:

- `int insertIfAbsent(String messageId, String handler)` - Insert if not duplicate (idempotency check)
    - Returns 1 if inserted, 0 if duplicate exists

### Domain Object: Inbox

**Location:**
`/Users/igormusic/code/messaging-platform/msg-platform-core/src/main/java/com/acme/reliable/domain/Inbox.java`

#### Fields:

- `InboxId id` - Composite key
    - `String messageId` - Unique message identifier
    - `String handler` - Handler processing this message
- `Instant processedAt` - Processing timestamp

### Abstract Base: JdbcInboxRepository

**Location:**
`/Users/igormusic/code/messaging-platform/msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/inbox/JdbcInboxRepository.java`

#### Implementation Methods:

- `int insertIfAbsent()` - Insert with duplicate detection
    - Uses SQL-specific syntax for idempotent insert
    - Returns count of rows inserted (0 for duplicates)

#### Template Methods (Abstract):

- `getInsertIfAbsentSql()` - Database-specific idempotent insert

### H2 Implementation: H2InboxRepository

**Location:**
`/Users/igormusic/code/messaging-platform/msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/inbox/H2InboxRepository.java`

#### Database-Specific SQL:

```sql
-- H2 uses INSERT IGNORE
INSERT IGNORE INTO inbox (message_id, handler, processed_at)
VALUES (?, ?, ?)
```

### PostgreSQL Implementation: PostgresInboxRepository

**Location:**
`/Users/igormusic/code/messaging-platform/msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/inbox/PostgresInboxRepository.java`

#### Database-Specific SQL:

```sql
-- PostgreSQL uses ON CONFLICT DO NOTHING
INSERT INTO inbox (message_id, handler, processed_at)
VALUES (?, ?, ?)
ON CONFLICT DO NOTHING
```

---

## 3. DLQ (DEAD LETTER QUEUE) REPOSITORY

### Interface: DlqRepository

**Location:**
`/Users/igormusic/code/messaging-platform/msg-platform-core/src/main/java/com/acme/reliable/repository/DlqRepository.java`

#### Key Methods:

-

`void insertDlqEntry(UUID commandId, String commandName, String businessKey, String payload, String failedStatus, String errorClass, String errorMessage, int attempts, String parkedBy)` -
Park failed command for manual intervention

### Abstract Base: JdbcDlqRepository

**Location:**
`/Users/igormusic/code/messaging-platform/msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/dlq/JdbcDlqRepository.java`

#### Implementation Methods:

- `void insertDlqEntry()` - Insert DLQ entry with all failure details
    - Generates UUID for DLQ ID
    - Records timestamp when parked

#### Template Methods (Abstract):

- `getInsertDlqEntrySql()` - Database-specific insert

### H2 Implementation: H2DlqRepository

**Location:**
`/Users/igormusic/code/messaging-platform/msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/dlq/H2DlqRepository.java`

#### Database-Specific SQL:

```sql
INSERT INTO dlq
(id, command_id, command_name, business_key, payload, failed_status, error_class, error_message, attempts, parked_by, parked_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
```

---

## 4. PROCESS REPOSITORY

### Interface: ProcessRepository

**Location:**
`/Users/igormusic/code/messaging-platform/msg-platform-core/src/main/java/com/acme/reliable/repository/ProcessRepository.java`

#### Key Methods:

- `void insert(ProcessInstance instance, ProcessEvent initialEvent)` - Create new process with initial event
- `Optional<ProcessInstance> findById(UUID processId)` - Find by process ID
- `List<ProcessInstance> findByStatus(ProcessStatus status, int limit)` - Find by status
- `List<ProcessInstance> findByTypeAndStatus(String processType, ProcessStatus status, int limit)` - Find by type and
  status
- `void update(ProcessInstance instance, ProcessEvent event)` - Update instance and log event
- `List<ProcessLogEntry> getLog(UUID processId)` - Get event log (unlimited)
- `List<ProcessLogEntry> getLog(UUID processId, int limit)` - Get event log with limit
- `Optional<ProcessInstance> findByBusinessKey(String processType, String businessKey)` - Find by business key

### Domain Objects: Process

#### ProcessInstance (Record)

**Location:**
`/Users/igormusic/code/messaging-platform/msg-platform-core/src/main/java/com/acme/reliable/process/ProcessInstance.java`

**Immutable record with copy-on-write updates:**

- `UUID processId` - Unique process identifier
- `String processType` - Process type/name
- `String businessKey` - Business domain key
- `ProcessStatus status` - Current status
- `String currentStep` - Current execution step
- `Map<String, Object> data` - Mutable working data
- `int retries` - Retry count
- `Instant createdAt` - Creation timestamp
- `Instant updatedAt` - Last update timestamp

**Factory & Update Methods:**

- `create()` - Create new process instance
- `withStatus()` - Create copy with new status
- `withCurrentStep()` - Create copy with new step
- `withData()` - Create copy with new data
- `withRetries()` - Create copy with new retry count
- `update()` - Create copy with multiple field updates

#### ProcessStatus (Enum)

**Location:**
`/Users/igormusic/code/messaging-platform/msg-platform-core/src/main/java/com/acme/reliable/process/ProcessStatus.java`

Values:

- `NEW` - Created but not started
- `RUNNING` - Actively executing steps
- `SUCCEEDED` - Completed successfully
- `FAILED` - Failed permanently
- `COMPENSATING` - Executing compensation steps
- `COMPENSATED` - Compensation completed
- `PAUSED` - Paused by operator

#### ProcessEvent (Sealed Interface)

**Location:**
`/Users/igormusic/code/messaging-platform/msg-platform-core/src/main/java/com/acme/reliable/process/ProcessEvent.java`

Event Types:

- `ProcessStarted(String processType, String businessKey, Map<String, Object> initialData)`
- `StepStarted(String step, String commandId)`
- `StepCompleted(String step, String commandId, Map<String, Object> resultData)`
- `StepFailed(String step, String commandId, String error, boolean retryable)`
- `StepTimedOut(String step, String commandId, String error)`
- `CompensationStarted(String step, String commandId)`
- `CompensationCompleted(String step, String commandId)`
- `CompensationFailed(String step, String commandId, String error)`
- `ProcessCompleted(String reason)`
- `ProcessFailed(String reason)`
- `ProcessPaused(String reason, String pausedBy)`
- `ProcessResumed(String reason, String resumedBy)`

#### ProcessLogEntry (Record)

**Location:**
`/Users/igormusic/code/messaging-platform/msg-platform-core/src/main/java/com/acme/reliable/process/ProcessLogEntry.java`

- `UUID processId` - Process identifier
- `long sequence` - Event sequence number
- `Instant timestamp` - Event timestamp
- `ProcessEvent event` - The event itself

### Abstract Base: JdbcProcessRepository

**Location:**
`/Users/igormusic/code/messaging-platform/msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/process/JdbcProcessRepository.java`

#### Implementation Methods:

- `void insert()` - Insert process instance and initial event
- `Optional<ProcessInstance> findById()` - Find by process ID
- `List<ProcessInstance> findByStatus()` - Query by status
- `List<ProcessInstance> findByTypeAndStatus()` - Query by type and status
- `void update()` - Update instance and log event
- `List<ProcessLogEntry> getLog()` - Retrieve event log
- `Optional<ProcessInstance> findByBusinessKey()` - Find by business key
- `ProcessInstance mapResultSetToInstance()` - ResultSet mapping
- `ProcessLogEntry mapResultSetToLogEntry()` - Log entry mapping

**Key Features:**

- Uses JSON serialization for data and events
- Maintains separate process and process_log tables
- Event sourcing with immutable log entries

#### Template Methods (Abstract):

- `getInsertSql()` - Process insert
- `getFindByIdSql()` - Find by ID query
- `getFindByStatusSql()` - Find by status query
- `getFindByTypeAndStatusSql()` - Find by type/status query
- `getUpdateSql()` - Process update
- `getLogQuerySql()` - Log query
- `getFindByBusinessKeySql()` - Find by business key query
- `getInsertLogEntrySql()` - Log entry insert

### H2 Implementation: H2ProcessRepository

**Location:**
`/Users/igormusic/code/messaging-platform/msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/process/H2ProcessRepository.java`

#### Database-Specific SQL:

```sql
-- Insert process
INSERT INTO process
(process_id, process_type, business_key, status, current_step, data, retries, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)

-- Find by ID
SELECT process_id, process_type, business_key, status, current_step, data, retries, created_at, updated_at
FROM process WHERE process_id = ?

-- Find by status
SELECT ... FROM process WHERE status = ? ORDER BY created_at DESC LIMIT ?

-- Find by type and status
SELECT ... FROM process 
WHERE process_type = ? AND status = ? ORDER BY created_at DESC LIMIT ?

-- Update process
UPDATE process SET status = ?, current_step = ?, data = ?, retries = ?, updated_at = ?
WHERE process_id = ?

-- Get log
SELECT process_id, seq, at, event FROM process_log
WHERE process_id = ? ORDER BY seq DESC LIMIT ?

-- Find by business key
SELECT ... FROM process WHERE process_type = ? AND business_key = ?

-- Insert log entry
INSERT INTO process_log (process_id, event) VALUES (?, ?)
```

---

## TEST SCENARIOS FOR EACH REPOSITORY

### OutboxRepository Test Scenarios

#### H2OutboxRepository & PostgresOutboxRepository:

1. **insertReturningId()**
    - Insert entry with valid data, verify returned ID is positive
    - Verify entry created with NEW status
    - Verify timestamps are set to current time
    - Verify headers default to empty JSON object

2. **claimIfNew()**
    - Claim NEW entry, verify returns Optional with entry
    - Attempt to claim already claimed entry, verify empty Optional
    - Attempt to claim non-existent entry, verify empty Optional
    - Verify status changes from NEW to CLAIMED

3. **sweepBatch()**
    - Insert multiple NEW entries, sweep batch with limit, verify correct count returned
    - Insert mix of NEW and CLAIMED entries, verify only NEW are claimed
    - Insert CLAIMED entry older than 5 minutes, verify included in sweep
    - Insert CLAIMED entry younger than 5 minutes, verify excluded from sweep
    - Insert entries with next_at in future, verify excluded
    - Insert entries with next_at in past, verify included
    - Verify entries are transitioned to CLAIMED status

4. **markPublished()**
    - Mark entry as published, verify status changes to PUBLISHED
    - Verify published_at timestamp is set
    - Mark non-existent entry, verify no exception (warn logged)

5. **markFailed()**
    - Mark entry as failed, verify error message stored
    - Verify next_at is set to future time
    - Mark entry multiple times, verify error accumulates or overwrites

6. **reschedule()**
    - Reschedule with various backoff values (1000ms, 5000ms, 60000ms)
    - Verify next_at is set correctly
    - Verify error message is stored
    - Verify status does NOT change (remains CLAIMED or FAILED)

7. **recoverStuck()**
    - Insert CLAIMED entry older than threshold
    - Call recoverStuck with duration
    - Verify status changes to NEW
    - Verify next_at is cleared (NULL)
    - Verify newer CLAIMED entries are not affected

#### H2-Specific:

8. **sweepBatch H2 implementation** (two-phase approach)
    - Test with large batch (100+ entries) to verify IN clause building works
    - Verify no SQL injection vulnerabilities in dynamic IN clause
    - Test with concurrent claims to ensure consistency

#### PostgreSQL-Specific:

9. **sweepBatch Postgres implementation** (FOR UPDATE SKIP LOCKED)
    - Test concurrent sweeps to verify SKIP LOCKED prevents blocking
    - Verify WITH clause CTE correctly filters available entries
    - Verify RETURNING clause provides immediate result without second query

### InboxRepository Test Scenarios

1. **insertIfAbsent() - First Insert**
    - Insert new message ID with handler
    - Verify returns 1 (one row inserted)
    - Verify inbox table contains entry

2. **insertIfAbsent() - Duplicate**
    - Insert same message ID and handler twice
    - First insert returns 1
    - Second insert returns 0 (duplicate, not inserted)
    - Verify only one entry exists

3. **insertIfAbsent() - Idempotency**
    - Insert same message with different handlers (should be allowed)
    - Both inserts return 1
    - Verify both entries exist

4. **insertIfAbsent() - Edge Cases**
    - Insert with null/empty message ID (should fail or return 0)
    - Insert with very long message ID (1000+ chars)
    - Insert with special characters in handler name

#### H2-Specific:

5. **H2 INSERT IGNORE syntax**
    - Verify INSERT IGNORE correctly handles duplicates
    - Verify no exception thrown on duplicate

#### PostgreSQL-Specific:

6. **PostgreSQL ON CONFLICT DO NOTHING**
    - Verify ON CONFLICT DO NOTHING correctly handles duplicates
    - Verify returns 0 for conflict case

### DlqRepository Test Scenarios

1. **insertDlqEntry()**
    - Insert entry with all required fields
    - Verify entry created with generated UUID
    - Verify parked_at timestamp is set
    - Verify all fields stored correctly:
        - command_id
        - command_name
        - business_key
        - payload (JSON preserved)
        - failed_status (FAILED, TIMED_OUT, etc.)
        - error_class (exception class name)
        - error_message
        - attempts (number of failures)
        - parked_by (component name)

2. **insertDlqEntry() - Long Values**
    - Insert with very long error messages (1000+ chars)
    - Insert with large JSON payload (10KB+)
    - Verify truncation or storage handling

3. **insertDlqEntry() - NULL Handling**
    - Insert with null error message
    - Insert with null business key
    - Verify database constraints allow/reject appropriately

### ProcessRepository Test Scenarios

1. **insert() - New Process**
    - Create new ProcessInstance with all fields
    - Insert with ProcessStarted event
    - Verify process table contains entry with correct status (NEW)
    - Verify process_log contains initial event
    - Verify event log entry has sequence number

2. **findById()**
    - Find existing process by ID
    - Verify all fields match (including data map)
    - Find non-existent process
    - Verify returns empty Optional

3. **findByStatus()**
    - Insert processes with different statuses (NEW, RUNNING, SUCCEEDED, FAILED)
    - Query by RUNNING status with limit of 2
    - Verify returns only RUNNING processes
    - Verify result count respects limit
    - Verify ordered by created_at DESC

4. **findByTypeAndStatus()**
    - Insert processes of different types (OrderProcess, PaymentProcess)
    - Insert with different statuses
    - Query for specific type and status combination
    - Verify only matching entries returned
    - Verify limit is respected

5. **update() - Status Change**
    - Insert process in NEW status
    - Update to RUNNING with StepStarted event
    - Verify status changed
    - Verify event logged
    - Find by ID and verify state

6. **update() - Step Change**
    - Update currentStep from "step1" to "step2"
    - Verify in log via getLog()
    - Log should contain StepCompleted event

7. **update() - Data Modification**
    - Update data map with new key-value pairs
    - Verify JSON serialization/deserialization
    - Verify data persisted and retrievable

8. **getLog() - Event Sourcing**
    - Insert process with ProcessStarted
    - Update multiple times with various events
    - Call getLog() without limit
    - Verify all events in sequence
    - Verify events in correct order (DESC by sequence)

9. **getLog() - Limit**
    - Insert process with 10 events
    - Call getLog(processId, 3)
    - Verify returns exactly 3 most recent events

10. **findByBusinessKey()**
    - Insert multiple processes with same type, different business keys
    - Query with specific business key
    - Verify exact match returned
    - Query with non-existent business key
    - Verify empty Optional

11. **Process State Transitions**
    - NEW -> RUNNING -> SUCCEEDED (happy path)
    - NEW -> RUNNING -> FAILED (failure path)
    - NEW -> RUNNING -> COMPENSATING -> COMPENSATED (compensation)
    - NEW -> PAUSED -> RESUMED -> RUNNING (pause/resume)
    - Verify each transition is logged

12. **Concurrent Process Updates**
    - Insert process
    - Simulate concurrent updates from different components
    - Verify last update wins (or verify optimistic locking if implemented)
    - Verify event log captures all updates

13. **JSON Data Serialization**
    - Insert process with complex nested data structures
    - Verify Maps are correctly serialized
    - Verify deserialization maintains structure
    - Test with special characters in data values

14. **Process Lifecycle Events**
    - Test all event types can be logged:
        - ProcessStarted, StepStarted, StepCompleted, StepFailed, StepTimedOut
        - CompensationStarted, CompensationCompleted, CompensationFailed
        - ProcessCompleted, ProcessFailed, ProcessPaused, ProcessResumed
    - Verify event type information preserved through serialization

---

## ARCHITECTURAL PATTERNS

### Template Method Pattern

- **Abstract Base Classes:** `JdbcOutboxRepository`, `JdbcInboxRepository`, `JdbcDlqRepository`, `JdbcProcessRepository`
- **Concrete Implementations:** H2 and PostgreSQL variants
- **Benefit:** Centralize business logic, only override database-specific SQL

### Database Abstraction

- **Property-based Selection:** `@Requires(property = "db.dialect", value = "H2")`
- **Configuration Driven:** Database selection at application startup
- **Singleton Scope:** Single instance per database implementation

### Transaction Management

- **@Transactional Annotation:** Methods marked with transaction boundaries
- **Read-only Operations:** `@Transactional(readOnly = true)` for queries
- **Consistency:** All reads/writes within transaction for ACID compliance

### ResultSet Mapping

- **Centralized Mapping:** `mapResultSetToOutbox()`, `mapResultSetToInstance()`, etc.
- **Type Conversion:** Handles Timestamp -> Instant, JSON strings -> objects
- **Null Safety:** Proper null checks for nullable columns

### Event Sourcing (Process Repository)

- **Immutable Events:** ProcessEvent sealed interface with record implementations
- **Event Log:** Separate table for complete audit trail
- **Replay Capability:** Events can be replayed to reconstruct state

---

## KEY IMPLEMENTATION DIFFERENCES

### H2 vs PostgreSQL

| Feature                | H2                                   | PostgreSQL                                 |
|------------------------|--------------------------------------|--------------------------------------------|
| **Upsert/Conflict**    | INSERT IGNORE                        | ON CONFLICT DO NOTHING                     |
| **RETURNING clause**   | Not supported                        | Supported (UPDATE ... RETURNING)           |
| **Query optimization** | FOR UPDATE SKIP LOCKED not available | FOR UPDATE SKIP LOCKED (prevents blocking) |
| **CTE support**        | Basic                                | Full WITH clause support                   |
| **JSON type**          | String                               | JSONB with type casting                    |
| **Generated keys**     | RETURN_GENERATED_KEYS                | Standard approach                          |

### H2OutboxRepository Special Handling

The H2 implementation of `sweepBatch()` overrides the abstract method entirely because:

1. H2's UPDATE doesn't support RETURNING clause
2. H2 doesn't support FOR UPDATE SKIP LOCKED
3. Solution: Two-phase approach (SELECT then UPDATE IN)

This requires dynamic SQL string building and careful handling of parameter binding for the IN clause.

---

## DATABASE SCHEMA REQUIREMENTS

### Outbox Table

```sql
CREATE TABLE outbox (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  category VARCHAR(50) NOT NULL,
  topic VARCHAR(255) NOT NULL,
  key VARCHAR(255),
  type VARCHAR(255) NOT NULL,
  payload CLOB NOT NULL,
  headers JSON,
  status VARCHAR(20) DEFAULT 'NEW',
  attempts INT DEFAULT 0,
  next_at TIMESTAMP,
  claimed_by VARCHAR(100),
  created_at TIMESTAMP NOT NULL,
  published_at TIMESTAMP,
  last_error VARCHAR(2000)
);
```

### Inbox Table

```sql
CREATE TABLE inbox (
  message_id VARCHAR(255) NOT NULL,
  handler VARCHAR(255) NOT NULL,
  processed_at TIMESTAMP NOT NULL,
  PRIMARY KEY (message_id, handler)
);
```

### DLQ Table

```sql
CREATE TABLE dlq (
  id UUID PRIMARY KEY,
  command_id UUID NOT NULL,
  command_name VARCHAR(255) NOT NULL,
  business_key VARCHAR(255),
  payload CLOB NOT NULL,
  failed_status VARCHAR(50),
  error_class VARCHAR(255),
  error_message VARCHAR(2000),
  attempts INT,
  parked_by VARCHAR(100),
  parked_at TIMESTAMP NOT NULL
);
```

### Process Table

```sql
CREATE TABLE process (
  process_id UUID PRIMARY KEY,
  process_type VARCHAR(255) NOT NULL,
  business_key VARCHAR(255),
  status VARCHAR(50) NOT NULL,
  current_step VARCHAR(255),
  data CLOB,
  retries INT DEFAULT 0,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
```

### Process Log Table

```sql
CREATE TABLE process_log (
  process_id UUID NOT NULL,
  seq BIGINT PRIMARY KEY AUTO_INCREMENT,
  at TIMESTAMP NOT NULL,
  event CLOB NOT NULL,
  FOREIGN KEY (process_id) REFERENCES process(process_id)
);
```

