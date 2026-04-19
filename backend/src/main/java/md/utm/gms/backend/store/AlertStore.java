package md.utm.gms.backend.store;

import md.utm.gms.backend.api.dto.AlertResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL-backed alert store.
 */
@Component
public class AlertStore {

    private static final RowMapper<AlertResponse> ALERT_ROW_MAPPER = (rs, rowNum) ->
            AlertResponse.builder()
                    .id(rs.getString("id"))
                    .severity(rs.getString("severity"))
                    .sensorKey(rs.getString("sensor_key"))
                    .message(rs.getString("message"))
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

        jdbcTemplate.update(
                """
                INSERT INTO gms.alert_event(
                    id,
                    severity,
                    sensor_key,
                    message,
                    triggered_at,
                    acknowledged,
                    dismissed_at
                ) VALUES (?, ?, ?, ?, ?, ?, NULL)
                ON CONFLICT (id)
                DO UPDATE SET
                    severity = EXCLUDED.severity,
                    sensor_key = EXCLUDED.sensor_key,
                    message = EXCLUDED.message,
                    triggered_at = EXCLUDED.triggered_at,
                    acknowledged = EXCLUDED.acknowledged,
                    dismissed_at = NULL
                """,
                alert.getId(),
                defaultString(alert.getSeverity(), "INFO"),
                alert.getSensorKey(),
                defaultString(alert.getMessage(), "Alert"),
                Timestamp.from(triggeredAt),
                alert.isAcknowledged()
        );
    }

    public Optional<AlertResponse> acknowledge(String id) {
        int updated = jdbcTemplate.update(
                """
                UPDATE gms.alert_event
                SET acknowledged = TRUE
                WHERE id = ? AND dismissed_at IS NULL
                """,
                id
        );
        if (updated == 0) {
            return Optional.empty();
        }
        return findById(id);
    }

    public boolean dismiss(String id) {
        int updated = jdbcTemplate.update(
                """
                UPDATE gms.alert_event
                SET dismissed_at = NOW()
                WHERE id = ? AND dismissed_at IS NULL
                """,
                id
        );
        return updated > 0;
    }

    public List<AlertResponse> getAll() {
        return jdbcTemplate.query(
                """
                SELECT id,
                       severity,
                       sensor_key,
                       message,
                       triggered_at,
                       acknowledged
                FROM gms.alert_event
                WHERE dismissed_at IS NULL
                ORDER BY acknowledged ASC,
                         CASE severity
                             WHEN 'CRITICAL' THEN 0
                             WHEN 'WARNING' THEN 1
                             WHEN 'INFO' THEN 2
                             ELSE 3
                         END,
                         triggered_at DESC
                """,
                ALERT_ROW_MAPPER
        );
    }

    private Optional<AlertResponse> findById(String id) {
        List<AlertResponse> results = jdbcTemplate.query(
                """
                SELECT id,
                       severity,
                       sensor_key,
                       message,
                       triggered_at,
                       acknowledged
                FROM gms.alert_event
                WHERE id = ? AND dismissed_at IS NULL
                """,
                ALERT_ROW_MAPPER,
                id
        );

        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(results.get(0));
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
