CREATE TABLE IF NOT EXISTS gms.gateway_status (
    tenant_id        TEXT        NOT NULL,
    greenhouse_id    TEXT        NOT NULL,
    gateway_id       TEXT        NOT NULL,
    status           TEXT        NOT NULL DEFAULT 'UNKNOWN',
    firmware_version TEXT,
    last_seen_at     TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, greenhouse_id, gateway_id)
);

CREATE INDEX IF NOT EXISTS idx_gateway_status_tenant_greenhouse
    ON gms.gateway_status (tenant_id, greenhouse_id, last_seen_at DESC);
