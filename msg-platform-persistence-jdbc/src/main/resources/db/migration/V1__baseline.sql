-- Baseline schema for reliable commands & events framework

create type command_status as enum ('PENDING','RUNNING','SUCCEEDED','FAILED','TIMED_OUT');
create type outbox_status as enum ('NEW','SENDING','PUBLISHED','FAILED');

create table command (
  id uuid primary key,
  name text not null,
  business_key text not null,
  payload jsonb not null,
  idempotency_key text not null,
  status command_status not null,
  requested_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  retries int not null default 0,
  processing_lease_until timestamptz,
  last_error text,
  reply jsonb not null default '{}'::jsonb,
  unique (name, business_key),
  unique (idempotency_key)
);

create table inbox (
  message_id text not null,
  handler text not null,
  processed_at timestamptz not null default now(),
  primary key (message_id, handler)
);

create table outbox (
  id           BIGSERIAL PRIMARY KEY,
  category     TEXT NOT NULL,
  topic        TEXT,
  key          TEXT,
  type         TEXT NOT NULL,
  payload      JSONB NOT NULL,
  headers      JSONB NOT NULL DEFAULT '{}'::JSONB,
  status       outbox_status NOT NULL DEFAULT 'NEW',
  attempts     INT NOT NULL DEFAULT 0,
  next_at      TIMESTAMPTZ,
  claimed_by   TEXT,
  claimed_at   TIMESTAMPTZ,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  published_at TIMESTAMPTZ,
  last_error   TEXT
);

create index outbox_dispatch_idx on outbox (status, coalesce(next_at, 'epoch'::timestamptz), created_at);
create index outbox_claimed_idx on outbox (status, claimed_at) where status='SENDING';

create table command_dlq (
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
