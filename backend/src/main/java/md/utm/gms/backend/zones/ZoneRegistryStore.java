package md.utm.gms.backend.zones;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import md.utm.gms.backend.mqtt.dto.RegistryEventPayload;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class ZoneRegistryStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ZoneRegistryStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public ZoneDeviceRecord upsertDiscovery(RegistryEventPayload event) {
        Instant now = event.getTimestamp() != null ? event.getTimestamp() : Instant.now();

        jdbcTemplate.update(
                """
                INSERT INTO gms.zone_device(
                    tenant_id,
                    greenhouse_id,
                    device_id,
                    zone_id,
                    zone_name,
                    firmware_version,
                    status,
                    last_seen_at,
                    updated_at,
                    metadata,
                    removed_at
                ) VALUES (?, ?, ?, NULL, NULL, ?, 'DISCOVERED', ?, NOW(), ?::jsonb, NULL)
                ON CONFLICT (tenant_id, greenhouse_id, device_id)
                DO UPDATE SET
                    firmware_version = EXCLUDED.firmware_version,
                    metadata = EXCLUDED.metadata,
                    last_seen_at = EXCLUDED.last_seen_at,
                    updated_at = NOW(),
                    removed_at = NULL,
                    status = CASE
                        WHEN gms.zone_device.zone_id IS NULL OR gms.zone_device.zone_id = '' THEN 'DISCOVERED'
                        ELSE 'ASSIGNED'
                    END
                """,
                event.getTenantId(),
                event.getGreenhouseId(),
                event.getDeviceId(),
                trimToNull(event.getFirmwareVersion()),
                Timestamp.from(now),
                toJson(event.getMetadata())
        );

        return findExisting(event.getTenantId(), event.getGreenhouseId(), event.getDeviceId());
    }

    public ZoneDeviceRecord applyAssignmentAck(RegistryEventPayload event) {
        upsertDiscovery(event);

        String type = normalizeType(event.getType());
        if ("ZONE_UNASSIGNED_APPLIED".equals(type)) {
            jdbcTemplate.update(
                    """
                    UPDATE gms.zone_device
                    SET zone_id = NULL,
                        zone_name = NULL,
                        status = 'DISCOVERED',
                        updated_at = NOW(),
                        removed_at = NULL
                    WHERE tenant_id = ? AND greenhouse_id = ? AND device_id = ?
                    """,
                    event.getTenantId(),
                    event.getGreenhouseId(),
                    event.getDeviceId()
            );
        } else {
            String nextZoneId = trimToNull(event.getZoneId());
            String nextZoneName = trimToNull(event.getZoneName());
            String status = nextZoneId != null ? "ASSIGNED" : "DISCOVERED";

            jdbcTemplate.update(
                    """
                    UPDATE gms.zone_device
                    SET zone_id = ?,
                        zone_name = ?,
                        status = ?,
                        updated_at = NOW(),
                        removed_at = NULL
                    WHERE tenant_id = ? AND greenhouse_id = ? AND device_id = ?
                    """,
                    nextZoneId,
                    nextZoneName,
                    status,
                    event.getTenantId(),
                    event.getGreenhouseId(),
                    event.getDeviceId()
            );
        }

        return findExisting(event.getTenantId(), event.getGreenhouseId(), event.getDeviceId());
    }

    public ZoneDeviceRecord touchFromTelemetry(String tenantId,
                                               String greenhouseId,
                                               String deviceId,
                                               String zoneId,
                                               String zoneName,
                                               Instant seenAt) {
        Instant now = seenAt != null ? seenAt : Instant.now();

        String normalizedZoneId = trimToNull(zoneId);
        String normalizedZoneName = trimToNull(zoneName);
        boolean telemetryAssigned = isTelemetryAssigned(normalizedZoneId, normalizedZoneName, deviceId);

        String nextZoneId = telemetryAssigned
                ? (normalizedZoneId != null ? normalizedZoneId : deviceId)
                : null;
        String nextZoneName = telemetryAssigned ? normalizedZoneName : null;
        String nextStatus = telemetryAssigned ? "ASSIGNED" : "DISCOVERED";

        jdbcTemplate.update(
                """
                INSERT INTO gms.zone_device(
                    tenant_id,
                    greenhouse_id,
                    device_id,
                    zone_id,
                    zone_name,
                    firmware_version,
                    status,
                    last_seen_at,
                    updated_at,
                    metadata,
                    removed_at
                ) VALUES (?, ?, ?, ?, ?, NULL, ?, ?, NOW(), '{}'::jsonb, NULL)
                ON CONFLICT (tenant_id, greenhouse_id, device_id)
                DO UPDATE SET
                    zone_id = EXCLUDED.zone_id,
                    zone_name = EXCLUDED.zone_name,
                    status = EXCLUDED.status,
                    last_seen_at = EXCLUDED.last_seen_at,
                    updated_at = NOW(),
                    removed_at = NULL
                """,
                tenantId,
                greenhouseId,
                deviceId,
                nextZoneId,
                nextZoneName,
                nextStatus,
                Timestamp.from(now)
        );

        return findExisting(tenantId, greenhouseId, deviceId);
    }

    public Optional<ZoneDeviceRecord> removeDevice(String tenantId, String greenhouseId, String deviceId) {
        return removeDevice(tenantId, greenhouseId, deviceId, Instant.now());
    }

    public Optional<ZoneDeviceRecord> removeDevice(String tenantId,
                                                   String greenhouseId,
                                                   String deviceId,
                                                   Instant removedAt) {
        Optional<ZoneDeviceRecord> current = findDevice(tenantId, greenhouseId, deviceId);
        Instant marker = removedAt != null ? removedAt : Instant.now();

        jdbcTemplate.update(
                """
                INSERT INTO gms.zone_device(
                    tenant_id,
                    greenhouse_id,
                    device_id,
                    zone_id,
                    zone_name,
                    firmware_version,
                    status,
                    last_seen_at,
                    updated_at,
                    metadata,
                    removed_at
                ) VALUES (?, ?, ?, NULL, NULL, NULL, 'REMOVED', ?, NOW(), '{}'::jsonb, ?)
                ON CONFLICT (tenant_id, greenhouse_id, device_id)
                DO UPDATE SET
                    zone_id = NULL,
                    zone_name = NULL,
                    status = 'REMOVED',
                    updated_at = NOW(),
                    removed_at = EXCLUDED.removed_at
                """,
                tenantId,
                greenhouseId,
                deviceId,
                Timestamp.from(marker),
                Timestamp.from(marker)
        );

        return current;
    }

    public boolean isSuppressed(String tenantId,
                                String greenhouseId,
                                String deviceId,
                                Instant eventTimestamp) {
        List<Timestamp> timestamps = jdbcTemplate.query(
                """
                SELECT removed_at
                FROM gms.zone_device
                WHERE tenant_id = ? AND greenhouse_id = ? AND device_id = ?
                """,
                (rs, rowNum) -> rs.getTimestamp("removed_at"),
                tenantId,
                greenhouseId,
                deviceId
        );

        if (timestamps.isEmpty() || timestamps.get(0) == null) {
            return false;
        }

        Instant removedAt = timestamps.get(0).toInstant();
        Instant effectiveTimestamp = eventTimestamp != null ? eventTimestamp : Instant.now();

        if (effectiveTimestamp.isAfter(removedAt)) {
            jdbcTemplate.update(
                    """
                    UPDATE gms.zone_device
                    SET removed_at = NULL,
                        status = CASE
                            WHEN zone_id IS NULL OR zone_id = '' THEN 'DISCOVERED'
                            ELSE 'ASSIGNED'
                        END,
                        updated_at = NOW()
                    WHERE tenant_id = ? AND greenhouse_id = ? AND device_id = ?
                    """,
                    tenantId,
                    greenhouseId,
                    deviceId
            );
            return false;
        }

        return true;
    }

    public ZoneDeviceRecord assignDevice(String tenantId,
                                         String greenhouseId,
                                         String deviceId,
                                         String zoneId,
                                         String zoneName,
                                         Map<String, Object> metadata) {
        String effectiveZoneId = trimToNull(zoneId);
        if (effectiveZoneId == null) {
            effectiveZoneId = UUID.randomUUID().toString();
        }

        String effectiveZoneName = trimToNull(zoneName);
        if (effectiveZoneName == null) {
            effectiveZoneName = "zone-" + effectiveZoneId.substring(0, 8);
        }

        Optional<ZoneDeviceRecord> previous = findAnyDevice(tenantId, greenhouseId, deviceId);
        Instant lastSeenAt = previous.map(ZoneDeviceRecord::getLastSeenAt).orElse(Instant.now());
        String firmwareVersion = previous.map(ZoneDeviceRecord::getFirmwareVersion).orElse(null);

        Map<String, Object> effectiveMetadata;
        if (metadata != null && !metadata.isEmpty()) {
            effectiveMetadata = metadata;
        } else {
            effectiveMetadata = previous.map(ZoneDeviceRecord::getMetadata).orElse(Map.of());
        }

        jdbcTemplate.update(
                """
                INSERT INTO gms.zone_device(
                    tenant_id,
                    greenhouse_id,
                    device_id,
                    zone_id,
                    zone_name,
                    firmware_version,
                    status,
                    last_seen_at,
                    updated_at,
                    metadata,
                    removed_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'ASSIGNED', ?, NOW(), ?::jsonb, NULL)
                ON CONFLICT (tenant_id, greenhouse_id, device_id)
                DO UPDATE SET
                    zone_id = EXCLUDED.zone_id,
                    zone_name = EXCLUDED.zone_name,
                    firmware_version = COALESCE(EXCLUDED.firmware_version, gms.zone_device.firmware_version),
                    status = 'ASSIGNED',
                    last_seen_at = COALESCE(gms.zone_device.last_seen_at, EXCLUDED.last_seen_at),
                    updated_at = NOW(),
                    metadata = EXCLUDED.metadata,
                    removed_at = NULL
                """,
                tenantId,
                greenhouseId,
                deviceId,
                effectiveZoneId,
                effectiveZoneName,
                firmwareVersion,
                Timestamp.from(lastSeenAt),
                toJson(effectiveMetadata)
        );

        return findExisting(tenantId, greenhouseId, deviceId);
    }

    public Optional<ZoneDeviceRecord> unassignDevice(String tenantId,
                                                     String greenhouseId,
                                                     String deviceId) {
        int updated = jdbcTemplate.update(
                """
                UPDATE gms.zone_device
                SET zone_id = NULL,
                    zone_name = NULL,
                    status = 'DISCOVERED',
                    updated_at = NOW(),
                    removed_at = NULL
                WHERE tenant_id = ? AND greenhouse_id = ? AND device_id = ? AND removed_at IS NULL
                """,
                tenantId,
                greenhouseId,
                deviceId
        );

        if (updated == 0) {
            return Optional.empty();
        }

        return findDevice(tenantId, greenhouseId, deviceId);
    }

    public Optional<ZoneDeviceRecord> findByZoneId(String tenantId, String greenhouseId, String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return Optional.empty();
        }

        List<ZoneDeviceRecord> results = jdbcTemplate.query(
                """
                SELECT tenant_id,
                       greenhouse_id,
                       device_id,
                       zone_id,
                       zone_name,
                       firmware_version,
                       status,
                       last_seen_at,
                       updated_at,
                       metadata,
                       removed_at
                FROM gms.zone_device
                WHERE tenant_id = ?
                  AND greenhouse_id = ?
                  AND zone_id = ?
                  AND removed_at IS NULL
                LIMIT 1
                """,
                this::mapRecord,
                tenantId,
                greenhouseId,
                zoneId
        );

        if (results.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(results.get(0));
    }

    public Optional<ZoneDeviceRecord> findDevice(String tenantId, String greenhouseId, String deviceId) {
        List<ZoneDeviceRecord> results = jdbcTemplate.query(
                """
                SELECT tenant_id,
                       greenhouse_id,
                       device_id,
                       zone_id,
                       zone_name,
                       firmware_version,
                       status,
                       last_seen_at,
                       updated_at,
                       metadata,
                       removed_at
                FROM gms.zone_device
                WHERE tenant_id = ?
                  AND greenhouse_id = ?
                  AND device_id = ?
                  AND removed_at IS NULL
                LIMIT 1
                """,
                this::mapRecord,
                tenantId,
                greenhouseId,
                deviceId
        );

        if (results.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(results.get(0));
    }

    public List<ZoneDeviceRecord> listByGreenhouse(String tenantId, String greenhouseId) {
        return jdbcTemplate.query(
                """
                SELECT tenant_id,
                       greenhouse_id,
                       device_id,
                       zone_id,
                       zone_name,
                       firmware_version,
                       status,
                       last_seen_at,
                       updated_at,
                       metadata,
                       removed_at
                FROM gms.zone_device
                WHERE tenant_id = ?
                  AND greenhouse_id = ?
                  AND removed_at IS NULL
                ORDER BY
                    CASE WHEN zone_id IS NULL OR zone_id = '' THEN 1 ELSE 0 END,
                    LOWER(COALESCE(zone_name, '~')),
                    LOWER(COALESCE(device_id, '~'))
                """,
                this::mapRecord,
                tenantId,
                greenhouseId
        );
    }

    private Optional<ZoneDeviceRecord> findAnyDevice(String tenantId, String greenhouseId, String deviceId) {
        List<ZoneDeviceRecord> results = jdbcTemplate.query(
                """
                SELECT tenant_id,
                       greenhouse_id,
                       device_id,
                       zone_id,
                       zone_name,
                       firmware_version,
                       status,
                       last_seen_at,
                       updated_at,
                       metadata,
                       removed_at
                FROM gms.zone_device
                WHERE tenant_id = ?
                  AND greenhouse_id = ?
                  AND device_id = ?
                LIMIT 1
                """,
                this::mapRecord,
                tenantId,
                greenhouseId,
                deviceId
        );

        if (results.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(results.get(0));
    }

    private ZoneDeviceRecord findExisting(String tenantId, String greenhouseId, String deviceId) {
        return findAnyDevice(tenantId, greenhouseId, deviceId)
                .orElseThrow(() -> new IllegalStateException("Zone device record not found after upsert"));
    }

    private ZoneDeviceRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
        Timestamp lastSeen = rs.getTimestamp("last_seen_at");
        Timestamp updated = rs.getTimestamp("updated_at");

        return ZoneDeviceRecord.builder()
                .tenantId(rs.getString("tenant_id"))
                .greenhouseId(rs.getString("greenhouse_id"))
                .deviceId(rs.getString("device_id"))
                .zoneId(rs.getString("zone_id"))
                .zoneName(rs.getString("zone_name"))
                .firmwareVersion(rs.getString("firmware_version"))
                .status(rs.getString("status"))
                .lastSeenAt(lastSeen != null ? lastSeen.toInstant() : null)
                .updatedAt(updated != null ? updated.toInstant() : null)
                .metadata(readMetadata(rs.getString("metadata")))
                .build();
    }

    private Map<String, Object> readMetadata(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String toJson(Map<String, Object> value) {
        Map<String, Object> safe = value != null ? value : Map.of();
        try {
            return objectMapper.writeValueAsString(safe);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private static String normalizeType(String type) {
        if (type == null) {
            return "";
        }
        return type.trim().toUpperCase();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isTelemetryAssigned(String zoneId, String zoneName, String deviceId) {
        if (zoneName != null
                && !zoneName.isBlank()
                && !"unassigned".equalsIgnoreCase(zoneName)) {
            return true;
        }
        if (zoneId == null || zoneId.isBlank()) {
            return false;
        }
        return !zoneId.equals(deviceId);
    }
}
