package md.utm.gms.backend.api.controller;

import jakarta.validation.Valid;
import md.utm.gms.backend.api.dto.ThresholdConfigResponse;
import md.utm.gms.backend.api.dto.ZoneAssignRequest;
import md.utm.gms.backend.api.dto.ZoneCommandRequest;
import md.utm.gms.backend.api.dto.ZoneDeviceResponse;
import md.utm.gms.backend.api.dto.GreenhouseResponse;
import md.utm.gms.backend.api.dto.ZoneRegistryResponse;
import md.utm.gms.backend.api.dto.ZoneSyncRequest;
import md.utm.gms.backend.api.dto.ZoneUnassignRequest;
import md.utm.gms.backend.auth.AuthContext;
import md.utm.gms.backend.mqtt.CommandService;
import md.utm.gms.backend.mqtt.dto.CommandAckPayload;
import md.utm.gms.backend.store.CommandAckStore;
import md.utm.gms.backend.store.GreenhouseStore;
import md.utm.gms.backend.store.ThresholdApplyStatusStore;
import md.utm.gms.backend.store.ThresholdStore;
import md.utm.gms.backend.zones.ZoneDeviceRecord;
import md.utm.gms.backend.zones.ZoneRegistryStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/v1/g/{greenhouse_id}/zones")
public class ZoneController {

    private final ZoneRegistryStore zoneRegistryStore;
    private final CommandService commandService;
    private final CommandAckStore commandAckStore;
    private final GreenhouseStore greenhouseStore;
    private final ThresholdStore thresholdStore;
    private final ThresholdApplyStatusStore thresholdApplyStatusStore;

    public ZoneController(ZoneRegistryStore zoneRegistryStore,
                          CommandService commandService,
                          CommandAckStore commandAckStore,
                          GreenhouseStore greenhouseStore,
                          ThresholdStore thresholdStore,
                          ThresholdApplyStatusStore thresholdApplyStatusStore) {
        this.zoneRegistryStore = zoneRegistryStore;
        this.commandService = commandService;
        this.commandAckStore = commandAckStore;
        this.greenhouseStore = greenhouseStore;
        this.thresholdStore = thresholdStore;
        this.thresholdApplyStatusStore = thresholdApplyStatusStore;
    }

    @GetMapping("/registry")
    public ZoneRegistryResponse registry(@PathVariable("greenhouse_id") String greenhouseId,
                                         Authentication authentication) {
        String tenantId = AuthContext.requireTenantId(authentication);
        requireGreenhouse(tenantId, greenhouseId);

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
    public ResponseEntity<Map<String, Object>> assign(@PathVariable("greenhouse_id") String greenhouseId,
                                                       @Valid @RequestBody ZoneAssignRequest request,
                                                       Authentication authentication) {
        String tenantId = AuthContext.requireTenantId(authentication);
        GreenhouseResponse greenhouse = requireGreenhouse(tenantId, greenhouseId);

        ZoneDeviceRecord updated = zoneRegistryStore.assignDevice(
                tenantId,
                greenhouseId,
                request.getDeviceId(),
                request.getZoneId(),
                request.getZoneName(),
                request.getMetadata());

        String commandId = UUID.randomUUID().toString();
        Map<String, Object> command = new HashMap<>();
        command.put("command_id", commandId);
        command.put("type", "ASSIGN_ZONE");
        command.put("tenant_id", tenantId);
        command.put("greenhouse_id", greenhouseId);
        command.put("gateway_id", greenhouse.gatewayId());
        command.put("device_id", request.getDeviceId());
        command.put("zone_id", updated.getZoneId());
        command.put("zone_name", updated.getZoneName());
        command.put("issued_at", Instant.now().toString());
        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            command.put("metadata", request.getMetadata());
        }

        publishDownlink(downlinkTopic(tenantId, greenhouseId, "registry"), command);

        ensureThresholdConfig(tenantId, greenhouseId, updated.getZoneId(), greenhouse);

        return ResponseEntity.ok(Map.of(
                "command_id", commandId,
                "topic", downlinkTopic(tenantId, greenhouseId, "registry"),
                "device", ZoneDeviceResponse.from(updated)
        ));
    }

    @PostMapping("/unassign")
    public ResponseEntity<Map<String, Object>> unassign(@PathVariable("greenhouse_id") String greenhouseId,
                                                         @Valid @RequestBody ZoneUnassignRequest request,
                                                         Authentication authentication) {
        String tenantId = AuthContext.requireTenantId(authentication);
        GreenhouseResponse greenhouse = requireGreenhouse(tenantId, greenhouseId);

        Optional<ZoneDeviceRecord> updated = zoneRegistryStore.unassignDevice(
                tenantId,
                greenhouseId,
                request.getDeviceId());

        if (updated.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String commandId = UUID.randomUUID().toString();
        Map<String, Object> command = Map.of(
                "command_id", commandId,
                "type", "UNASSIGN_ZONE",
                "tenant_id", tenantId,
                "greenhouse_id", greenhouseId,
                "gateway_id", greenhouse.gatewayId(),
                "device_id", request.getDeviceId(),
                "issued_at", Instant.now().toString()
        );

        publishDownlink(downlinkTopic(tenantId, greenhouseId, "registry"), command);

        return ResponseEntity.ok(Map.of(
                "command_id", commandId,
                "topic", downlinkTopic(tenantId, greenhouseId, "registry"),
                "device", ZoneDeviceResponse.from(updated.get())
        ));
    }

    @PostMapping("/command")
    public ResponseEntity<Map<String, Object>> command(@PathVariable("greenhouse_id") String greenhouseId,
                                                        @Valid @RequestBody ZoneCommandRequest request,
                                                        Authentication authentication) {
        String tenantId = AuthContext.requireTenantId(authentication);
        GreenhouseResponse greenhouse = requireGreenhouse(tenantId, greenhouseId);

        String resolvedDeviceId = request.getDeviceId();

        if ((resolvedDeviceId == null || resolvedDeviceId.isBlank())
                && request.getZoneId() != null && !request.getZoneId().isBlank()) {
            resolvedDeviceId = zoneRegistryStore
                    .findByZoneId(tenantId, greenhouseId, request.getZoneId())
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
        payload.put("tenant_id", tenantId);
        payload.put("greenhouse_id", greenhouseId);
        payload.put("gateway_id", greenhouse.gatewayId());
        payload.put("device_id", resolvedDeviceId);
        payload.put("zone_id", request.getZoneId());
        payload.put("issued_at", Instant.now().toString());
        if (request.getPayload() != null && !request.getPayload().isEmpty()) {
            payload.put("payload", request.getPayload());
        }

        String topic = downlinkTopic(tenantId, greenhouseId, "command");
        publishDownlink(topic, payload);

        return ResponseEntity.ok(Map.of(
                "command_id", commandId,
                "topic", topic,
                "device_id", resolvedDeviceId
        ));
    }

    @GetMapping("/command-ack")
    public ResponseEntity<CommandAckPayload> commandAck(@PathVariable("greenhouse_id") String greenhouseId,
                                                         @RequestParam("command_id") String commandId,
                                                         Authentication authentication) {
        String tenantId = AuthContext.requireTenantId(authentication);
        requireGreenhouse(tenantId, greenhouseId);

        return commandAckStore
                .findByCommandId(commandId, tenantId, greenhouseId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync(@PathVariable("greenhouse_id") String greenhouseId,
                                                     @Valid @RequestBody ZoneSyncRequest request,
                                                     Authentication authentication) {
        String tenantId = AuthContext.requireTenantId(authentication);
        GreenhouseResponse greenhouse = requireGreenhouse(tenantId, greenhouseId);

        String commandId = UUID.randomUUID().toString();

        List<Map<String, Object>> zones = zoneRegistryStore.listByGreenhouse(tenantId, greenhouseId).stream()
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

        String gatewayId = request.gatewayIdOrDefault(greenhouse.gatewayId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("command_id", commandId);
        payload.put("type", "ZONE_REGISTRY_SYNC");
        payload.put("tenant_id", tenantId);
        payload.put("greenhouse_id", greenhouseId);
        payload.put("gateway_id", gatewayId);
        payload.put("config_version", Instant.now().toString());
        payload.put("issued_at", Instant.now().toString());
        payload.put("zones", zones);

        String topic = downlinkTopic(tenantId, greenhouseId, "registry");
        publishDownlink(topic, payload);

        Set<String> syncedZoneIds = new HashSet<>();
        for (Map<String, Object> zone : zones) {
            Object zid = zone.get("zone_id");
            if (zid != null) syncedZoneIds.add(zid.toString());
        }
        for (String zid : syncedZoneIds) {
            ensureThresholdConfig(tenantId, greenhouseId, zid, greenhouse);
        }

        return ResponseEntity.ok(Map.of(
                "command_id", commandId,
                "topic", topic,
                "gateway_id", gatewayId,
                "zones_count", zones.size()
        ));
    }

    private GreenhouseResponse requireGreenhouse(String tenantId, String greenhouseId) {
        return greenhouseStore.find(tenantId, greenhouseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Greenhouse not found."));
    }

    private static String downlinkTopic(String tenantId, String greenhouseId, String stream) {
        return "gms/%s/%s/downlink/%s".formatted(tenantId, greenhouseId, stream);
    }

    private void publishDownlink(String topic, Object payload) {
        try {
            commandService.sendCommand(topic, payload);
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to publish command to gateway broker. Verify MQTT connectivity.",
                    ex
            );
        }
    }

    /**
     * If no threshold config row exists for the given zone, saves default thresholds
     * and publishes a downlink command so the edge engine caches them immediately.
     */
    private void ensureThresholdConfig(String tenantId, String greenhouseId, String zoneId,
                                       GreenhouseResponse greenhouse) {
        if (zoneId == null || zoneId.isBlank()) return;

        ThresholdConfigResponse existing = thresholdStore.get(tenantId, greenhouseId, zoneId);
        if (existing.getConfigVersion() > 0) {
            // Config already persisted — push existing config to edge in case it was missed
            pushThresholdDownlink(tenantId, greenhouseId, zoneId, greenhouse, existing);
            return;
        }

        // No config yet — save defaults and push
        String commandId = UUID.randomUUID().toString();
        try {
            ThresholdConfigResponse saved = thresholdStore.put(
                    tenantId, greenhouseId, zoneId,
                    thresholdStore.defaultThresholds(),
                    "system", commandId);

            String gatewayId = defaultString(greenhouse.gatewayId(), greenhouse.greenhouseId());
            thresholdApplyStatusStore.markPending(
                    tenantId, greenhouseId, zoneId, gatewayId,
                    saved.getConfigVersion(), commandId);

            pushThresholdDownlink(tenantId, greenhouseId, zoneId, greenhouse, saved);
        } catch (Exception e) {
            // Non-fatal: zone assignment succeeded even if threshold push fails
        }
    }

    private void pushThresholdDownlink(String tenantId, String greenhouseId, String zoneId,
                                       GreenhouseResponse greenhouse, ThresholdConfigResponse config) {
        String gatewayId = defaultString(greenhouse.gatewayId(), greenhouse.greenhouseId());
        String commandId = config.getCommandId() != null ? config.getCommandId() : UUID.randomUUID().toString();

        Map<String, Object> downlink = new HashMap<>();
        downlink.put("command_id", commandId);
        downlink.put("type", "THRESHOLD_CONFIG_UPDATE");
        downlink.put("tenant_id", tenantId);
        downlink.put("greenhouse_id", greenhouseId);
        downlink.put("gateway_id", gatewayId);
        downlink.put("zone_id", zoneId);
        downlink.put("config_version", config.getConfigVersion());
        downlink.put("issued_at", Instant.now().toString());
        downlink.put("thresholds", config.getThresholds());

        try {
            commandService.sendCommand(downlinkTopic(tenantId, greenhouseId, "threshold"), downlink);
        } catch (Exception ignored) {
            // Non-fatal: threshold push is best-effort during zone assignment
        }
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
