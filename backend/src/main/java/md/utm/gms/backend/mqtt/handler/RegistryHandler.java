package md.utm.gms.backend.mqtt.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import md.utm.gms.backend.mqtt.dto.RegistryEventPayload;
import md.utm.gms.backend.zones.ZoneDeviceRecord;
import md.utm.gms.backend.zones.ZoneRegistryStore;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RegistryHandler {

    private final ObjectMapper objectMapper;
    private final ZoneRegistryStore zoneRegistryStore;

    @ServiceActivator(inputChannel = "registryChannel")
    public void handle(Message<String> message) {
        try {
            RegistryEventPayload payload = objectMapper.readValue(message.getPayload(), RegistryEventPayload.class);

            if (isBlank(payload.getTenantId()) || isBlank(payload.getGreenhouseId()) || isBlank(payload.getDeviceId())) {
                log.warn("Registry event missing tenant/greenhouse/device fields: {}", message.getPayload());
                return;
            }

            String type = normalizeType(payload.getType());
            Optional<ZoneDeviceRecord> updated = switch (type) {
                case "ZONE_ASSIGNMENT_APPLIED", "ZONE_UNASSIGNED_APPLIED", "ASSIGNMENT_ACK" ->
                        Optional.of(zoneRegistryStore.applyAssignmentAck(payload));
                case "DEVICE_REMOVED", "DEVICE_DECOMMISSIONED" ->
                        zoneRegistryStore.removeDevice(
                                payload.getTenantId(),
                                payload.getGreenhouseId(),
                                payload.getDeviceId(),
                                payload.getTimestamp());
                default -> {
                    if (zoneRegistryStore.isSuppressed(
                            payload.getTenantId(),
                            payload.getGreenhouseId(),
                            payload.getDeviceId(),
                            payload.getTimestamp())) {
                        log.debug("Ignoring stale registry event type={} greenhouse={} device={} ts={}",
                                payload.getType(),
                                payload.getGreenhouseId(),
                                payload.getDeviceId(),
                                payload.getTimestamp());
                        yield Optional.empty();
                    }
                    yield Optional.of(zoneRegistryStore.upsertDiscovery(payload));
                }
            };

            if (updated.isPresent()) {
                ZoneDeviceRecord record = updated.get();
                log.info("Registry event type={} commandId={} greenhouse={} device={} zone={} status={}",
                        payload.getType(),
                        payload.getCommandId(),
                        payload.getGreenhouseId(),
                        payload.getDeviceId(),
                        record.getZoneId(),
                        record.getStatus());
                return;
            }

            log.info("Registry event type={} commandId={} greenhouse={} device={} removed=true",
                    payload.getType(),
                    payload.getCommandId(),
                    payload.getGreenhouseId(),
                    payload.getDeviceId());
        } catch (Exception e) {
            log.error("Failed to process registry event payload='{}' error='{}'",
                    message.getPayload(), e.getMessage(), e);
        }
    }

    private static String normalizeType(String type) {
        if (type == null) {
            return "";
        }
        return type.trim().toUpperCase();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
