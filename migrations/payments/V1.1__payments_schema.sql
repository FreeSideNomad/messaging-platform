-- Payments Domain Schema and Extensions
-- Consolidated initial extension migration (V2 + V3 + V5)

-- Create payments schema for domain-specific tables
CREATE SCHEMA IF NOT EXISTS payments;

-- ========== V2: Process Manager ==========
-- Process instance state (mutable, current state)
CREATE TABLE payments.process_instance (
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

CREATE INDEX idx_process_instance_status ON payments.process_instance(status);
CREATE INDEX idx_process_instance_type_key ON payments.process_instance(process_type, business_key);
CREATE INDEX idx_process_instance_updated ON payments.process_instance(updated_at);
CREATE INDEX idx_process_instance_type_status ON payments.process_instance(process_type, status);

COMMENT ON TABLE payments.process_instance IS 'Current state of process orchestrations';
COMMENT ON COLUMN payments.process_instance.process_type IS 'Type of process (e.g., SubmitPayment, OpenAccount)';
COMMENT ON COLUMN payments.process_instance.business_key IS 'Business identifier (e.g., paymentId, accountId)';
COMMENT ON COLUMN payments.process_instance.status IS 'NEW|RUNNING|SUCCEEDED|FAILED|COMPENSATING|COMPENSATED|PAUSED';
COMMENT ON COLUMN payments.process_instance.current_step IS 'Current or last executed step name';
COMMENT ON COLUMN payments.process_instance.data IS 'Working context for process execution';
COMMENT ON COLUMN payments.process_instance.retries IS 'Number of retries for current step';

-- Immutable event log (event sourcing)
CREATE TABLE payments.process_log (
    process_id UUID NOT NULL,
    seq BIGINT GENERATED ALWAYS AS IDENTITY,
    at TIMESTAMPTZ NOT NULL DEFAULT now(),
    event JSONB NOT NULL,
    PRIMARY KEY (process_id, seq)
);

CREATE INDEX idx_process_log_at ON payments.process_log(at);
CREATE INDEX idx_process_log_process_id ON payments.process_log(process_id);

COMMENT ON TABLE payments.process_log IS 'Immutable event log for process audit trail';
COMMENT ON COLUMN payments.process_log.seq IS 'Sequence number for ordering events';
COMMENT ON COLUMN payments.process_log.event IS 'ProcessEvent as JSON (ProcessStarted, StepCompleted, etc.)';

-- ========== V3: Payments Schema ==========
-- Account aggregate
CREATE TABLE payments.account (
    account_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    account_number VARCHAR(20) NOT NULL UNIQUE,
    currency_code VARCHAR(3) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    transit_number VARCHAR(20) NOT NULL,
    limit_based BOOLEAN NOT NULL DEFAULT false,
    available_balance DECIMAL(19, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_account_number ON payments.account(account_number);
CREATE INDEX idx_account_customer ON payments.account(customer_id);

-- Transaction entity (part of Account aggregate)
CREATE TABLE payments.transaction (
    transaction_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES payments.account(account_id),
    transaction_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    transaction_type VARCHAR(20) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    description TEXT,
    balance DECIMAL(19, 4) NOT NULL
);

CREATE INDEX idx_transaction_account ON payments.transaction(account_id, transaction_date DESC);

-- Account Limit aggregate
CREATE TABLE payments.account_limit (
    limit_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES payments.account(account_id),
    period_type VARCHAR(20) NOT NULL,  -- MINUTE, HOUR, DAY, WEEK, MONTH
    limit_amount DECIMAL(19, 4) NOT NULL,
    utilized DECIMAL(19, 4) NOT NULL DEFAULT 0,
    currency_code VARCHAR(3) NOT NULL,
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_limit_account_period ON payments.account_limit(account_id, period_type, period_end);

-- FX Contract aggregate
CREATE TABLE payments.fx_contract (
    fx_contract_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    debit_account_id UUID NOT NULL REFERENCES payments.account(account_id),
    debit_amount DECIMAL(19, 4) NOT NULL,
    debit_currency_code VARCHAR(3) NOT NULL,
    credit_amount DECIMAL(19, 4) NOT NULL,
    credit_currency_code VARCHAR(3) NOT NULL,
    rate DECIMAL(19, 8) NOT NULL,
    value_date TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_fx_contract_debit_account ON payments.fx_contract(debit_account_id);
CREATE INDEX idx_fx_contract_customer ON payments.fx_contract(customer_id);

-- Payment aggregate
CREATE TABLE payments.payment (
    payment_id UUID PRIMARY KEY,
    debit_account_id UUID NOT NULL REFERENCES payments.account(account_id),
    debit_transaction_id UUID REFERENCES payments.transaction(transaction_id),
    fx_contract_id UUID REFERENCES payments.fx_contract(fx_contract_id),
    debit_amount DECIMAL(19, 4) NOT NULL,
    debit_currency_code VARCHAR(3) NOT NULL,
    credit_amount DECIMAL(19, 4) NOT NULL,
    credit_currency_code VARCHAR(3) NOT NULL,
    value_date TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    beneficiary_name TEXT NOT NULL,
    beneficiary_account_number TEXT NOT NULL,
    beneficiary_transit_number TEXT,
    beneficiary_bank_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_debit_account ON payments.payment(debit_account_id);
CREATE INDEX idx_payment_status ON payments.payment(status, created_at);

COMMENT ON TABLE payments.account IS 'Customer payment accounts';
COMMENT ON TABLE payments.transaction IS 'Account transactions (debits/credits)';
COMMENT ON TABLE payments.account_limit IS 'Transaction limits per period';
COMMENT ON TABLE payments.fx_contract IS 'Foreign exchange contracts';
COMMENT ON TABLE payments.payment IS 'Payment submissions';
