package md.utm.gms.backend.store;

import md.utm.gms.backend.mqtt.dto.CommandAckPayload;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class CommandAckStore {

    private final JdbcTemplate jdbcTemplate;

    public CommandAckStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void update(CommandAckPayload payload) {
        if (payload == null || isBlank(payload.getCommandId())) {
            return;
        }

        Instant ackTimestamp = payload.getTimestamp() != null ? payload.getTimestamp() : Instant.now();

        jdbcTemplate.update(
                """
                INSERT INTO gms.command_ack(
                    command_id,
                    event_id,
                    type,
                    tenant_id,
                    greenhouse_id,
                    gateway_id,
                    device_id,
                    zone_id,
                    status,
                    reason,
                    ack_timestamp,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (command_id)
                DO UPDATE SET
                    event_id = EXCLUDED.event_id,
                    type = EXCLUDED.type,
                    tenant_id = EXCLUDED.tenant_id,
                    greenhouse_id = EXCLUDED.greenhouse_id,
                    gateway_id = EXCLUDED.gateway_id,
                    device_id = EXCLUDED.device_id,
                    zone_id = EXCLUDED.zone_id,
                    status = EXCLUDED.status,
                    reason = EXCLUDED.reason,
                    ack_timestamp = EXCLUDED.ack_timestamp,
                    updated_at = NOW()
                """,
                payload.getCommandId(),
                payload.getEventId(),
                payload.getType(),
                payload.getTenantId(),
                payload.getGreenhouseId(),
                payload.getGatewayId(),
                payload.getDeviceId(),
                payload.getZoneId(),
                payload.getStatus(),
                payload.getReason(),
                Timestamp.from(ackTimestamp)
        );
    }

    public Optional<CommandAckPayload> findByCommandId(String commandId) {
        if (isBlank(commandId)) {
            return Optional.empty();
        }

        List<CommandAckPayload> matches = jdbcTemplate.query(
                """
                SELECT command_id,
                       event_id,
                       type,
                       tenant_id,
                       greenhouse_id,
                       gateway_id,
                       device_id,
                       zone_id,
                       status,
                       reason,
                       ack_timestamp
                FROM gms.command_ack
                WHERE command_id = ?
                """,
                (rs, rowNum) -> {
                    CommandAckPayload payload = new CommandAckPayload();
                    payload.setCommandId(rs.getString("command_id"));
                    payload.setEventId(rs.getString("event_id"));
                    payload.setType(rs.getString("type"));
                    payload.setTenantId(rs.getString("tenant_id"));
                    payload.setGreenhouseId(rs.getString("greenhouse_id"));
                    payload.setGatewayId(rs.getString("gateway_id"));
                    payload.setDeviceId(rs.getString("device_id"));
                    payload.setZoneId(rs.getString("zone_id"));
                    payload.setStatus(rs.getString("status"));
                    payload.setReason(rs.getString("reason"));
                    Timestamp ackTimestamp = rs.getTimestamp("ack_timestamp");
                    if (ackTimestamp != null) {
                        payload.setTimestamp(ackTimestamp.toInstant());
                    }
                    return payload;
                },
                commandId
        );

        if (matches.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(matches.get(0));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
