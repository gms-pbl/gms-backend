package md.utm.gms.backend.store;

import md.utm.gms.backend.api.dto.ThresholdApplyStatusResponse;
import md.utm.gms.backend.mqtt.dto.CommandAckPayload;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class ThresholdApplyStatusStore {

    private static final RowMapper<ThresholdApplyStatusResponse> STATUS_MAPPER = (rs, rowNum) ->
            ThresholdApplyStatusResponse.builder()
                    .tenantId(rs.getString("tenant_id"))
                    .greenhouseId(rs.getString("greenhouse_id"))
                    .zoneId(rs.getString("zone_id"))
                    .gatewayId(rs.getString("gateway_id"))
                    .configVersion(rs.getLong("config_version"))
                    .commandId(rs.getString("command_id"))
                    .status(rs.getString("status"))
                    .reason(rs.getString("reason"))
                    .ackTimestamp(toInstant(rs.getTimestamp("ack_timestamp")))
                    .updatedAt(toInstant(rs.getTimestamp("updated_at")))
                    .build();

    private final JdbcTemplate jdbcTemplate;

    public ThresholdApplyStatusStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void markPending(String tenantId,
                            String greenhouseId,
                            String zoneId,
                            String gatewayId,
                            long configVersion,
                            String commandId) {
        jdbcTemplate.update(
                """
                INSERT INTO gms.threshold_apply_status(
                    tenant_id,
                    greenhouse_id,
                    zone_id,
                    gateway_id,
                    config_version,
                    command_id,
                    status,
                    reason,
                    ack_timestamp,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'PENDING', NULL, NULL, NOW())
                ON CONFLICT (tenant_id, greenhouse_id, zone_id, gateway_id, config_version)
                DO UPDATE SET
                    command_id = EXCLUDED.command_id,
                    status = 'PENDING',
                    reason = NULL,
                    ack_timestamp = NULL,
                    updated_at = NOW()
                """,
                tenantId,
                greenhouseId,
                zoneId,
                gatewayId,
                configVersion,
                commandId
        );
    }

    public void markFailed(String commandId, String reason) {
        if (isBlank(commandId)) {
            return;
        }
        jdbcTemplate.update(
                """
                UPDATE gms.threshold_apply_status
                SET status = 'FAILED',
                    reason = ?,
                    ack_timestamp = NOW(),
                    updated_at = NOW()
                WHERE command_id = ?
                """,
                reason,
                commandId
        );
    }

    public boolean updateFromAck(CommandAckPayload payload) {
        if (payload == null || isBlank(payload.getCommandId())) {
            return false;
        }

        Instant ackTimestamp = payload.getTimestamp() != null ? payload.getTimestamp() : Instant.now();
        int updated = jdbcTemplate.update(
                """
                UPDATE gms.threshold_apply_status
                SET status = ?,
                    reason = ?,
                    ack_timestamp = ?,
                    updated_at = NOW()
                WHERE command_id = ?
                """,
                defaultString(payload.getStatus(), "UNKNOWN"),
                payload.getReason(),
                Timestamp.from(ackTimestamp),
                payload.getCommandId()
        );

        return updated > 0;
    }

    public Optional<ThresholdApplyStatusResponse> findLatest(String tenantId,
                                                             String greenhouseId,
                                                             String zoneId) {
        List<ThresholdApplyStatusResponse> rows = jdbcTemplate.query(
                """
                SELECT tenant_id,
                       greenhouse_id,
                       zone_id,
                       gateway_id,
                       config_version,
                       command_id,
                       status,
                       reason,
                       ack_timestamp,
                       updated_at
                FROM gms.threshold_apply_status
                WHERE tenant_id = ? AND greenhouse_id = ? AND zone_id = ?
                ORDER BY config_version DESC, updated_at DESC
                LIMIT 1
                """,
                STATUS_MAPPER,
                tenantId,
                greenhouseId,
                zoneId
        );

        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String defaultString(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
