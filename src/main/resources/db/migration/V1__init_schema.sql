-- ── Enums ─────────────────────────────────────────────────────────────────────

CREATE TYPE notification_status AS ENUM (
    'PENDING',
    'PROCESSING',
    'DELIVERED',
    'FAILED'
);

CREATE TYPE channel AS ENUM (
    'EMAIL',
    'SMS'
);

CREATE TYPE delivery_status AS ENUM (
    'DELIVERED',
    'FAILED',
    'RETRYING'
);

-- ── api_keys ──────────────────────────────────────────────────────────────────

CREATE TABLE api_keys (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_hash      VARCHAR(64)  NOT NULL UNIQUE,
    name          VARCHAR(100) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_used_at  TIMESTAMPTZ
);

-- ── notification_jobs ─────────────────────────────────────────────────────────

CREATE TABLE notification_jobs (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_key_id           UUID         NOT NULL REFERENCES api_keys(id),
    idempotency_key      VARCHAR(255) NOT NULL UNIQUE,
    channels             channel[]    NOT NULL,
    status               notification_status NOT NULL DEFAULT 'PENDING',
    recipient_email      VARCHAR(255),
    recipient_phone      VARCHAR(20),
    subject              VARCHAR(500),
    body                 TEXT         NOT NULL,
    sns_message_id       VARCHAR(255),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ── delivery_attempts ─────────────────────────────────────────────────────────

CREATE TABLE delivery_attempts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id              UUID            NOT NULL REFERENCES notification_jobs(id),
    channel             channel         NOT NULL,
    status              delivery_status NOT NULL,
    attempt_number      INT             NOT NULL DEFAULT 1,
    delivered_at        TIMESTAMPTZ,
    failed_at           TIMESTAMPTZ,
    error_message       TEXT,
    provider_message_id VARCHAR(255)
);

-- ── Indexes ───────────────────────────────────────────────────────────────────

CREATE INDEX idx_notification_jobs_status     ON notification_jobs(status);
CREATE INDEX idx_notification_jobs_api_key_id ON notification_jobs(api_key_id);
CREATE INDEX idx_delivery_attempts_job_id     ON delivery_attempts(job_id);
