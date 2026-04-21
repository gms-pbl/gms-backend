package md.utm.gms.backend.store;

import md.utm.gms.backend.api.dto.SensorHistoryPoint;
import md.utm.gms.backend.api.dto.SensorHistoryResponse;
import md.utm.gms.backend.api.dto.SensorReadingResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL + TimescaleDB-backed sensor reading store.
 */
@Component
public class SensorReadingStore {

    private static final RowMapper<SensorReadingResponse> READING_ROW_MAPPER = (rs, rowNum) ->
            SensorReadingResponse.builder()
                    .sensorKey(rs.getString("sensor_key"))
                    .tenantId(rs.getString("tenant_id"))
                    .greenhouseId(rs.getString("greenhouse_id"))
                    .zoneId(rs.getString("zone_id"))
                    .deviceId(rs.getString("device_id"))
                    .value(rs.getDouble("value"))
                    .unit(rs.getString("unit"))
                    .status(rs.getString("status"))
                    .lastUpdatedAt(rs.getTimestamp("last_updated_at").toInstant())
                    .build();

    private final JdbcTemplate jdbcTemplate;

    public SensorReadingStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void update(SensorReadingResponse reading) {
        if (reading == null || isBlank(reading.getSensorKey())) {
            return;
        }

        Instant observedAt = reading.getLastUpdatedAt() != null ? reading.getLastUpdatedAt() : Instant.now();
        String tenantId = defaultString(reading.getTenantId(), "tenant-demo");
        String greenhouseId = defaultString(reading.getGreenhouseId(), "greenhouse-demo");
        String deviceId = defaultString(reading.getDeviceId(), "device-default");
        String zoneId = defaultString(reading.getZoneId(), deviceId);
        String unit = defaultString(reading.getUnit(), "raw");
        String status = defaultString(reading.getStatus(), "OK");

        jdbcTemplate.update(
                """
                INSERT INTO gms.telemetry_reading(
                    tenant_id,
                    greenhouse_id,
                    zone_id,
                    device_id,
                    sensor_key,
                    value,
                    unit,
                    status,
                    observed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                tenantId,
                greenhouseId,
                zoneId,
                deviceId,
                reading.getSensorKey(),
                reading.getValue(),
                unit,
                status,
                Timestamp.from(observedAt)
        );

        jdbcTemplate.update(
                """
                INSERT INTO gms.latest_metric(
                    tenant_id,
                    greenhouse_id,
                    zone_id,
                    device_id,
                    sensor_key,
                    value,
                    unit,
                    status,
                    last_updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, greenhouse_id, device_id, sensor_key)
                DO UPDATE SET
                    zone_id = EXCLUDED.zone_id,
                    value = EXCLUDED.value,
                    unit = EXCLUDED.unit,
                    status = EXCLUDED.status,
                    last_updated_at = EXCLUDED.last_updated_at
                """,
                tenantId,
                greenhouseId,
                zoneId,
                deviceId,
                reading.getSensorKey(),
                reading.getValue(),
                unit,
                status,
                Timestamp.from(observedAt)
        );
    }

    public List<SensorReadingResponse> getAll(String tenantId) {
        return jdbcTemplate.query(
                """
                SELECT tenant_id,
                       sensor_key,
                       greenhouse_id,
                       zone_id,
                       device_id,
                       value,
                       unit,
                       status,
                       last_updated_at
                FROM gms.latest_metric
                WHERE tenant_id = ?
                ORDER BY greenhouse_id, zone_id, sensor_key
                """,
                READING_ROW_MAPPER,
                tenantId
        );
    }

    public List<SensorReadingResponse> getByScope(String tenantId, String greenhouseId, String zoneId) {
        StringBuilder sql = new StringBuilder("""
                SELECT tenant_id,
                       sensor_key,
                       greenhouse_id,
                       zone_id,
                       device_id,
                       value,
                       unit,
                       status,
                       last_updated_at
                FROM gms.latest_metric
                WHERE tenant_id = ?
                """);

        List<Object> args = new ArrayList<>();
        args.add(tenantId);

        if (!isBlank(greenhouseId)) {
            sql.append(" AND greenhouse_id = ?");
            args.add(greenhouseId.trim());
        }
        if (!isBlank(zoneId)) {
            sql.append(" AND (zone_id = ? OR device_id = ?)");
            args.add(zoneId.trim());
            args.add(zoneId.trim());
        }

        sql.append(" ORDER BY greenhouse_id, zone_id, sensor_key");

        return jdbcTemplate.query(sql.toString(), READING_ROW_MAPPER, args.toArray());
    }

    public List<SensorReadingResponse> getAll() {
        return getAll("tenant-demo");
    }

    public List<SensorReadingResponse> getByScope(String greenhouseId, String zoneId) {
        return getByScope("tenant-demo", greenhouseId, zoneId);
    }

    /**
     * Returns a bucketed or raw time-series for a single sensor key.
     *
     * <p>Granularity options:
     * <ul>
     *   <li>{@code raw}    — individual readings, capped at 1 000 rows</li>
     *   <li>{@code minute} — 1-minute averages via {@code date_trunc('minute', …)}</li>
     *   <li>{@code hourly} — 1-hour averages (default)</li>
     *   <li>{@code daily}  — 1-day averages</li>
     * </ul>
     *
     * <p>Tenant isolation is enforced at the greenhouse level: the caller must
     * have already validated that {@code greenhouseId} belongs to the tenant.
     */
    public SensorHistoryResponse getHistory(String greenhouseId,
                                            String zoneId,
                                            String deviceId,
                                            String sensorKey,
                                            Instant from,
                                            Instant to,
                                            String granularity) {
        List<Object> args = new ArrayList<>();
        args.add(greenhouseId);
        args.add(sensorKey);
        args.add(Timestamp.from(from));
        args.add(Timestamp.from(to));

        StringBuilder conditions = new StringBuilder(
                "WHERE greenhouse_id = ? AND sensor_key = ? AND observed_at >= ? AND observed_at <= ?"
        );
        if (!isBlank(zoneId)) {
            conditions.append(" AND zone_id = ?");
            args.add(zoneId.trim());
        }
        if (!isBlank(deviceId)) {
            conditions.append(" AND device_id = ?");
            args.add(deviceId.trim());
        }

        String sql;
        if ("raw".equalsIgnoreCase(granularity)) {
            sql = "SELECT observed_at AS ts, value, unit "
                    + "FROM gms.telemetry_reading "
                    + conditions
                    + " ORDER BY observed_at ASC LIMIT 1000";
        } else {
            // toDateTruncUnit validates the value — safe to embed as a literal
            String truncUnit = toDateTruncUnit(granularity);
            sql = "SELECT date_trunc('" + truncUnit + "', observed_at) AS ts, "
                    + "AVG(value) AS value, MIN(unit) AS unit "
                    + "FROM gms.telemetry_reading "
                    + conditions
                    + " GROUP BY ts ORDER BY ts ASC";
        }

        record Row(Instant ts, double value, String unit) {}

        List<Row> rows = jdbcTemplate.query(sql,
                (rs, n) -> new Row(
                        rs.getTimestamp("ts").toInstant(),
                        rs.getDouble("value"),
                        rs.getString("unit")),
                args.toArray());

        String unit = rows.isEmpty() ? "raw" : rows.get(0).unit();
        List<SensorHistoryPoint> points = rows.stream()
                .map(r -> SensorHistoryPoint.builder()
                        .timestamp(r.ts())
                        .value(r.value())
                        .build())
                .toList();

        return SensorHistoryResponse.builder()
                .sensorKey(sensorKey)
                .greenhouseId(greenhouseId)
                .zoneId(isBlank(zoneId) ? null : zoneId.trim())
                .deviceId(isBlank(deviceId) ? null : deviceId.trim())
                .unit(unit)
                .granularity(granularity.toLowerCase())
                .from(from)
                .to(to)
                .points(points)
                .build();
    }

    /** Maps user-supplied granularity to a {@code date_trunc} precision literal.
     *  Only ever returns one of three hardcoded strings — safe to embed in SQL. */
    private static String toDateTruncUnit(String granularity) {
        return switch (granularity.toLowerCase()) {
            case "minute"       -> "minute";
            case "daily", "day" -> "day";
            default             -> "hour"; // covers "hourly", "hour", unknown
        };
    }

    private static String defaultString(String value, String fallback) {
        if (isBlank(value)) {
            return fallback;
        }
        return value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
