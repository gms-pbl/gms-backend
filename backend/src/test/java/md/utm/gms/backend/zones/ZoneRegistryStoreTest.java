package md.utm.gms.backend.zones;

import md.utm.gms.backend.mqtt.dto.RegistryEventPayload;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ZoneRegistryStoreTest {

    @Test
    void unassignAckClearsZoneEvenIfPayloadContainsDeviceAsZoneId() {
        ZoneRegistryStore store = new ZoneRegistryStore();

        store.assignDevice(
                "tenant-demo",
                "greenhouse-demo",
                "device-1",
                "zone-1",
                "Tomatoes",
                Map.of());

        RegistryEventPayload unassign = RegistryEventPayload.builder()
                .tenantId("tenant-demo")
                .greenhouseId("greenhouse-demo")
                .deviceId("device-1")
                .type("ZONE_UNASSIGNED_APPLIED")
                .zoneId("device-1")
                .zoneName(null)
                .timestamp(Instant.now())
                .build();

        ZoneDeviceRecord updated = store.applyAssignmentAck(unassign);

        assertThat(updated.getZoneId()).isNull();
        assertThat(updated.getZoneName()).isNull();
        assertThat(updated.getStatus()).isEqualTo("DISCOVERED");
        assertThat(updated.isAssigned()).isFalse();
    }

    @Test
    void telemetryWithoutExplicitZoneKeepsDeviceDiscovered() {
        ZoneRegistryStore store = new ZoneRegistryStore();

        ZoneDeviceRecord updated = store.touchFromTelemetry(
                "tenant-demo",
                "greenhouse-demo",
                "device-1",
                "device-1",
                null,
                Instant.now());

        assertThat(updated.isAssigned()).isFalse();
        assertThat(updated.getStatus()).isEqualTo("DISCOVERED");
        assertThat(updated.getZoneId()).isNull();
    }

    @Test
    void telemetryWithZoneNameMarksDeviceAssigned() {
        ZoneRegistryStore store = new ZoneRegistryStore();

        ZoneDeviceRecord updated = store.touchFromTelemetry(
                "tenant-demo",
                "greenhouse-demo",
                "device-2",
                "zone-2",
                "Cucumbers",
                Instant.now());

        assertThat(updated.isAssigned()).isTrue();
        assertThat(updated.getStatus()).isEqualTo("ASSIGNED");
        assertThat(updated.getZoneId()).isEqualTo("zone-2");
        assertThat(updated.getZoneName()).isEqualTo("Cucumbers");
    }

    @Test
    void telemetryWithoutZoneClearsStaleAssignment() {
        ZoneRegistryStore store = new ZoneRegistryStore();

        store.assignDevice(
                "tenant-demo",
                "greenhouse-demo",
                "device-4",
                "zone-4",
                "Peppers",
                Map.of());

        ZoneDeviceRecord updated = store.touchFromTelemetry(
                "tenant-demo",
                "greenhouse-demo",
                "device-4",
                "device-4",
                null,
                Instant.now());

        assertThat(updated.isAssigned()).isFalse();
        assertThat(updated.getZoneId()).isNull();
        assertThat(updated.getZoneName()).isNull();
        assertThat(updated.getStatus()).isEqualTo("DISCOVERED");
    }

    @Test
    void staleEventsAreSuppressedUntilNewerTelemetryArrives() {
        ZoneRegistryStore store = new ZoneRegistryStore();

        Instant removedAt = Instant.parse("2026-04-16T16:00:00Z");
        store.removeDevice("tenant-demo", "greenhouse-demo", "device-5", removedAt);

        assertThat(store.isSuppressed(
                "tenant-demo",
                "greenhouse-demo",
                "device-5",
                Instant.parse("2026-04-16T15:59:00Z"))).isTrue();

        assertThat(store.isSuppressed(
                "tenant-demo",
                "greenhouse-demo",
                "device-5",
                Instant.parse("2026-04-16T16:00:10Z"))).isFalse();
    }

    @Test
    void removeDeviceDeletesRecordFromRegistry() {
        ZoneRegistryStore store = new ZoneRegistryStore();

        store.assignDevice(
                "tenant-demo",
                "greenhouse-demo",
                "device-3",
                "zone-3",
                "Peppers",
                Map.of());

        assertThat(store.removeDevice("tenant-demo", "greenhouse-demo", "device-3")).isPresent();
        assertThat(store.findDevice("tenant-demo", "greenhouse-demo", "device-3")).isEmpty();
    }
}
