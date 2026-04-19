package md.utm.gms.backend.store;

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
        String greenhouseId = defaultString(reading.getGreenhouseId(), "greenhouse-default");
        String deviceId = defaultString(reading.getDeviceId(), "device-default");
        String zoneId = defaultString(reading.getZoneId(), deviceId);
        String unit = defaultString(reading.getUnit(), "raw");
        String status = defaultString(reading.getStatus(), "OK");

        jdbcTemplate.update(
                """
                INSERT INTO gms.telemetry_reading(
                    greenhouse_id,
                    zone_id,
                    device_id,
                    sensor_key,
                    value,
                    unit,
                    status,
                    observed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
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
                    greenhouse_id,
                    zone_id,
                    device_id,
                    sensor_key,
                    value,
                    unit,
                    status,
                    last_updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (greenhouse_id, device_id, sensor_key)
                DO UPDATE SET
                    zone_id = EXCLUDED.zone_id,
                    value = EXCLUDED.value,
                    unit = EXCLUDED.unit,
                    status = EXCLUDED.status,
                    last_updated_at = EXCLUDED.last_updated_at
                """,
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

    public List<SensorReadingResponse> getAll() {
        return jdbcTemplate.query(
                """
                SELECT sensor_key,
                       greenhouse_id,
                       zone_id,
                       device_id,
                       value,
                       unit,
                       status,
                       last_updated_at
                FROM gms.latest_metric
                ORDER BY greenhouse_id, zone_id, sensor_key
                """,
                READING_ROW_MAPPER
        );
    }

    public List<SensorReadingResponse> getByScope(String greenhouseId, String zoneId) {
        StringBuilder sql = new StringBuilder("""
                SELECT sensor_key,
                       greenhouse_id,
                       zone_id,
                       device_id,
                       value,
                       unit,
                       status,
                       last_updated_at
                FROM gms.latest_metric
                WHERE 1=1
                """);

        List<Object> args = new ArrayList<>();

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
