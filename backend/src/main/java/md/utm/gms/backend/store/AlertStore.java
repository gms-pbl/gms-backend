package md.utm.gms.backend.store;

import md.utm.gms.backend.api.dto.AlertResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PostgreSQL-backed alert store.
 */
@Component
public class AlertStore {

    private static final RowMapper<AlertResponse> ALERT_ROW_MAPPER = (rs, rowNum) ->
            AlertResponse.builder()
                    .id(rs.getString("id"))
                    .tenantId(rs.getString("tenant_id"))
                    .greenhouseId(rs.getString("greenhouse_id"))
                    .gatewayId(rs.getString("gateway_id"))
                    .zoneId(rs.getString("zone_id"))
                    .deviceId(rs.getString("device_id"))
                    .severity(rs.getString("severity"))
                    .sensorKey(rs.getString("sensor_key"))
                    .message(rs.getString("message"))
                    .source(rs.getString("source"))
                    .thresholdVersion(rs.getObject("threshold_version", Long.class))
                    .currentValue(rs.getObject("current_value", Double.class))
                    .thresholdMin(rs.getObject("threshold_min", Double.class))
                    .thresholdMax(rs.getObject("threshold_max", Double.class))
                    .triggeredAt(rs.getTimestamp("triggered_at").toInstant())
                    .acknowledged(rs.getBoolean("acknowledged"))
                    .build();

    private final JdbcTemplate jdbcTemplate;

    public AlertStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void add(AlertResponse alert) {
        if (alert == null || isBlank(alert.getId())) {
            return;
        }

        Instant triggeredAt = alert.getTriggeredAt() != null ? alert.getTriggeredAt() : Instant.now();
        String tenantId = defaultString(alert.getTenantId(), "tenant-demo");
        String greenhouseId = defaultString(alert.getGreenhouseId(), "greenhouse-demo");

        jdbcTemplate.update(
                """
                INSERT INTO gms.alert_event(
                    id,
                    tenant_id,
                    greenhouse_id,
                    gateway_id,
                    zone_id,
                    device_id,
                    severity,
                    sensor_key,
                    message,
                    source,
                    threshold_version,
                    current_value,
                    threshold_min,
                    threshold_max,
                    triggered_at,
                    acknowledged,
                    dismissed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
                ON CONFLICT (id)
                DO UPDATE SET
                    tenant_id = EXCLUDED.tenant_id,
                    greenhouse_id = EXCLUDED.greenhouse_id,
                    gateway_id = EXCLUDED.gateway_id,
                    zone_id = EXCLUDED.zone_id,
                    device_id = EXCLUDED.device_id,
                    severity = EXCLUDED.severity,
                    sensor_key = EXCLUDED.sensor_key,
                    message = EXCLUDED.message,
                    source = EXCLUDED.source,
                    threshold_version = EXCLUDED.threshold_version,
                    current_value = EXCLUDED.current_value,
                    threshold_min = EXCLUDED.threshold_min,
                    threshold_max = EXCLUDED.threshold_max,
                    triggered_at = EXCLUDED.triggered_at,
                    acknowledged = EXCLUDED.acknowledged,
                    dismissed_at = NULL
                """,
                alert.getId(),
                tenantId,
                greenhouseId,
                alert.getGatewayId(),
                alert.getZoneId(),
                alert.getDeviceId(),
                defaultString(alert.getSeverity(), "INFO"),
                alert.getSensorKey(),
                defaultString(alert.getMessage(), "Alert"),
                defaultString(alert.getSource(), "edge"),
                alert.getThresholdVersion(),
                alert.getCurrentValue(),
                alert.getThresholdMin(),
                alert.getThresholdMax(),
                Timestamp.from(triggeredAt),
                alert.isAcknowledged()
        );
    }

    public Optional<AlertResponse> acknowledge(String tenantId, String id) {
        int updated = jdbcTemplate.update(
                """
                UPDATE gms.alert_event
                SET acknowledged = TRUE
                WHERE tenant_id = ? AND id = ? AND dismissed_at IS NULL
                """,
                tenantId,
                id
        );
        if (updated == 0) {
            return Optional.empty();
        }
        return findById(tenantId, id);
    }

    public boolean dismiss(String tenantId, String id) {
        int updated = jdbcTemplate.update(
                """
                UPDATE gms.alert_event
                SET dismissed_at = NOW()
                WHERE tenant_id = ? AND id = ? AND dismissed_at IS NULL
                """,
                tenantId,
                id
        );
        return updated > 0;
    }

    public List<AlertResponse> getAll(String tenantId) {
        return jdbcTemplate.query(
                """
                SELECT id,
                       tenant_id,
                       greenhouse_id,
                       gateway_id,
                       zone_id,
                       device_id,
                       severity,
                       sensor_key,
                       message,
                       source,
                       threshold_version,
                       current_value,
                       threshold_min,
                       threshold_max,
                       triggered_at,
                       acknowledged
                FROM gms.alert_event
                WHERE tenant_id = ?
                  AND dismissed_at IS NULL
                ORDER BY acknowledged ASC,
                         CASE severity
                             WHEN 'CRITICAL' THEN 0
                             WHEN 'WARNING' THEN 1
                             WHEN 'INFO' THEN 2
                             ELSE 3
                         END,
                         triggered_at DESC
                """,
                ALERT_ROW_MAPPER,
                tenantId
        );
    }

    public List<AlertResponse> getAll() {
        return getAll("tenant-demo");
    }

    public Map<String, String> getLatestSeverityByScope(String tenantId,
                                                        String greenhouseId,
                                                        String zoneId) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT DISTINCT ON (greenhouse_id, zone_id, device_id, sensor_key)
                       greenhouse_id,
                       zone_id,
                       device_id,
                       sensor_key,
                       severity
                FROM gms.alert_event
                WHERE tenant_id = ?
                """
        );

        var args = new java.util.ArrayList<>();
        args.add(tenantId);

        if (!isBlank(greenhouseId)) {
            sql.append(" AND greenhouse_id = ?");
            args.add(greenhouseId.trim());
        }

        if (!isBlank(zoneId)) {
            sql.append(" AND zone_id = ?");
            args.add(zoneId.trim());
        }

        sql.append(
                """
                 ORDER BY greenhouse_id,
                          zone_id,
                          device_id,
                          sensor_key,
                          triggered_at DESC
                """
        );

        Map<String, String> latest = new HashMap<>();
        jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> Map.entry(
                        severityKey(
                                rs.getString("greenhouse_id"),
                                rs.getString("zone_id"),
                                rs.getString("device_id"),
                                rs.getString("sensor_key")
                        ),
                        normalizeSeverity(rs.getString("severity"))
                ),
                args.toArray()
        ).forEach((entry) -> latest.put(entry.getKey(), entry.getValue()));
        return latest;
    }

    private Optional<AlertResponse> findById(String tenantId, String id) {
        List<AlertResponse> results = jdbcTemplate.query(
                """
                SELECT id,
                       tenant_id,
                       greenhouse_id,
                       gateway_id,
                       zone_id,
                       device_id,
                       severity,
                       sensor_key,
                       message,
                       source,
                       threshold_version,
                       current_value,
                       threshold_min,
                       threshold_max,
                       triggered_at,
                       acknowledged
                FROM gms.alert_event
                WHERE tenant_id = ? AND id = ? AND dismissed_at IS NULL
                """,
                ALERT_ROW_MAPPER,
                tenantId,
                id
        );

        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(results.get(0));
    }

    public static String severityKey(String greenhouseId, String zoneId, String deviceId, String sensorKey) {
        return String.join("\u0000",
                defaultString(greenhouseId, ""),
                defaultString(zoneId, ""),
                defaultString(deviceId, ""),
                defaultString(sensorKey, ""));
    }

    private static String normalizeSeverity(String severity) {
        if (isBlank(severity)) {
            return "OK";
        }
        return switch (severity.trim().toUpperCase()) {
            case "CRITICAL" -> "CRITICAL";
            case "WARNING" -> "WARNING";
            default -> "OK";
        };
    }

    private static String defaultString(String value, String fallback) {
        if (isBlank(value)) {
            return fallback;
        }
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
