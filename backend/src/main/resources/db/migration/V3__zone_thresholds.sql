CREATE TABLE IF NOT EXISTS gms.zone_threshold (
    tenant_id     TEXT        NOT NULL,
    greenhouse_id TEXT        NOT NULL,
    zone_id       TEXT        NOT NULL,
    thresholds    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, greenhouse_id, zone_id)
);
