
DO $$ BEGIN
  CREATE TYPE outbox_status AS ENUM ('NEW','SENDING','PUBLISHED','FAILED');
EXCEPTION
  WHEN duplicate_object THEN null;
END $$;

ALTER TABLE IF EXISTS outbox RENAME TO outbox_old;

CREATE TABLE IF NOT EXISTS outbox (
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
  claimed_at   TIMESTAMPTZ,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS outbox_dispatch_idx
  ON outbox (status, COALESCE(next_at, 'epoch'::TIMESTAMPTZ), created_at);

CREATE INDEX IF NOT EXISTS outbox_claimed_idx
  ON outbox (status, claimed_at) WHERE status='SENDING';

INSERT INTO outbox (category, topic, key, type, payload, headers, status, attempts, next_at, created_at)
SELECT 
  category, 
  topic, 
  key, 
  type, 
  payload, 
  headers, 
  CASE 
    WHEN status = 'CLAIMED' THEN 'PUBLISHED'::outbox_status
    ELSE status::outbox_status
  END,
  attempts,
  next_at,
  created_at
FROM outbox_old
WHERE status IN ('NEW', 'PUBLISHED', 'CLAIMED');

DROP TABLE outbox_old;
