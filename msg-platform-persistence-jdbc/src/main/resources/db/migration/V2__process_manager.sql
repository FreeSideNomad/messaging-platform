-- Process Manager Tables
-- Process instance state (mutable, current state)
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
CREATE INDEX idx_process_instance_type_status ON process_instance(process_type, status);

COMMENT ON TABLE process_instance IS 'Current state of process orchestrations';
COMMENT ON COLUMN process_instance.process_type IS 'Type of process (e.g., SubmitPayment, OpenAccount)';
COMMENT ON COLUMN process_instance.business_key IS 'Business identifier (e.g., paymentId, accountId)';
COMMENT ON COLUMN process_instance.status IS 'NEW|RUNNING|SUCCEEDED|FAILED|COMPENSATING|COMPENSATED|PAUSED';
COMMENT ON COLUMN process_instance.current_step IS 'Current or last executed step name';
COMMENT ON COLUMN process_instance.data IS 'Working context for process execution';
COMMENT ON COLUMN process_instance.retries IS 'Number of retries for current step';

-- Immutable event log (event sourcing)
CREATE TABLE process_log (
    process_id UUID NOT NULL,
    seq BIGINT GENERATED ALWAYS AS IDENTITY,
    at TIMESTAMPTZ NOT NULL DEFAULT now(),
    event JSONB NOT NULL,
    PRIMARY KEY (process_id, seq)
);

CREATE INDEX idx_process_log_at ON process_log(at);
CREATE INDEX idx_process_log_process_id ON process_log(process_id);

COMMENT ON TABLE process_log IS 'Immutable event log for process audit trail';
COMMENT ON COLUMN process_log.seq IS 'Sequence number for ordering events';
COMMENT ON COLUMN process_log.event IS 'ProcessEvent as JSON (ProcessStarted, StepCompleted, etc.)';

-- Partition process_log by day for better performance and retention
-- (Can be enabled later for production)
-- CREATE TABLE process_log_2025_11 PARTITION OF process_log
-- FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');
