-- Payments Domain Schema
-- Minimal implementation for Process Manager demonstration

-- Account aggregate
CREATE TABLE account (
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

CREATE UNIQUE INDEX idx_account_number ON account(account_number);
CREATE INDEX idx_account_customer ON account(customer_id);

-- Transaction entity (part of Account aggregate)
CREATE TABLE transaction (
    transaction_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account(account_id),
    transaction_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    transaction_type VARCHAR(20) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    description TEXT,
    balance DECIMAL(19, 4) NOT NULL
);

CREATE INDEX idx_transaction_account ON transaction(account_id, transaction_date DESC);

-- Account Limit aggregate
CREATE TABLE account_limit (
    limit_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account(account_id),
    period_type VARCHAR(20) NOT NULL,  -- MINUTE, HOUR, DAY, WEEK, MONTH
    limit_amount DECIMAL(19, 4) NOT NULL,
    utilized DECIMAL(19, 4) NOT NULL DEFAULT 0,
    currency_code VARCHAR(3) NOT NULL,
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_limit_account_period ON account_limit(account_id, period_type, period_end);

-- FX Contract aggregate
CREATE TABLE fx_contract (
    fx_contract_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    debit_account_id UUID NOT NULL REFERENCES account(account_id),
    debit_amount DECIMAL(19, 4) NOT NULL,
    debit_currency_code VARCHAR(3) NOT NULL,
    credit_amount DECIMAL(19, 4) NOT NULL,
    credit_currency_code VARCHAR(3) NOT NULL,
    rate DECIMAL(19, 8) NOT NULL,
    value_date TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_fx_contract_debit_account ON fx_contract(debit_account_id);
CREATE INDEX idx_fx_contract_customer ON fx_contract(customer_id);

-- Payment aggregate
CREATE TABLE payment (
    payment_id UUID PRIMARY KEY,
    debit_account_id UUID NOT NULL REFERENCES account(account_id),
    debit_transaction_id UUID REFERENCES transaction(transaction_id),
    fx_contract_id UUID REFERENCES fx_contract(fx_contract_id),
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

CREATE INDEX idx_payment_debit_account ON payment(debit_account_id);
CREATE INDEX idx_payment_status ON payment(status, created_at);

-- Inbox for this BC (idempotent message processing)
CREATE TABLE inbox_bc (
    message_id TEXT NOT NULL,
    handler TEXT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(message_id, handler)
);

-- Outbox for this BC (reliable replies/events)
CREATE TABLE outbox_bc (
    id BIGSERIAL PRIMARY KEY,
    category TEXT NOT NULL,
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

COMMENT ON TABLE account IS 'Customer payment accounts';
COMMENT ON TABLE transaction IS 'Account transactions (debits/credits)';
COMMENT ON TABLE account_limit IS 'Transaction limits per period';
COMMENT ON TABLE fx_contract IS 'Foreign exchange contracts';
COMMENT ON TABLE payment IS 'Payment submissions';
COMMENT ON TABLE inbox_bc IS 'Inbox for idempotent message processing';
COMMENT ON TABLE outbox_bc IS 'Outbox for reliable replies and events';
