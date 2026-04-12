package md.utm.gms.backend.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import md.utm.gms.backend.api.dto.ZoneAssignRequest;
import md.utm.gms.backend.api.dto.ZoneCommandRequest;
import md.utm.gms.backend.api.dto.ZoneDeviceResponse;
import md.utm.gms.backend.api.dto.ZoneRegistryResponse;
import md.utm.gms.backend.api.dto.ZoneSyncRequest;
import md.utm.gms.backend.api.dto.ZoneUnassignRequest;
import md.utm.gms.backend.mqtt.CommandService;
import md.utm.gms.backend.mqtt.dto.CommandAckPayload;
import md.utm.gms.backend.store.CommandAckStore;
import md.utm.gms.backend.zones.ZoneDeviceRecord;
import md.utm.gms.backend.zones.ZoneRegistryStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneRegistryStore zoneRegistryStore;
    private final CommandService commandService;
    private final CommandAckStore commandAckStore;

    @GetMapping("/registry")
    public ZoneRegistryResponse registry(@RequestParam("tenant_id") String tenantId,
                                         @RequestParam("greenhouse_id") String greenhouseId) {

        List<ZoneDeviceResponse> assigned = zoneRegistryStore.listByGreenhouse(tenantId, greenhouseId).stream()
                .filter(ZoneDeviceRecord::isAssigned)
                .map(ZoneDeviceResponse::from)
                .toList();

        List<ZoneDeviceResponse> discovered = zoneRegistryStore.listByGreenhouse(tenantId, greenhouseId).stream()
                .filter(record -> !record.isAssigned())
                .map(ZoneDeviceResponse::from)
                .toList();

        return ZoneRegistryResponse.builder()
                .tenantId(tenantId)
                .greenhouseId(greenhouseId)
                .assignedZones(assigned)
                .discoveredDevices(discovered)
                .build();
    }

    @PostMapping("/assign")
    public ResponseEntity<Map<String, Object>> assign(@Valid @RequestBody ZoneAssignRequest request) {
        ZoneDeviceRecord updated = zoneRegistryStore.assignDevice(
                request.getTenantId(),
                request.getGreenhouseId(),
                request.getDeviceId(),
                request.getZoneId(),
                request.getZoneName(),
                request.getMetadata());

        String commandId = UUID.randomUUID().toString();
        Map<String, Object> command = new HashMap<>();
        command.put("command_id", commandId);
        command.put("type", "ASSIGN_ZONE");
        command.put("tenant_id", request.getTenantId());
        command.put("greenhouse_id", request.getGreenhouseId());
        command.put("gateway_id", request.getGreenhouseId());
        command.put("device_id", request.getDeviceId());
        command.put("zone_id", updated.getZoneId());
        command.put("zone_name", updated.getZoneName());
        command.put("issued_at", Instant.now().toString());
        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            command.put("metadata", request.getMetadata());
        }

        commandService.sendCommand(downlinkTopic(request.getTenantId(), request.getGreenhouseId(), "registry"), command);

        return ResponseEntity.ok(Map.of(
                "command_id", commandId,
                "topic", downlinkTopic(request.getTenantId(), request.getGreenhouseId(), "registry"),
                "device", ZoneDeviceResponse.from(updated)
        ));
    }

    @PostMapping("/unassign")
    public ResponseEntity<Map<String, Object>> unassign(@Valid @RequestBody ZoneUnassignRequest request) {
        Optional<ZoneDeviceRecord> updated = zoneRegistryStore.unassignDevice(
                request.getTenantId(),
                request.getGreenhouseId(),
                request.getDeviceId());

        if (updated.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String commandId = UUID.randomUUID().toString();
        Map<String, Object> command = Map.of(
                "command_id", commandId,
                "type", "UNASSIGN_ZONE",
                "tenant_id", request.getTenantId(),
                "greenhouse_id", request.getGreenhouseId(),
                "gateway_id", request.getGreenhouseId(),
                "device_id", request.getDeviceId(),
                "issued_at", Instant.now().toString()
        );

        commandService.sendCommand(downlinkTopic(request.getTenantId(), request.getGreenhouseId(), "registry"), command);

        return ResponseEntity.ok(Map.of(
                "command_id", commandId,
                "topic", downlinkTopic(request.getTenantId(), request.getGreenhouseId(), "registry"),
                "device", ZoneDeviceResponse.from(updated.get())
        ));
    }

    @PostMapping("/command")
    public ResponseEntity<Map<String, Object>> command(@Valid @RequestBody ZoneCommandRequest request) {
        String resolvedDeviceId = request.getDeviceId();

        if ((resolvedDeviceId == null || resolvedDeviceId.isBlank())
                && request.getZoneId() != null && !request.getZoneId().isBlank()) {
            resolvedDeviceId = zoneRegistryStore
                    .findByZoneId(request.getTenantId(), request.getGreenhouseId(), request.getZoneId())
                    .map(ZoneDeviceRecord::getDeviceId)
                    .orElse(null);
        }

        if (resolvedDeviceId == null || resolvedDeviceId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Target device could not be resolved. Provide device_id or a valid zone_id."
            ));
        }

        String commandId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("command_id", commandId);
        payload.put("type", request.getAction());
        payload.put("tenant_id", request.getTenantId());
        payload.put("greenhouse_id", request.getGreenhouseId());
        payload.put("gateway_id", request.getGreenhouseId());
        payload.put("device_id", resolvedDeviceId);
        payload.put("zone_id", request.getZoneId());
        payload.put("issued_at", Instant.now().toString());
        if (request.getPayload() != null && !request.getPayload().isEmpty()) {
            payload.put("payload", request.getPayload());
        }

        String topic = downlinkTopic(request.getTenantId(), request.getGreenhouseId(), "command");
        commandService.sendCommand(topic, payload);

        return ResponseEntity.ok(Map.of(
                "command_id", commandId,
                "topic", topic,
                "device_id", resolvedDeviceId
        ));
    }

    @GetMapping("/command-ack")
    public ResponseEntity<CommandAckPayload> commandAck(@RequestParam("command_id") String commandId) {
        return commandAckStore
                .findByCommandId(commandId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync(@Valid @RequestBody ZoneSyncRequest request) {
        String commandId = UUID.randomUUID().toString();

        List<Map<String, Object>> zones = zoneRegistryStore.listByGreenhouse(request.getTenantId(), request.getGreenhouseId()).stream()
                .filter(ZoneDeviceRecord::isAssigned)
                .map(record -> {
                    Map<String, Object> zone = new HashMap<>();
                    zone.put("zone_id", record.getZoneId());
                    zone.put("zone_name", record.getZoneName());
                    zone.put("device_id", record.getDeviceId());
                    if (record.getMetadata() != null && !record.getMetadata().isEmpty()) {
                        zone.put("metadata", record.getMetadata());
                    }
                    return zone;
                })
                .toList();

        String gatewayId = request.gatewayIdOrDefault(request.getGreenhouseId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("command_id", commandId);
        payload.put("type", "ZONE_REGISTRY_SYNC");
        payload.put("tenant_id", request.getTenantId());
        payload.put("greenhouse_id", request.getGreenhouseId());
        payload.put("gateway_id", gatewayId);
        payload.put("config_version", Instant.now().toString());
        payload.put("issued_at", Instant.now().toString());
        payload.put("zones", zones);

        String topic = downlinkTopic(request.getTenantId(), request.getGreenhouseId(), "registry");
        commandService.sendCommand(topic, payload);

        return ResponseEntity.ok(Map.of(
                "command_id", commandId,
                "topic", topic,
                "gateway_id", gatewayId,
                "zones_count", zones.size()
        ));
    }

    private static String downlinkTopic(String tenantId, String greenhouseId, String stream) {
        return "gms/%s/%s/downlink/%s".formatted(tenantId, greenhouseId, stream);
    }
}
