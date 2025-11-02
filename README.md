# Messaging Platform - Scalable Reliable Messaging

Multi-module messaging platform with separate API and Worker components for horizontal scaling.

## Architecture

```
Client → API (8080) → Database + MQ → Worker(s) (9090, 9091, 9092...)
                                          ├─> Process Command
                                          ├─> Publish Reply (MQ)
                                          └─> Publish Event (Kafka)
```

## Modules

### msg-platform-core
Shared domain logic, entities, repositories, and services.
- **Domain Entities**: Command, Inbox, Outbox, DLQ
- **Repositories**: CommandRepository, InboxRepository, OutboxRepository, DlqRepository
- **Services**: CommandService, InboxService, OutboxService, DlqService
- **Core Logic**: CommandBus, Executor, Outbox, FastPathPublisher
- **Configuration**: TimeoutConfig, MessagingConfig
- **Database Migrations**: Flyway migrations

### msg-platform-api
REST API for accepting commands and publishing to MQ.
- **Controllers**: CommandController (REST endpoints)
- **JMS**: Producer only (sends commands to MQ queues)
- **Thread Pool**: Optimized for HTTP concurrency (200 worker threads)
- **DB Pool**: Smaller (100 connections - only writes commands/outbox)
- **JMS Consumers**: **DISABLED** (no command processing)
- **No Kafka**: Events published asynchronously by Worker

### msg-platform-worker
Worker for processing commands from MQ and publishing replies/events.
- **JMS**: Consumer enabled (receives commands from MQ)
- **Handlers**: CreateUserHandler and other command handlers
- **OutboxRelay**: Background job to sweep unpublished outbox entries
- **Kafka**: Producer for events
- **Thread Pool**: Minimal HTTP (50 threads - only health checks)
- **DB Pool**: Larger (200 connections - heavy read/write)
- **JMS Consumers**: **ENABLED** (processes commands)

## Building

```bash
# Build all modules
cd messaging-platform
mvn clean install

# Build specific module
cd msg-platform-api
mvn clean package
```

## Running

### Prerequisites

Start infrastructure:
```bash
cd ../reliable-messaging  # Original project has docker-compose.yml
docker-compose up -d
```

Wait for services to be ready (~30-40 seconds for IBM MQ).

### Start API

```bash
cd msg-platform-api
cp .env.example .env
# Edit .env if needed
mvn mn:run
```

API will start on http://localhost:8080

### Start Worker (Single Instance)

```bash
cd msg-platform-worker
cp .env.example .env
# Edit .env if needed
mvn mn:run
```

Worker will start on http://localhost:9090

### Start Multiple Workers

**Terminal 1 (Worker 1):**
```bash
cd msg-platform-worker
WORKER_ID=worker-1 WORKER_PORT=9091 mvn mn:run
```

**Terminal 2 (Worker 2):**
```bash
cd msg-platform-worker
WORKER_ID=worker-2 WORKER_PORT=9092 mvn mn:run
```

**Terminal 3 (Worker 3):**
```bash
cd msg-platform-worker
WORKER_ID=worker-3 WORKER_PORT=9093 mvn mn:run
```

## Testing

### Submit a Command via API

```bash
curl -X POST http://localhost:8080/commands/CreateUser \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: test-123' \
  -d '{"username":"testuser"}'
```

Response:
```json
{
  "message": "Command accepted, processing asynchronously"
}
```

### Check Processing

```bash
# Check command status in database
docker exec reliable-postgres psql -U postgres -d reliable -c \
  "SELECT id, name, status FROM command WHERE idempotency_key = 'test-123';"

# Check outbox entries
docker exec reliable-postgres psql -U postgres -d reliable -c \
  "SELECT category, topic, status FROM outbox WHERE key = '<business_key>';"
```

## Scaling

### Horizontal Scaling Pattern

- **API**: 1 instance handles HTTP traffic
- **Workers**: N instances process commands in parallel from MQ

### Load Distribution

IBM MQ automatically distributes messages across multiple worker instances:
- Each message is delivered to only ONE worker (load balancing)
- Workers compete for messages (first-come-first-served)
- No coordination needed between workers

### Recommended Configuration

| Load | API Instances | Worker Instances | Total TPS |
|------|---------------|------------------|-----------|
| Low | 1 | 1 | 100-200 |
| Medium | 1 | 2-3 | 400-600 |
| High | 1-2 | 4-6 | 800-1200 |
| Very High | 2-3 | 8-12 | 2000+ |

## Configuration

### API Configuration (.env)

```bash
API_PORT=8080
NETTY_WORKER_THREADS=200       # High for HTTP concurrency
HIKARI_MAX_POOL_SIZE=100       # Smaller pool (only writes)
JMS_CONSUMERS_ENABLED=false    # NO consumers in API
SYNC_WAIT_DURATION=0s          # Async mode (return 202 immediately)
```

### Worker Configuration (.env)

```bash
WORKER_ID=worker-1             # Unique ID for each worker
WORKER_PORT=9090               # Unique port for each worker
NETTY_WORKER_THREADS=50        # Minimal (only health checks)
HIKARI_MAX_POOL_SIZE=200       # Larger pool (heavy DB access)
JMS_CONSUMERS_ENABLED=true     # YES consumers in Worker
JMS_CONCURRENCY=10             # Concurrent message processing
OUTBOX_SWEEP_INTERVAL=1s       # Fast sweep for failures
```

## Health Checks

### API Health
```bash
curl http://localhost:8080/health
```

### Worker Health
```bash
# Worker 1
curl http://localhost:9091/health

# Worker 2
curl http://localhost:9092/health
```

## Monitoring

### Database Queries

```bash
# Command status
docker exec reliable-postgres psql -U postgres -d reliable -c \
  "SELECT status, COUNT(*) FROM command GROUP BY status;"

# Outbox status
docker exec reliable-postgres psql -U postgres -d reliable -c \
  "SELECT status, COUNT(*) FROM outbox GROUP BY status;"

# Worker activity (by claimed_by)
docker exec reliable-postgres psql -U postgres -d reliable -c \
  "SELECT claimed_by, COUNT(*) FROM outbox WHERE status='CLAIMED' GROUP BY claimed_by;"
```

### MQ Monitoring

```bash
# Queue depth
echo "DISPLAY QLOCAL('APP.CMD.CreateUser.Q') CURDEPTH" | \
  docker exec -i reliable-ibmmq /opt/mqm/bin/runmqsc QM1
```

## Deployment

### Docker Compose (Multi-Worker)

See `docker-compose.yml` in this directory for full setup with:
- 1 API instance
- 3 Worker instances
- PostgreSQL
- IBM MQ
- Kafka

```bash
docker-compose up -d
```

### Kubernetes

Example deployment configuration in `k8s/`:
- `api-deployment.yaml` - 2 API replicas
- `worker-deployment.yaml` - 5 Worker replicas (auto-scaling)
- HPA for workers based on queue depth

## Troubleshooting

### API Not Starting
- Check PostgreSQL is running
- Check IBM MQ is accessible
- Verify port 8080 is free

### Worker Not Processing
- Check JMS_CONSUMERS_ENABLED=true
- Check IBM MQ channel is running
- Check queue has messages
- Check worker logs for errors

### Messages Stuck in Queue
- Check all workers are running
- Check workers have DB connectivity
- Check for DLQ entries (failed commands)

### Performance Issues
- Increase number of workers
- Increase JMS_CONCURRENCY per worker
- Increase HIKARI_MAX_POOL_SIZE
- Check thread limits (see THREAD-TUNING.md in original project)

## Migration from Monolith

This platform is the v2.0 refactored version of the original `/reliable-messaging` monolith (v1.0).

To migrate:
1. Keep v1.0 running
2. Deploy v2.0 API + Workers in parallel
3. Switch traffic to v2.0 API
4. Verify workers processing correctly
5. Decommission v1.0 monolith

## Benefits of This Architecture

✅ **Horizontal Scaling**: Add workers to increase throughput
✅ **Resource Optimization**: API optimized for HTTP, Workers for processing
✅ **Fault Isolation**: Worker failure doesn't affect API
✅ **Independent Deployment**: Deploy API and Workers separately
✅ **Clear Boundaries**: REST → Queue → Process → Reply
✅ **Load Balancing**: MQ automatically distributes work across workers
✅ **No Coordination**: Workers are stateless and independent

## Performance

With this architecture:
- **API**: Handles 1000+ req/s with 200 worker threads
- **Worker**: Each processes 200-300 TPS
- **Total**: Linear scaling with number of workers
  - 1 worker = 200-300 TPS
  - 3 workers = 600-900 TPS
  - 6 workers = 1200-1800 TPS
  - 12 workers = 2400+ TPS

## License

Same as parent project.
