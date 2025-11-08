-- Baseline schema for reliable commands & events framework
-- Consolidated initial migration (V1 + V2 + V4)

-- Create platform schema for messaging infrastructure
CREATE SCHEMA IF NOT EXISTS platform;

-- ========== V1: Baseline ==========
-- Create types first
CREATE TYPE platform.command_status AS ENUM ('PENDING','RUNNING','SUCCEEDED','FAILED','TIMED_OUT');
CREATE TYPE platform.outbox_status AS ENUM ('NEW','SENDING','PUBLISHED','FAILED');

-- Create tables
CREATE TABLE platform.command (
  id uuid primary key,
  name text not null,
  business_key text not null,
  payload jsonb not null,
  idempotency_key text not null,
  status platform.command_status not null,
  requested_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  retries int not null default 0,
  processing_lease_until timestamptz,
  last_error text,
  reply jsonb not null default '{}'::jsonb,
  unique (name, business_key),
  unique (idempotency_key)
);

create table platform.inbox (
  message_id text not null,
  handler text not null,
  processed_at timestamptz not null default now(),
  primary key (message_id, handler)
);

create table platform.outbox (
  id           BIGSERIAL PRIMARY KEY,
  category     TEXT NOT NULL,
  topic        TEXT,
  key          TEXT,
  type         TEXT NOT NULL,
  payload      JSONB NOT NULL,
  headers      JSONB NOT NULL DEFAULT '{}'::JSONB,
  status       platform.outbox_status NOT NULL DEFAULT 'NEW',
  attempts     INT NOT NULL DEFAULT 0,
  next_at      TIMESTAMPTZ,
  claimed_at   TIMESTAMPTZ,
  claimed_by   TEXT,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  published_at TIMESTAMPTZ,
  last_error   TEXT
);

create index outbox_dispatch_idx on platform.outbox (status, coalesce(next_at, 'epoch'::timestamptz), created_at);
create index outbox_claimed_idx on platform.outbox (status, claimed_at) WHERE status='SENDING';

create table platform.command_dlq (
  id uuid primary key default gen_random_uuid(),
  command_id uuid not null,
  command_name text not null,
  business_key text not null,
  payload jsonb not null,
  failed_status text not null,
  error_class text not null,
  error_message text,
  attempts int not null default 0,
  parked_by text not null,
  parked_at timestamptz not null default now()
);

-- ========== V2: Process Manager ==========
-- Process instance state (mutable, current state)
CREATE TABLE platform.process_instance (
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

CREATE INDEX idx_process_instance_status ON platform.process_instance(status);
CREATE INDEX idx_process_instance_type_key ON platform.process_instance(process_type, business_key);
CREATE INDEX idx_process_instance_updated ON platform.process_instance(updated_at);
CREATE INDEX idx_process_instance_type_status ON platform.process_instance(process_type, status);

COMMENT ON TABLE platform.process_instance IS 'Current state of process orchestrations';
COMMENT ON COLUMN platform.process_instance.process_type IS 'Type of process (e.g., SubmitPayment, OpenAccount)';
COMMENT ON COLUMN platform.process_instance.business_key IS 'Business identifier (e.g., paymentId, accountId)';
COMMENT ON COLUMN platform.process_instance.status IS 'NEW|RUNNING|SUCCEEDED|FAILED|COMPENSATING|COMPENSATED|PAUSED';
COMMENT ON COLUMN platform.process_instance.current_step IS 'Current or last executed step name';
COMMENT ON COLUMN platform.process_instance.data IS 'Working context for process execution';
COMMENT ON COLUMN platform.process_instance.retries IS 'Number of retries for current step';

-- Immutable event log (event sourcing)
CREATE TABLE platform.process_log (
    process_id UUID NOT NULL,
    seq BIGINT GENERATED ALWAYS AS IDENTITY,
    at TIMESTAMPTZ NOT NULL DEFAULT now(),
    event JSONB NOT NULL,
    PRIMARY KEY (process_id, seq)
);

CREATE INDEX idx_process_log_at ON platform.process_log(at);
CREATE INDEX idx_process_log_process_id ON platform.process_log(process_id);

COMMENT ON TABLE platform.process_log IS 'Immutable event log for process audit trail';
COMMENT ON COLUMN platform.process_log.seq IS 'Sequence number for ordering events';
COMMENT ON COLUMN platform.process_log.event IS 'ProcessEvent as JSON (ProcessStarted, StepCompleted, etc.)';
