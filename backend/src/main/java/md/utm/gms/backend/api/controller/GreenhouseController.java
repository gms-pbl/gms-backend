package md.utm.gms.backend.api.controller;

import jakarta.validation.Valid;
import md.utm.gms.backend.api.dto.GreenhouseCreateRequest;
import md.utm.gms.backend.api.dto.GreenhouseGatewayConfigResponse;
import md.utm.gms.backend.api.dto.GreenhouseResponse;
import md.utm.gms.backend.api.dto.GreenhouseUpdateRequest;
import md.utm.gms.backend.auth.AuthContext;
import md.utm.gms.backend.store.GreenhouseStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/v1/g")
public class GreenhouseController {

    private final GreenhouseStore greenhouseStore;

    public GreenhouseController(GreenhouseStore greenhouseStore) {
        this.greenhouseStore = greenhouseStore;
    }

    @GetMapping
    public List<GreenhouseResponse> list(Authentication authentication) {
        String tenantId = AuthContext.requireTenantId(authentication);
        return greenhouseStore.listByTenant(tenantId);
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody GreenhouseCreateRequest request,
                                    Authentication authentication) {
        String tenantId = AuthContext.requireTenantId(authentication);

        String greenhouseId = sanitizeIdentifier(request.greenhouseId());
        if (greenhouseId == null) {
            greenhouseId = sanitizeIdentifier(request.name());
        }
        if (greenhouseId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unable to derive greenhouse_id."));
        }

        if (greenhouseStore.exists(tenantId, greenhouseId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "greenhouse_id already exists."));
        }

        String gatewayId = sanitizeIdentifier(request.gatewayId());
        if (gatewayId == null) {
            gatewayId = greenhouseId;
        }

        try {
            GreenhouseResponse created = greenhouseStore.create(tenantId, greenhouseId, gatewayId, request.name().trim());
            return ResponseEntity.ok(created);
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "gateway_id is already assigned in this tenant."));
        }
    }

    @GetMapping("/{greenhouse_id}")
    public ResponseEntity<GreenhouseResponse> get(@PathVariable("greenhouse_id") String greenhouseId,
                                                  Authentication authentication) {
        String tenantId = AuthContext.requireTenantId(authentication);
        return greenhouseStore.find(tenantId, greenhouseId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{greenhouse_id}")
    public ResponseEntity<?> update(@PathVariable("greenhouse_id") String greenhouseId,
                                    @RequestBody GreenhouseUpdateRequest request,
                                    Authentication authentication) {
        String tenantId = AuthContext.requireTenantId(authentication);

        if (!request.hasUpdates()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No fields provided for update."));
        }

        String normalizedGatewayId = sanitizeIdentifier(request.gatewayId());
        String normalizedName = request.name() != null ? request.name().trim() : null;

        try {
            return greenhouseStore
                    .update(tenantId, greenhouseId, normalizedName, normalizedGatewayId)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "gateway_id is already assigned in this tenant."));
        }
    }

    @DeleteMapping("/{greenhouse_id}")
    public ResponseEntity<Void> delete(@PathVariable("greenhouse_id") String greenhouseId,
                                       Authentication authentication) {
        String tenantId = AuthContext.requireTenantId(authentication);
        boolean deleted = greenhouseStore.deleteHard(tenantId, greenhouseId);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/{greenhouse_id}/gateway-config")
    public ResponseEntity<GreenhouseGatewayConfigResponse> gatewayConfig(@PathVariable("greenhouse_id") String greenhouseId,
                                                                         Authentication authentication) {
        String tenantId = AuthContext.requireTenantId(authentication);

        return greenhouseStore.find(tenantId, greenhouseId)
                .map(g -> {
                    Map<String, String> env = new LinkedHashMap<>();
                    env.put("TENANT_ID", g.tenantId());
                    env.put("GREENHOUSE_ID", g.greenhouseId());
                    env.put("GATEWAY_ID", g.gatewayId());
                    env.put("CLOUD_BROKER_HOST", "<your-mqtt-broker-host>");
                    env.put("CLOUD_BROKER_PORT", "8883");

                    return ResponseEntity.ok(new GreenhouseGatewayConfigResponse(
                            g.tenantId(),
                            g.greenhouseId(),
                            g.gatewayId(),
                            env
                    ));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static String sanitizeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");

        return normalized.isBlank() ? null : normalized;
    }
}
