package md.utm.gms.backend.zones;

import md.utm.gms.backend.mqtt.dto.RegistryEventPayload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ZoneRegistryStore {

    private final ConcurrentHashMap<String, ZoneDeviceRecord> devices = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> removedDevices = new ConcurrentHashMap<>();

    public ZoneDeviceRecord upsertDiscovery(RegistryEventPayload event) {
        String key = composeKey(event.getTenantId(), event.getGreenhouseId(), event.getDeviceId());
        Instant now = event.getTimestamp() != null ? event.getTimestamp() : Instant.now();

        return devices.compute(key, (ignored, current) -> {
            ZoneDeviceRecord next = current != null
                    ? current
                    : ZoneDeviceRecord.builder()
                            .tenantId(event.getTenantId())
                            .greenhouseId(event.getGreenhouseId())
                            .deviceId(event.getDeviceId())
                            .status("DISCOVERED")
                            .build();

            next.setTenantId(event.getTenantId());
            next.setGreenhouseId(event.getGreenhouseId());
            next.setDeviceId(event.getDeviceId());
            next.setFirmwareVersion(event.getFirmwareVersion());
            next.setMetadata(event.getMetadata());
            next.setLastSeenAt(now);
            next.setUpdatedAt(Instant.now());

            if (!next.isAssigned()) {
                next.setStatus("DISCOVERED");
            }

            return next;
        });
    }

    public ZoneDeviceRecord applyAssignmentAck(RegistryEventPayload event) {
        ZoneDeviceRecord record = upsertDiscovery(event);

        String type = event.getType() == null ? "" : event.getType().trim().toUpperCase();
        if ("ZONE_UNASSIGNED_APPLIED".equals(type)) {
            record.setZoneId(null);
            record.setZoneName(null);
        } else {
            record.setZoneId(trimToNull(event.getZoneId()));
            record.setZoneName(trimToNull(event.getZoneName()));
        }

        record.setStatus(record.isAssigned() ? "ASSIGNED" : "DISCOVERED");
        record.setUpdatedAt(Instant.now());
        return record;
    }

    public ZoneDeviceRecord touchFromTelemetry(String tenantId,
                                               String greenhouseId,
                                               String deviceId,
                                               String zoneId,
                                               String zoneName,
                                               Instant seenAt) {
        String key = composeKey(tenantId, greenhouseId, deviceId);
        Instant now = seenAt != null ? seenAt : Instant.now();

        String normalizedZoneId = trimToNull(zoneId);
        String normalizedZoneName = trimToNull(zoneName);
        boolean telemetryAssigned = isTelemetryAssigned(normalizedZoneId, normalizedZoneName, deviceId);

        return devices.compute(key, (ignored, current) -> {
            ZoneDeviceRecord next = current != null
                    ? current
                    : ZoneDeviceRecord.builder()
                    .tenantId(tenantId)
                    .greenhouseId(greenhouseId)
                    .deviceId(deviceId)
                    .status("DISCOVERED")
                    .build();

            next.setTenantId(tenantId);
            next.setGreenhouseId(greenhouseId);
            next.setDeviceId(deviceId);
            next.setLastSeenAt(now);
            next.setUpdatedAt(Instant.now());

            if (telemetryAssigned) {
                next.setZoneId(normalizedZoneId != null ? normalizedZoneId : deviceId);
                next.setZoneName(normalizedZoneName);
                next.setStatus("ASSIGNED");
            } else {
                next.setZoneId(null);
                next.setZoneName(null);
                next.setStatus("DISCOVERED");
            }

            return next;
        });
    }

    public Optional<ZoneDeviceRecord> removeDevice(String tenantId, String greenhouseId, String deviceId) {
        return removeDevice(tenantId, greenhouseId, deviceId, Instant.now());
    }

    public Optional<ZoneDeviceRecord> removeDevice(String tenantId,
                                                   String greenhouseId,
                                                   String deviceId,
                                                   Instant removedAt) {
        String key = composeKey(tenantId, greenhouseId, deviceId);
        removedDevices.put(key, removedAt != null ? removedAt : Instant.now());
        ZoneDeviceRecord removed = devices.remove(key);
        return Optional.ofNullable(removed);
    }

    public boolean isSuppressed(String tenantId,
                                String greenhouseId,
                                String deviceId,
                                Instant eventTimestamp) {
        String key = composeKey(tenantId, greenhouseId, deviceId);
        Instant removedAt = removedDevices.get(key);
        if (removedAt == null) {
            return false;
        }

        Instant effectiveTimestamp = eventTimestamp != null ? eventTimestamp : Instant.now();
        if (effectiveTimestamp.isAfter(removedAt)) {
            removedDevices.remove(key, removedAt);
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

        String key = composeKey(tenantId, greenhouseId, deviceId);
        removedDevices.remove(key);
        String effectiveZoneId = trimToNull(zoneId);
        if (effectiveZoneId == null) {
            effectiveZoneId = UUID.randomUUID().toString();
        }

        String effectiveZoneName = trimToNull(zoneName);
        if (effectiveZoneName == null) {
            effectiveZoneName = "zone-" + effectiveZoneId.substring(0, 8);
        }

        String finalEffectiveZoneId = effectiveZoneId;
        String finalEffectiveZoneName = effectiveZoneName;
        return devices.compute(key, (ignored, current) -> {
            ZoneDeviceRecord next = current != null
                    ? current
                    : ZoneDeviceRecord.builder()
                            .tenantId(tenantId)
                            .greenhouseId(greenhouseId)
                            .deviceId(deviceId)
                            .build();

            next.setTenantId(tenantId);
            next.setGreenhouseId(greenhouseId);
            next.setDeviceId(deviceId);
            next.setZoneId(finalEffectiveZoneId);
            next.setZoneName(finalEffectiveZoneName);
            next.setStatus("ASSIGNED");
            next.setUpdatedAt(Instant.now());
            if (metadata != null && !metadata.isEmpty()) {
                next.setMetadata(metadata);
            }
            return next;
        });
    }

    public Optional<ZoneDeviceRecord> unassignDevice(String tenantId,
                                                     String greenhouseId,
                                                     String deviceId) {
        String key = composeKey(tenantId, greenhouseId, deviceId);
        removedDevices.remove(key);
        ZoneDeviceRecord updated = devices.computeIfPresent(key, (ignored, current) -> {
            current.setZoneId(null);
            current.setZoneName(null);
            current.setStatus("DISCOVERED");
            current.setUpdatedAt(Instant.now());
            return current;
        });
        return Optional.ofNullable(updated);
    }

    public Optional<ZoneDeviceRecord> findByZoneId(String tenantId, String greenhouseId, String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return Optional.empty();
        }

        return devices.values().stream()
                .filter(record -> tenantId.equals(record.getTenantId()))
                .filter(record -> greenhouseId.equals(record.getGreenhouseId()))
                .filter(record -> zoneId.equals(record.getZoneId()))
                .findFirst();
    }

    public Optional<ZoneDeviceRecord> findDevice(String tenantId, String greenhouseId, String deviceId) {
        return Optional.ofNullable(devices.get(composeKey(tenantId, greenhouseId, deviceId)));
    }

    public List<ZoneDeviceRecord> listByGreenhouse(String tenantId, String greenhouseId) {
        List<ZoneDeviceRecord> result = new ArrayList<>();
        for (ZoneDeviceRecord record : devices.values()) {
            if (tenantId.equals(record.getTenantId()) && greenhouseId.equals(record.getGreenhouseId())) {
                result.add(record);
            }
        }
        result.sort(ZoneRegistryStore::compareRecords);
        return result;
    }

    private static int compareRecords(ZoneDeviceRecord a, ZoneDeviceRecord b) {
        int assigned = Boolean.compare(!a.isAssigned(), !b.isAssigned());
        if (assigned != 0) {
            return assigned;
        }

        String zoneA = a.getZoneName() == null ? "~" : a.getZoneName().toLowerCase();
        String zoneB = b.getZoneName() == null ? "~" : b.getZoneName().toLowerCase();
        int zoneCompare = zoneA.compareTo(zoneB);
        if (zoneCompare != 0) {
            return zoneCompare;
        }

        String deviceA = a.getDeviceId() == null ? "~" : a.getDeviceId().toLowerCase();
        String deviceB = b.getDeviceId() == null ? "~" : b.getDeviceId().toLowerCase();
        return deviceA.compareTo(deviceB);
    }

    private static String composeKey(String tenantId, String greenhouseId, String deviceId) {
        return tenantId + ":" + greenhouseId + ":" + deviceId;
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
