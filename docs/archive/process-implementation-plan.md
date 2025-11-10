# Process Manager Implementation Plan

**Version:** 1.0
**Date:** 2025-11-03
**Scope:** Minimal subset for msg-platform-payments-worker (no batch initially)

---

## Table of Contents

1. [Overview](#overview)
2. [Phase 1: Generic Process Manager Framework](#phase-1-generic-process-manager-framework)
3. [Phase 2: Payments Worker Implementation](#phase-2-payments-worker-implementation)
4. [Phase 3: Testing & Validation](#phase-3-testing--validation)
5. [Future Enhancements](#future-enhancements)

---

## Overview

### Goals

- Implement a **generic, reusable Process Manager** framework in `msg-platform-processor`
- Create domain objects in `msg-platform-core`
- Add persistence layer in `msg-platform-persistence-jdbc`
- Build `msg-platform-payments-worker` as the first bounded context using the framework
- Start with **minimal feature set**: single payment orchestration (no batch)

### Out of Scope (Future)

- Batch processing (`batch_run`, `batch_item` tables)
- Multiple bounded contexts (Accounts, Limits, FX - only Payments for now)
- Advanced compensation logic
- Operator admin UI/REST endpoints

### Architecture Principles

1. **Generic first**: Process Manager should be reusable for any orchestration
2. **Domain-driven**: Clear separation between framework and business logic
3. **Event-sourced process log**: Immutable audit trail of all decisions
4. **Idempotent steps**: Each step uses `processId:STEP_NAME` as idempotency key
5. **Reliable messaging**: Built on existing outbox/inbox patterns

---

## Phase 1: Generic Process Manager Framework

### 1.1 Domain Objects (msg-platform-core)

#### New Package: `com.acme.reliable.process`

**ProcessInstance** (Value Object)

```java
public record ProcessInstance(
    UUID processId,
    String processType,        // "PaymentExecution", "AccountOpening", etc.
    String businessKey,         // paymentId, accountId, etc.
    ProcessStatus status,
    String currentStep,
    Map<String, Object> data,   // Working context (fx rate, holds, etc.)
    int retries,
    Instant updatedAt
) {}
```

**ProcessStatus** (Enum)

```java
public enum ProcessStatus {
    NEW,
    RUNNING,
    SUCCEEDED,
    FAILED,
    COMPENSATING,
    COMPENSATED,
    PAUSED
}
```

**ProcessLogEntry** (Value Object)

```java
public record ProcessLogEntry(
    UUID processId,
    long sequence,
    Instant timestamp,
    ProcessEvent event
) {}
```

**ProcessEvent** (Interface + Implementations)

```java
public sealed interface ProcessEvent {
    record ProcessStarted(String processType, String businessKey) implements ProcessEvent {}
    record StepStarted(String step, String commandId) implements ProcessEvent {}
    record StepCompleted(String step, String commandId, Map<String, Object> data) implements ProcessEvent {}
    record StepFailed(String step, String commandId, String error, boolean retryable) implements ProcessEvent {}
    record StepTimedOut(String step, String commandId) implements ProcessEvent {}
    record CompensationStarted(String step, String commandId) implements ProcessEvent {}
    record CompensationCompleted(String step, String commandId) implements ProcessEvent {}
    record ProcessCompleted(String reason) implements ProcessEvent {}
    record ProcessFailed(String reason) implements ProcessEvent {}
}
```

**ProcessDefinition** (Interface - implemented by business logic)

```java
public interface ProcessDefinition {
    String getProcessType();
    String getInitialStep();
    Optional<String> getNextStep(String currentStep, Map<String, Object> data);
    boolean requiresCompensation(String step);
    Optional<String> getCompensationStep(String step);
    boolean isRetryable(String step, String error);
    int getMaxRetries(String step);
}
```

### 1.2 Persistence Layer (msg-platform-persistence-jdbc)

#### New Package: `com.acme.reliable.persistence.jdbc.process`

**SQL Schema** (Flyway migration `V3__process_manager.sql`)

```sql
-- Process instance state
CREATE TABLE process_instance (
    process_id UUID PRIMARY KEY,
    process_type TEXT NOT NULL,
    business_key TEXT NOT NULL,
    status TEXT NOT NULL,
    current_step TEXT NOT NULL,
    data JSONB NOT NULL DEFAULT '{}'::jsonb,
    retries INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_process_instance_status ON process_instance(status);
CREATE INDEX idx_process_instance_type_key ON process_instance(process_type, business_key);
CREATE INDEX idx_process_instance_updated ON process_instance(updated_at);

-- Immutable event log
CREATE TABLE process_log (
    process_id UUID NOT NULL,
    seq BIGINT GENERATED ALWAYS AS IDENTITY,
    at TIMESTAMPTZ NOT NULL DEFAULT now(),
    event JSONB NOT NULL,
    PRIMARY KEY (process_id, seq)
);

CREATE INDEX idx_process_log_at ON process_log(at);
```

**ProcessRepository** (Interface)

```java
public interface ProcessRepository {
    void insert(ProcessInstance instance, ProcessEvent initialEvent);
    Optional<ProcessInstance> findById(UUID processId);
    List<ProcessInstance> findByStatus(ProcessStatus status, int limit);
    void update(ProcessInstance instance, ProcessEvent event);
    List<ProcessLogEntry> getLog(UUID processId);
}
```

**JdbcProcessRepository** (Implementation)

```java
@Singleton
public class JdbcProcessRepository implements ProcessRepository {
    private final TransactionOperations<Connection> txOps;

    @Override
    @Transactional
    public void insert(ProcessInstance instance, ProcessEvent initialEvent) {
        // Insert into process_instance
        // Insert into process_log
    }

    @Override
    @Transactional
    public void update(ProcessInstance instance, ProcessEvent event) {
        // Update process_instance (with optimistic locking)
        // Insert into process_log
    }

    // ... other methods
}
```

### 1.3 Process Manager Engine (msg-platform-processor)

#### New Package: `com.acme.reliable.processor.process`

**ProcessManager** (Core orchestration engine)

```java
@Singleton
public class ProcessManager {
    private final ProcessRepository processRepo;
    private final CommandBus commandBus;
    private final Map<String, ProcessDefinition> definitions;

    public UUID startProcess(ProcessDefinition definition, String businessKey,
                              Map<String, Object> initialData) {
        UUID processId = UUID.randomUUID();
        String initialStep = definition.getInitialStep();

        ProcessInstance instance = new ProcessInstance(
            processId,
            definition.getProcessType(),
            businessKey,
            ProcessStatus.NEW,
            initialStep,
            initialData,
            0,
            Instant.now()
        );

        ProcessEvent event = new ProcessEvent.ProcessStarted(
            definition.getProcessType(),
            businessKey
        );

        processRepo.insert(instance, event);
        executeStep(instance, definition);

        return processId;
    }

    public void handleReply(UUID processId, String commandId, CommandReply reply) {
        ProcessInstance instance = processRepo.findById(processId)
            .orElseThrow(() -> new ProcessNotFoundException(processId));

        ProcessDefinition definition = definitions.get(instance.processType());

        switch (reply.status()) {
            case COMPLETED -> handleStepCompleted(instance, definition, reply);
            case FAILED -> handleStepFailed(instance, definition, reply);
            case TIMED_OUT -> handleStepTimedOut(instance, definition, reply);
        }
    }

    private void executeStep(ProcessInstance instance, ProcessDefinition definition) {
        String step = instance.currentStep();
        String idempotencyKey = instance.processId() + ":" + step;

        UUID commandId = commandBus.send(
            step,
            instance.businessKey(),
            instance.data(),
            idempotencyKey,
            instance.processId() // correlationId
        );

        ProcessEvent event = new ProcessEvent.StepStarted(step, commandId.toString());
        ProcessInstance updated = instance.withStatus(ProcessStatus.RUNNING);
        processRepo.update(updated, event);
    }

    private void handleStepCompleted(ProcessInstance instance,
                                       ProcessDefinition definition,
                                       CommandReply reply) {
        // Merge reply data into process data
        Map<String, Object> newData = new HashMap<>(instance.data());
        newData.putAll(reply.data());

        ProcessEvent event = new ProcessEvent.StepCompleted(
            instance.currentStep(),
            reply.commandId().toString(),
            reply.data()
        );

        Optional<String> nextStep = definition.getNextStep(instance.currentStep(), newData);

        if (nextStep.isPresent()) {
            ProcessInstance updated = instance
                .withCurrentStep(nextStep.get())
                .withData(newData)
                .withRetries(0);
            processRepo.update(updated, event);
            executeStep(updated, definition);
        } else {
            // Process complete
            ProcessInstance updated = instance
                .withStatus(ProcessStatus.SUCCEEDED)
                .withData(newData);
            ProcessEvent completeEvent = new ProcessEvent.ProcessCompleted("All steps completed");
            processRepo.update(updated, completeEvent);
        }
    }

    private void handleStepFailed(ProcessInstance instance,
                                    ProcessDefinition definition,
                                    CommandReply reply) {
        boolean retryable = definition.isRetryable(instance.currentStep(), reply.error());
        int maxRetries = definition.getMaxRetries(instance.currentStep());

        ProcessEvent event = new ProcessEvent.StepFailed(
            instance.currentStep(),
            reply.commandId().toString(),
            reply.error(),
            retryable
        );

        if (retryable && instance.retries() < maxRetries) {
            // Retry
            ProcessInstance updated = instance.withRetries(instance.retries() + 1);
            processRepo.update(updated, event);
            executeStep(updated, definition);
        } else {
            // Permanent failure - check if compensation needed
            if (definition.requiresCompensation(instance.currentStep())) {
                startCompensation(instance, definition, event);
            } else {
                ProcessInstance updated = instance.withStatus(ProcessStatus.FAILED);
                ProcessEvent failEvent = new ProcessEvent.ProcessFailed(reply.error());
                processRepo.update(updated, failEvent);
            }
        }
    }

    private void handleStepTimedOut(ProcessInstance instance,
                                      ProcessDefinition definition,
                                      CommandReply reply) {
        // Similar to handleStepFailed
        ProcessEvent event = new ProcessEvent.StepTimedOut(
            instance.currentStep(),
            reply.commandId().toString()
        );

        if (definition.requiresCompensation(instance.currentStep())) {
            startCompensation(instance, definition, event);
        } else {
            ProcessInstance updated = instance.withStatus(ProcessStatus.FAILED);
            ProcessEvent failEvent = new ProcessEvent.ProcessFailed("Timeout: " + reply.error());
            processRepo.update(updated, failEvent);
        }
    }

    private void startCompensation(ProcessInstance instance,
                                     ProcessDefinition definition,
                                     ProcessEvent triggerEvent) {
        ProcessInstance updated = instance.withStatus(ProcessStatus.COMPENSATING);
        processRepo.update(updated, triggerEvent);

        // Execute compensation steps
        Optional<String> compensationStep = definition.getCompensationStep(instance.currentStep());
        if (compensationStep.isPresent()) {
            executeCompensationStep(updated, definition, compensationStep.get());
        } else {
            ProcessInstance compensated = updated.withStatus(ProcessStatus.COMPENSATED);
            ProcessEvent event = new ProcessEvent.ProcessCompleted("Compensated");
            processRepo.update(compensated, event);
        }
    }

    private void executeCompensationStep(ProcessInstance instance,
                                           ProcessDefinition definition,
                                           String compensationStep) {
        // Similar to executeStep but for compensation
        String idempotencyKey = instance.processId() + ":COMPENSATE:" + compensationStep;

        UUID commandId = commandBus.send(
            compensationStep,
            instance.businessKey(),
            instance.data(),
            idempotencyKey,
            instance.processId()
        );

        ProcessEvent event = new ProcessEvent.CompensationStarted(
            compensationStep,
            commandId.toString()
        );
        processRepo.update(instance, event);
    }
}
```

**ReplyConsumer** (Listens to MQ replies)

```java
@Singleton
public class ReplyConsumer {
    private final ProcessManager processManager;

    @JMSListener(destination = "APP.CMD.REPLY.Q")
    public void onReply(@Body String message) {
        CommandReply reply = parseReply(message);
        UUID processId = reply.correlationId(); // correlationId = processId
        processManager.handleReply(processId, reply.commandId(), reply);
    }
}
```

---

## Phase 2: Payments Worker Implementation

### 2.1 Module Structure

Create new Maven module: `msg-platform-payments-worker`

```
msg-platform-payments-worker/
├── pom.xml
└── src/main/
    ├── java/com/acme/payments/
    │   ├── domain/
    │   │   ├── model/          # Entities, Value Objects
    │   │   │   ├── Account.java
    │   │   │   ├── Transaction.java
    │   │   │   ├── AccountLimit.java
    │   │   │   ├── FxContract.java
    │   │   │   └── Payment.java
    │   │   ├── service/        # Application Domain Services (ADS)
    │   │   │   ├── AccountService.java
    │   │   │   ├── LimitService.java
    │   │   │   ├── FxService.java
    │   │   │   └── PaymentService.java
    │   │   └── repository/     # Repository interfaces
    │   │       ├── AccountRepository.java
    │   │       ├── LimitRepository.java
    │   │       ├── FxContractRepository.java
    │   │       └── PaymentRepository.java
    │   ├── infrastructure/
    │   │   └── persistence/    # JDBC implementations
    │   │       ├── JdbcAccountRepository.java
    │   │       ├── JdbcLimitRepository.java
    │   │       ├── JdbcFxContractRepository.java
    │   │       └── JdbcPaymentRepository.java
    │   ├── application/
    │   │   ├── command/        # Command handlers
    │   │   │   ├── CreateAccountHandler.java
    │   │   │   ├── CreateTransactionHandler.java
    │   │   │   ├── BookLimitsHandler.java
    │   │   │   ├── BookFxHandler.java
    │   │   │   └── CreatePaymentHandler.java
    │   │   └── query/          # Query handlers
    │   │       └── GetAccountByIdHandler.java
    │   └── orchestration/
    │       └── SubmitPaymentProcessDefinition.java  # Process Manager definition
    └── resources/
        ├── application.yml
        └── db/migration/
            └── V1__payments_schema.sql
```

### 2.2 Domain Model (Simplified - Start Minimal)

**Account.java** (Aggregate Root)

```java
public class Account {
    private UUID accountId;
    private UUID customerId;
    private String currencyCode;
    private String accountNumber;
    private String transitNumber;
    private AccountType accountType;
    private BigDecimal availableBalance;
    private boolean isLimitBased;
    private List<Transaction> transactions;

    public Transaction createTransaction(TransactionType type, BigDecimal amount,
                                          String description) {
        Transaction txn = new Transaction(
            UUID.randomUUID(),
            this.accountId,
            Instant.now(),
            type,
            amount,
            description,
            calculateNewBalance(type, amount)
        );

        if (!isLimitBased && type.isDebit()) {
            if (availableBalance.compareTo(amount) < 0) {
                throw new InsufficientFundsException(accountId, availableBalance, amount);
            }
        }

        transactions.add(txn);
        availableBalance = txn.balance();
        return txn;
    }

    private BigDecimal calculateNewBalance(TransactionType type, BigDecimal amount) {
        return type.isCredit()
            ? availableBalance.add(amount)
            : availableBalance.subtract(amount);
    }
}
```

**AccountLimit.java** (Aggregate Root)

```java
public class AccountLimit {
    private UUID limitId;
    private UUID accountId;
    private PeriodType periodType;  // MINUTE, HOUR, DAY, WEEK, MONTH
    private Instant startTime;
    private Instant endTime;
    private BigDecimal limitAmount;
    private BigDecimal utilized;

    public void book(BigDecimal amount) {
        if (utilized.add(amount).compareTo(limitAmount) > 0) {
            throw new LimitExceededException(limitId, periodType, limitAmount, utilized, amount);
        }
        utilized = utilized.add(amount);
    }

    public void reverse(BigDecimal amount) {
        utilized = utilized.subtract(amount);
        if (utilized.compareTo(BigDecimal.ZERO) < 0) {
            utilized = BigDecimal.ZERO;
        }
    }
}
```

**FxContract.java** (Aggregate Root)

```java
public class FxContract {
    private UUID fxContractId;
    private UUID customerId;
    private UUID debitAccountId;
    private Amount debitAmount;
    private Amount creditAmount;
    private BigDecimal rate;
    private LocalDate valueDate;
    private FxStatus status;  // BOOKED, UNWOUND

    public void unwind(String reason) {
        if (status == FxStatus.UNWOUND) {
            throw new FxContractAlreadyUnwoundException(fxContractId);
        }
        status = FxStatus.UNWOUND;
    }
}
```

**Payment.java** (Aggregate Root)

```java
public class Payment {
    private UUID paymentId;
    private UUID debitAccountId;
    private UUID debitTransactionId;
    private UUID fxContractId;
    private Amount debitAmount;
    private Amount creditAmount;
    private Amount feeAmount;
    private LocalDate valueDate;
    private Beneficiary beneficiary;
    private PaymentStatus status;
}
```

### 2.3 Application Domain Services (ADS)

**AccountService.java**

```java
@Singleton
public class AccountService {
    private final AccountRepository accountRepo;

    @Transactional
    public Account createAccount(CreateAccountCommand cmd) {
        Account account = new Account(
            UUID.randomUUID(),
            cmd.customerId(),
            cmd.currencyCode(),
            generateAccountNumber(),
            cmd.transitNumber(),
            cmd.accountType(),
            BigDecimal.ZERO,
            cmd.isLimitBased()
        );
        accountRepo.save(account);
        return account;
    }

    @Transactional
    public Transaction createTransaction(CreateTransactionCommand cmd) {
        Account account = accountRepo.findById(cmd.accountId())
            .orElseThrow(() -> new AccountNotFoundException(cmd.accountId()));

        Transaction txn = account.createTransaction(
            cmd.transactionType(),
            cmd.amount(),
            cmd.description()
        );

        accountRepo.save(account);
        return txn;
    }

    @Transactional(readOnly = true)
    public Account getAccountById(UUID accountId) {
        return accountRepo.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));
    }
}
```

**LimitService.java**

```java
@Singleton
public class LimitService {
    private final LimitRepository limitRepo;

    @Transactional
    public void bookLimits(UUID accountId, BigDecimal amount) {
        List<AccountLimit> limits = limitRepo.findActiveByAccountId(accountId, Instant.now());

        // Try to book all limits - fail if any exceeds
        List<AccountLimit> booked = new ArrayList<>();
        try {
            for (AccountLimit limit : limits) {
                limit.book(amount);
                booked.add(limit);
            }
            // All succeeded - save
            booked.forEach(limitRepo::save);
        } catch (LimitExceededException e) {
            // Rollback already booked
            booked.forEach(limit -> limit.reverse(amount));
            throw e;
        }
    }

    @Transactional
    public void reverseLimits(UUID accountId, BigDecimal amount) {
        List<AccountLimit> limits = limitRepo.findActiveByAccountId(accountId, Instant.now());
        limits.forEach(limit -> {
            limit.reverse(amount);
            limitRepo.save(limit);
        });
    }
}
```

**FxService.java**

```java
@Singleton
public class FxService {
    private final FxContractRepository fxRepo;

    @Transactional
    public FxContract bookFx(BookFxCommand cmd) {
        // Validate customer exists (could be external call or local check)

        FxContract contract = new FxContract(
            UUID.randomUUID(),
            cmd.customerId(),
            cmd.debitAccountId(),
            cmd.debitAmount(),
            cmd.creditAmount(),
            cmd.rate(),
            cmd.valueDate(),
            FxStatus.BOOKED
        );

        fxRepo.save(contract);
        return contract;
    }

    @Transactional
    public void unwindFx(UUID fxContractId, String reason) {
        FxContract contract = fxRepo.findById(fxContractId)
            .orElseThrow(() -> new FxContractNotFoundException(fxContractId));

        contract.unwind(reason);
        fxRepo.save(contract);
    }
}
```

**PaymentService.java**

```java
@Singleton
public class PaymentService {
    private final PaymentRepository paymentRepo;

    @Transactional
    public Payment createPayment(CreatePaymentCommand cmd) {
        Payment payment = new Payment(
            cmd.paymentId(),
            cmd.debitAccountId(),
            cmd.debitTransactionId(),
            cmd.fxContractId(),
            cmd.debitAmount(),
            cmd.creditAmount(),
            cmd.feeAmount(),
            cmd.valueDate(),
            cmd.beneficiary(),
            PaymentStatus.SUBMITTED
        );

        paymentRepo.save(payment);
        return payment;
    }
}
```

### 2.4 Command Handlers

**CreateTransactionHandler.java**

```java
@Singleton
public class CreateTransactionHandler {
    private final AccountService accountService;
    private final InboxRepository inboxRepo;
    private final OutboxRepository outboxRepo;

    @JMSListener(destination = "APP.CMD.PAYMENTS.CreateTransaction.Q")
    @Transactional
    public void handle(@Body String message, MessageHeaders headers) {
        CommandEnvelope envelope = parseEnvelope(message);

        // Dedupe
        if (!inboxRepo.recordIfNew(envelope.messageId(), "CreateTransactionHandler")) {
            return; // Duplicate
        }

        try {
            CreateTransactionCommand cmd = parseCommand(envelope);
            Transaction txn = accountService.createTransaction(cmd);

            // Reply
            CommandReply reply = CommandReply.completed(
                envelope.commandId(),
                Map.of("transactionId", txn.transactionId())
            );
            outboxRepo.insert(OutboxRow.reply(
                envelope.headers().get("replyTo"),
                reply,
                envelope.correlationId()
            ));

            // Event
            TransactionCreatedEvent event = new TransactionCreatedEvent(
                txn.accountId(),
                txn.transactionId(),
                txn.type(),
                txn.amount()
            );
            outboxRepo.insert(OutboxRow.event(
                "events.Payments",
                txn.accountId().toString(),
                event
            ));
        } catch (Exception e) {
            CommandReply reply = CommandReply.failed(
                envelope.commandId(),
                e.getMessage()
            );
            outboxRepo.insert(OutboxRow.reply(
                envelope.headers().get("replyTo"),
                reply,
                envelope.correlationId()
            ));
        }
    }
}
```

### 2.5 Process Definition

**SubmitPaymentProcessDefinition.java**

```java
@Singleton
public class SubmitPaymentProcessDefinition implements ProcessDefinition {

    @Override
    public String getProcessType() {
        return "SubmitPayment";
    }

    @Override
    public String getInitialStep() {
        return "CreateTransaction";
    }

    @Override
    public Optional<String> getNextStep(String currentStep, Map<String, Object> data) {
        return switch (currentStep) {
            case "CreateTransaction" -> {
                boolean isLimitBased = (Boolean) data.get("isLimitBased");
                yield isLimitBased ? Optional.of("BookLimits") : Optional.of("BookFx");
            }
            case "BookLimits" -> Optional.of("BookFx");
            case "BookFx" -> Optional.of("CreatePayment");
            case "CreatePayment" -> Optional.empty(); // Done
            default -> throw new IllegalStateException("Unknown step: " + currentStep);
        };
    }

    @Override
    public boolean requiresCompensation(String step) {
        return switch (step) {
            case "BookLimits", "CreateTransaction", "BookFx" -> true;
            default -> false;
        };
    }

    @Override
    public Optional<String> getCompensationStep(String step) {
        return switch (step) {
            case "BookFx" -> Optional.of("UnwindFx");
            case "BookLimits" -> Optional.of("ReverseLimits");
            case "CreateTransaction" -> Optional.of("ReverseTransaction");
            default -> Optional.empty();
        };
    }

    @Override
    public boolean isRetryable(String step, String error) {
        // Transient errors are retryable
        return error.contains("timeout") ||
               error.contains("connection") ||
               error.contains("temporary");
    }

    @Override
    public int getMaxRetries(String step) {
        return 3; // Default retry count
    }
}
```

### 2.6 Database Schema

**V1__payments_schema.sql**

```sql
-- Value Objects
CREATE TABLE currency_code (
    code VARCHAR(3) PRIMARY KEY,
    description TEXT NOT NULL
);

CREATE TABLE transaction_type (
    code VARCHAR(50) PRIMARY KEY,
    description TEXT NOT NULL,
    direction VARCHAR(2) NOT NULL  -- 'CR' or 'DR'
);

-- Account aggregate
CREATE TABLE account (
    account_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    account_number VARCHAR(5) NOT NULL,
    transit_number VARCHAR(7) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    available_balance DECIMAL(19, 4) NOT NULL,
    is_limit_based BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_account_number ON account(account_number, transit_number);
CREATE INDEX idx_account_customer ON account(customer_id);

-- Transaction entity (part of Account aggregate)
CREATE TABLE account_transaction (
    transaction_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account(account_id),
    value_date DATE NOT NULL,
    action_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    transaction_type VARCHAR(50) NOT NULL,
    description TEXT,
    amount DECIMAL(19, 4) NOT NULL,
    balance DECIMAL(19, 4) NOT NULL,
    remittance_data TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transaction_account ON account_transaction(account_id, action_date);

-- Account Limit aggregate
CREATE TABLE account_limit_type (
    account_id UUID NOT NULL REFERENCES account(account_id),
    period_type VARCHAR(20) NOT NULL,  -- MINUTE, HOUR, DAY, WEEK, MONTH
    amount DECIMAL(19, 4) NOT NULL,
    PRIMARY KEY (account_id, period_type)
);

CREATE TABLE account_limit (
    limit_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account(account_id),
    period_type VARCHAR(20) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    limit_amount DECIMAL(19, 4) NOT NULL,
    utilized DECIMAL(19, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_limit_account_period ON account_limit(account_id, period_type, end_time);

-- FX Contract aggregate
CREATE TABLE fx_contract (
    fx_contract_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    debit_account_id UUID NOT NULL REFERENCES account(account_id),
    debit_currency VARCHAR(3) NOT NULL,
    debit_amount DECIMAL(19, 4) NOT NULL,
    credit_currency VARCHAR(3) NOT NULL,
    credit_amount DECIMAL(19, 4) NOT NULL,
    rate DECIMAL(19, 8) NOT NULL,
    value_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_fx_contract_debit_account ON fx_contract(debit_account_id);
CREATE INDEX idx_fx_contract_customer ON fx_contract(customer_id);

-- FX Contract Unwind (could be entity or event)
CREATE TABLE fx_contract_unwind (
    fx_contract_id UUID PRIMARY KEY REFERENCES fx_contract(fx_contract_id),
    unwind_reason TEXT NOT NULL,
    unwound_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Payment aggregate
CREATE TABLE payment (
    payment_id UUID PRIMARY KEY,
    debit_account_id UUID NOT NULL REFERENCES account(account_id),
    debit_transaction_id UUID NOT NULL REFERENCES account_transaction(transaction_id),
    fx_contract_id UUID REFERENCES fx_contract(fx_contract_id),
    debit_currency VARCHAR(3) NOT NULL,
    debit_amount DECIMAL(19, 4) NOT NULL,
    credit_currency VARCHAR(3) NOT NULL,
    credit_amount DECIMAL(19, 4) NOT NULL,
    fee_currency VARCHAR(3) NOT NULL,
    fee_amount DECIMAL(19, 4) NOT NULL DEFAULT 0,
    value_date DATE NOT NULL,
    beneficiary_bank_code VARCHAR(11) NOT NULL,  -- SWIFT BIC
    beneficiary_account_number TEXT NOT NULL,    -- IBAN
    beneficiary_name TEXT NOT NULL,
    beneficiary_address TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_debit_account ON payment(debit_account_id);
CREATE INDEX idx_payment_value_date ON payment(value_date);

-- Idempotency enforcement
CREATE TABLE payment_idempotency (
    idempotency_key TEXT PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payment(payment_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Inbox for this BC
CREATE TABLE inbox_bc (
    message_id TEXT NOT NULL,
    handler TEXT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(message_id, handler)
);

-- Outbox for this BC
CREATE TABLE outbox_bc (
    id BIGSERIAL PRIMARY KEY,
    category TEXT NOT NULL,         -- 'reply' | 'event'
    topic TEXT,
    key TEXT,
    type TEXT NOT NULL,
    payload JSONB NOT NULL,
    headers JSONB NOT NULL DEFAULT '{}',
    status TEXT NOT NULL DEFAULT 'NEW',
    attempts INT NOT NULL DEFAULT 0,
    next_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_bc_dispatch ON outbox_bc(status, COALESCE(next_at, 'epoch'::timestamptz), created_at);
```

---

## Phase 3: Testing & Validation

### 3.1 Unit Tests

- [ ] `ProcessManager` state transitions
- [ ] `ProcessDefinition` step logic
- [ ] Domain services (AccountService, LimitService, etc.)
- [ ] Command handlers with inbox dedupe

### 3.2 Integration Tests

- [ ] Process Manager with in-memory MQ
- [ ] Full orchestration: CreateTransaction → BookLimits → BookFx → CreatePayment
- [ ] Compensation flow: failure after BookFx triggers UnwindFx
- [ ] Retry logic for transient failures
- [ ] Idempotency: duplicate command delivery

### 3.3 E2E Tests (Testcontainers)

- [ ] Postgres + IBM MQ containers
- [ ] Happy path: submit payment, verify all steps
- [ ] Failure path: insufficient balance
- [ ] Limit exceeded path
- [ ] FX booking failure → compensation
- [ ] Timeout handling

---

## Future Enhancements

### Phase 4: Batch Support

- Add `batch_run` and `batch_item` tables
- Batch orchestrator that creates multiple process instances
- Dashboard for batch progress

### Phase 5: Multiple Bounded Contexts

- Extract Accounts BC
- Extract Limits BC
- Extract FX BC
- Each with their own database and workers

### Phase 6: Operator Tools

- Admin REST API for:
    - Resubmit failed process
    - Skip to next step
    - Force compensation
    - View process log
- Web UI for batch monitoring

### Phase 7: Advanced Features

- Process instance timeouts (watchdog)
- Dead Letter Queue (DLQ) handling
- Metrics and observability
- Process definition versioning

---

## Implementation Order

### Sprint 1: Foundation (Week 1)

1. Create domain objects in `msg-platform-core`
2. Create process tables + Flyway migration in `msg-platform-persistence-jdbc`
3. Implement `ProcessRepository` (JDBC)

### Sprint 2: Process Manager Engine (Week 2)

4. Implement `ProcessManager` core logic
5. Implement `ReplyConsumer`
6. Unit tests for process state machine

### Sprint 3: Payments Worker (Week 3)

7. Create `msg-platform-payments-worker` module
8. Implement domain model (Account, Payment, etc.)
9. Implement repositories (JDBC)
10. Implement Application Domain Services

### Sprint 4: Orchestration (Week 4)

11. Implement command handlers with inbox/outbox
12. Implement `SubmitPaymentProcessDefinition`
13. Integration tests

### Sprint 5: E2E Testing (Week 5)

14. E2E tests with Testcontainers
15. Performance testing
16. Documentation

---

## Success Criteria

- [ ] Generic Process Manager framework is reusable for any orchestration
- [ ] Payments worker successfully orchestrates: CreateTransaction → BookLimits → BookFx → CreatePayment
- [ ] Compensation works: failure after BookFx triggers UnwindFx
- [ ] Idempotency: duplicate deliveries don't cause duplicate side-effects
- [ ] All tests pass (unit, integration, E2E)
- [ ] Documentation complete

---

## Notes

- Keep it simple: start with single payment (no batch)
- Focus on correctness over performance initially
- Use existing outbox/inbox patterns - don't reinvent
- Make Process Manager generic enough for future use cases
- Clear separation: framework code vs business logic
