package md.utm.gms.backend.api.controller;

import lombok.RequiredArgsConstructor;
import md.utm.gms.backend.api.dto.GreenhouseResponse;
import md.utm.gms.backend.api.dto.ThresholdApplyStatusResponse;
import md.utm.gms.backend.api.dto.ThresholdConfigResponse;
import md.utm.gms.backend.api.dto.ThresholdUpdateRequest;
import md.utm.gms.backend.auth.AuthContext;
import md.utm.gms.backend.auth.AuthenticatedUser;
import md.utm.gms.backend.mqtt.CommandService;
import md.utm.gms.backend.store.GreenhouseStore;
import md.utm.gms.backend.store.ThresholdApplyStatusStore;
import md.utm.gms.backend.store.ThresholdStore;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/g/{greenhouse_id}/zones/{zone_id}/thresholds")
@RequiredArgsConstructor
public class ThresholdController {

    private final ThresholdStore thresholdStore;
    private final ThresholdApplyStatusStore thresholdApplyStatusStore;
    private final GreenhouseStore greenhouseStore;
    private final CommandService commandService;

    @GetMapping
    public ThresholdConfigResponse getThresholds(@PathVariable("greenhouse_id") String greenhouseId,
                                                 @PathVariable("zone_id") String zoneId,
                                                 Authentication authentication) {
        String tenantId = AuthContext.requireTenantId(authentication);
        requireGreenhouse(tenantId, greenhouseId);
        ThresholdConfigResponse response = thresholdStore.get(tenantId, greenhouseId, zoneId);
        thresholdApplyStatusStore.findLatest(tenantId, greenhouseId, zoneId)
                .ifPresent(response::setApplyStatus);
        return response;
    }

    @GetMapping("/status")
    public ThresholdApplyStatusResponse getStatus(@PathVariable("greenhouse_id") String greenhouseId,
                                                  @PathVariable("zone_id") String zoneId,
                                                  Authentication authentication) {
        String tenantId = AuthContext.requireTenantId(authentication);
        requireGreenhouse(tenantId, greenhouseId);
        return thresholdApplyStatusStore.findLatest(tenantId, greenhouseId, zoneId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Threshold status not found."));
    }

    @PutMapping
    public ThresholdConfigResponse putThresholds(@PathVariable("greenhouse_id") String greenhouseId,
                                                 @PathVariable("zone_id") String zoneId,
                                                 @RequestBody ThresholdUpdateRequest request,
                                                 Authentication authentication) {
        AuthenticatedUser user = AuthContext.requireUser(authentication);
        String tenantId = user.getTenantId();
        GreenhouseResponse greenhouse = requireGreenhouse(tenantId, greenhouseId);

        String commandId = UUID.randomUUID().toString();
        ThresholdConfigResponse saved;
        try {
            saved = thresholdStore.put(
                    tenantId,
                    greenhouseId,
                    zoneId,
                    request == null ? null : request.thresholds(),
                    user.getUsername(),
                    commandId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }

        String gatewayId = defaultString(greenhouse.gatewayId(), greenhouse.greenhouseId());
        thresholdApplyStatusStore.markPending(
                tenantId,
                greenhouseId,
                zoneId,
                gatewayId,
                saved.getConfigVersion(),
                commandId);

        Map<String, Object> downlink = new HashMap<>();
        downlink.put("command_id", commandId);
        downlink.put("type", "THRESHOLD_CONFIG_UPDATE");
        downlink.put("tenant_id", tenantId);
        downlink.put("greenhouse_id", greenhouseId);
        downlink.put("gateway_id", gatewayId);
        downlink.put("zone_id", zoneId);
        downlink.put("config_version", saved.getConfigVersion());
        downlink.put("issued_at", Instant.now().toString());
        downlink.put("thresholds", saved.getThresholds());

        try {
            commandService.sendCommand(downlinkTopic(tenantId, greenhouseId), downlink);
        } catch (Exception e) {
            thresholdApplyStatusStore.markFailed(commandId, "Unable to publish threshold config to gateway broker.");
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to publish threshold config to gateway broker. Verify MQTT connectivity.",
                    e
            );
        }

        thresholdApplyStatusStore.findLatest(tenantId, greenhouseId, zoneId)
                .ifPresent(saved::setApplyStatus);
        return saved;
    }

    private GreenhouseResponse requireGreenhouse(String tenantId, String greenhouseId) {
        return greenhouseStore.find(tenantId, greenhouseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Greenhouse not found."));
    }

    private static String downlinkTopic(String tenantId, String greenhouseId) {
        return "gms/%s/%s/downlink/threshold".formatted(tenantId, greenhouseId);
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
