ALTER TABLE gms.zone_threshold
    ADD COLUMN IF NOT EXISTS config_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS updated_by TEXT,
    ADD COLUMN IF NOT EXISTS last_command_id TEXT;

ALTER TABLE gms.command_ack
    ADD COLUMN IF NOT EXISTS config_version BIGINT;

UPDATE gms.zone_threshold
SET thresholds = (
    SELECT COALESCE(jsonb_object_agg(
        CASE key
            WHEN 'air_temperature' THEN 'air_temp'
            WHEN 'air_humidity' THEN 'air_hum'
            WHEN 'soil_moisture' THEN 'soil_moist'
            WHEN 'soil_ec' THEN 'soil_cond'
            WHEN 'soil_nitrogen' THEN 'soil_n'
            WHEN 'soil_phosphorus' THEN 'soil_p'
            WHEN 'soil_potassium' THEN 'soil_k'
            ELSE key
        END,
        value
    ), '{}'::jsonb)
    FROM jsonb_each(thresholds)
)
WHERE thresholds ?| ARRAY[
    'air_temperature',
    'air_humidity',
    'soil_moisture',
    'soil_ec',
    'soil_nitrogen',
    'soil_phosphorus',
    'soil_potassium'
];

CREATE TABLE IF NOT EXISTS gms.threshold_apply_status (
    tenant_id TEXT NOT NULL,
    greenhouse_id TEXT NOT NULL,
    zone_id TEXT NOT NULL,
    gateway_id TEXT NOT NULL,
    config_version BIGINT NOT NULL,
    command_id TEXT NOT NULL,
    status TEXT NOT NULL,
    reason TEXT,
    ack_timestamp TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, greenhouse_id, zone_id, gateway_id, config_version)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_threshold_apply_command
    ON gms.threshold_apply_status (command_id);

CREATE INDEX IF NOT EXISTS idx_threshold_apply_scope_latest
    ON gms.threshold_apply_status (tenant_id, greenhouse_id, zone_id, updated_at DESC);

ALTER TABLE gms.alert_event
    ADD COLUMN IF NOT EXISTS gateway_id TEXT,
    ADD COLUMN IF NOT EXISTS zone_id TEXT,
    ADD COLUMN IF NOT EXISTS device_id TEXT,
    ADD COLUMN IF NOT EXISTS source TEXT NOT NULL DEFAULT 'edge',
    ADD COLUMN IF NOT EXISTS threshold_version BIGINT,
    ADD COLUMN IF NOT EXISTS current_value DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS threshold_min DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS threshold_max DOUBLE PRECISION;

CREATE INDEX IF NOT EXISTS idx_alert_event_context
    ON gms.alert_event (tenant_id, greenhouse_id, zone_id, device_id, triggered_at DESC);
