# Messaging Platform

High-performance messaging platform with saga orchestration, transactional outbox pattern, and payments microservice.

## 🎯 Features

- **Saga Orchestration**: Distributed transaction management with compensation
- **Transactional Outbox**: Reliable message publishing with guaranteed delivery
- **Process Manager**: Stateful workflow engine with retry and error handling
- **Payments Microservice**: Complete payment processing with FX support
- **Clean Architecture**: Domain-driven design with framework-agnostic core
- **High Performance**: 1,000 TPS baseline with horizontal scalability

## 📦 Modules

### Core Modules
- **msg-platform-core**: Framework-agnostic domain models and interfaces
- **msg-platform-processor**: Transactional command processing and saga orchestration
- **msg-platform-persistence-jdbc**: JDBC-based repository implementations
- **msg-platform-messaging-ibmmq**: IBM MQ integration for reliable messaging

### Services
- **msg-platform-api**: REST API for command submission
- **msg-platform-worker**: Generic command processing workers
- **msg-platform-payments-worker**: Payments domain microservice

## 🧪 Test Coverage

**55 tests passing** across all test suites:

### Unit Tests (33 tests)
- AccountService: 8 tests
- PaymentService: 8 tests
- SimplePaymentProcessDefinition: 17 tests

### Integration Tests (17 tests)
- JdbcAccountRepository: 11 tests with Testcontainers
- JdbcPaymentRepository: 6 tests with Testcontainers

### E2E Tests (5 tests)
- Complete payment flows
- Account creation and management
- FX payment processing
- Multi-account scenarios

## 🏗️ Architecture

### Domain Model (Payments)
```
Account
├── Transactions (credit/debit)
├── Account Limits (period-based)
└── Balance Management

Payment
├── Debit/Credit Amounts
├── FX Contract (optional)
├── Beneficiary Information
└── Status Tracking
```

### Saga Orchestration
```
Process Manager
├── Process Graph (DSL)
├── Step Execution
├── Compensation Logic
└── Retry Handling
```

### Infrastructure Patterns
- Transactional Outbox
- Inbox for idempotency
- Dead Letter Queue
- Command Registry with auto-discovery

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker & Docker Compose
- PostgreSQL 16
- IBM MQ 9.4

### Run Tests
```bash
# Unit tests
mvn test -pl msg-platform-payments-worker -Dtest=*Test

# Integration tests
mvn test -pl msg-platform-payments-worker -Dtest=*IntegrationTest

# E2E tests
mvn test -pl msg-platform-payments-worker -Dtest=*E2ETest

# All tests
mvn test
```

### Run Infrastructure
```bash
docker-compose up -d
```

### Build
```bash
mvn clean install
```

## 📊 Performance

- **Baseline**: 1,000 TPS
- **Scalability**: Horizontal scaling with multiple workers
- **Reliability**: Transactional outbox ensures no message loss
- **Resilience**: Automatic retry with exponential backoff

## 🏛️ Design Patterns

- **Saga Pattern**: Distributed transactions with compensation
- **Outbox Pattern**: Reliable event publishing
- **Process Manager**: Stateful workflow coordination
- **Repository Pattern**: Clean data access abstraction
- **Factory Pattern**: DI boundary management
- **Command Pattern**: Decoupled command execution

## 📝 Key Technologies

- **Framework**: Micronaut 4.10
- **Database**: PostgreSQL 16
- **Messaging**: IBM MQ 9.4
- **Testing**: JUnit 5, Testcontainers, Mockito, AssertJ
- **Migrations**: Flyway
- **Build**: Maven

## 🔧 Configuration

See `application.yml` in each module for configuration options.

Key configurations:
- Database connection pooling
- Message queue settings
- Process manager tuning
- Timeout configurations

## 📚 Documentation

- [Implementation Guide](IMPLEMENTATION-COMPLETE-GUIDE.md)
- [Process Manager Status](PROCESS-MANAGER-STATUS.md)
- [Testing Summary](TESTING-COMPLETE-SUMMARY.md)

## 🤝 Contributing

This is a reference implementation demonstrating enterprise messaging patterns.

## 📄 License

MIT License - See LICENSE file for details

---

🤖 Built with [Claude Code](https://claude.com/claude-code)
