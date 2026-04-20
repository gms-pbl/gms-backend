package md.utm.gms.backend.api.controller;

import lombok.RequiredArgsConstructor;
import md.utm.gms.backend.auth.AuthContext;
import md.utm.gms.backend.api.dto.AlertResponse;
import md.utm.gms.backend.store.AlertStore;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Alert management endpoints consumed by the React dashboard.
 *
 * <p>Alerts are populated by the MQTT {@code AlertHandler} as edge devices
 * publish threshold-breach events. The dashboard can acknowledge or dismiss them.
 */
@RestController
@RequestMapping("/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertStore alertStore;

    /**
     * Returns all active alerts sorted by: unacknowledged first,
     * then CRITICAL → WARNING → INFO.
     */
    @GetMapping
    public List<AlertResponse> getAlerts(Authentication authentication) {
        String tenantId = AuthContext.requireTenantId(authentication);
        return alertStore.getAll(tenantId);
    }

    /**
     * Marks an alert as acknowledged. The alert remains visible in the dashboard
     * but is visually dimmed. Operator role will be required once RBAC is in place.
     *
     * @return 200 with the updated alert, or 404 if the id is unknown.
     */
    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<AlertResponse> acknowledge(@PathVariable String id,
                                                     Authentication authentication) {
        String tenantId = AuthContext.requireTenantId(authentication);

        return alertStore.acknowledge(tenantId, id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Removes an alert from the active list. Only available for INFO alerts
     * and already-acknowledged alerts (enforced client-side; server accepts any id).
     *
     * @return 204 on success, 404 if the id is unknown.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> dismiss(@PathVariable String id,
                                        Authentication authentication) {
        String tenantId = AuthContext.requireTenantId(authentication);

        return alertStore.dismiss(tenantId, id)
                ? ResponseEntity.noContent().<Void>build()
                : ResponseEntity.notFound().<Void>build();
    }
}
