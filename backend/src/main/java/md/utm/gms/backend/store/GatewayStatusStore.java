package md.utm.gms.backend.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * PostgreSQL-backed store for gateway heartbeat/connectivity state.
 *
 * <p>Each upsert records the latest status received from a gateway over MQTT
 * so operators can detect connectivity loss from the dashboard.
 */
@Component
public class GatewayStatusStore {

    private final JdbcTemplate jdbcTemplate;

    public GatewayStatusStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(String tenantId,
                       String greenhouseId,
                       String gatewayId,
                       String status,
                       String firmwareVersion,
                       Instant lastSeenAt) {
        Instant seenAt = lastSeenAt != null ? lastSeenAt : Instant.now();

        jdbcTemplate.update(
                """
                INSERT INTO gms.gateway_status(
                    tenant_id,
                    greenhouse_id,
                    gateway_id,
                    status,
                    firmware_version,
                    last_seen_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (tenant_id, greenhouse_id, gateway_id)
                DO UPDATE SET
                    status           = EXCLUDED.status,
                    firmware_version = COALESCE(EXCLUDED.firmware_version, gms.gateway_status.firmware_version),
                    last_seen_at     = EXCLUDED.last_seen_at,
                    updated_at       = NOW()
                """,
                tenantId,
                greenhouseId,
                gatewayId,
                normalizeStatus(status),
                trimToNull(firmwareVersion),
                Timestamp.from(seenAt)
        );
    }

    /**
     * Returns all gateways whose last heartbeat is older than {@code threshold}.
     * Used by the heartbeat monitor to detect connectivity loss.
     */
    public List<OfflineGateway> findStaleSince(Duration threshold) {
        Instant cutoff = Instant.now().minus(threshold);
        return jdbcTemplate.query(
                """
                SELECT tenant_id, greenhouse_id, gateway_id, last_seen_at
                FROM gms.gateway_status
                WHERE last_seen_at < ?
                ORDER BY last_seen_at ASC
                """,
                (rs, rowNum) -> new OfflineGateway(
                        rs.getString("tenant_id"),
                        rs.getString("greenhouse_id"),
                        rs.getString("gateway_id"),
                        rs.getTimestamp("last_seen_at").toInstant()),
                Timestamp.from(cutoff)
        );
    }

    public record OfflineGateway(String tenantId,
                                 String greenhouseId,
                                 String gatewayId,
                                 Instant lastSeenAt) {}

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "UNKNOWN";
        }
        return switch (status.trim().toUpperCase()) {
            case "ONLINE"   -> "ONLINE";
            case "OFFLINE"  -> "OFFLINE";
            case "DEGRADED" -> "DEGRADED";
            default         -> "UNKNOWN";
        };
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
