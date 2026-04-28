package md.utm.gms.backend.api.controller;

import lombok.RequiredArgsConstructor;
import md.utm.gms.backend.auth.AuthContext;
import md.utm.gms.backend.store.GreenhouseStore;
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

import java.util.Map;

@RestController
@RequestMapping("/v1/g/{greenhouse_id}/zones/{zone_id}/thresholds")
@RequiredArgsConstructor
public class ThresholdController {

    private final ThresholdStore thresholdStore;
    private final GreenhouseStore greenhouseStore;

    @GetMapping
    public Map<String, Object> getThresholds(@PathVariable("greenhouse_id") String greenhouseId,
                                             @PathVariable("zone_id") String zoneId,
                                             Authentication authentication) {
        String tenantId = AuthContext.requireTenantId(authentication);
        requireGreenhouse(tenantId, greenhouseId);
        return thresholdStore.get(tenantId, greenhouseId, zoneId);
    }

    @PutMapping
    public Map<String, Object> putThresholds(@PathVariable("greenhouse_id") String greenhouseId,
                                             @PathVariable("zone_id") String zoneId,
                                             @RequestBody Map<String, Object> body,
                                             Authentication authentication) {
        String tenantId = AuthContext.requireTenantId(authentication);
        requireGreenhouse(tenantId, greenhouseId);
        return thresholdStore.put(tenantId, greenhouseId, zoneId, body);
    }

    private void requireGreenhouse(String tenantId, String greenhouseId) {
        greenhouseStore.find(tenantId, greenhouseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Greenhouse not found."));
    }
}
