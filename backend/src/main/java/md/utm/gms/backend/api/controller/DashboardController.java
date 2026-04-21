package md.utm.gms.backend.api.controller;

import lombok.RequiredArgsConstructor;
import md.utm.gms.backend.auth.AuthContext;
import md.utm.gms.backend.api.dto.SensorHistoryResponse;
import md.utm.gms.backend.api.dto.SensorReadingResponse;
import md.utm.gms.backend.store.SensorReadingStore;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Provides the real-time sensor snapshot consumed by the React dashboard.
 *
 * <p>The dashboard polls {@code GET /v1/dashboard/live} every 10 seconds.
 * Each response contains the latest persisted reading per tracked sensor key.
 */
@RestController
@RequestMapping("/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final SensorReadingStore sensorReadingStore;

    /** Returns the latest reading snapshot persisted in {@code gms.latest_metric}. */
    @GetMapping("/live")
    public List<SensorReadingResponse> live(@RequestParam(value = "greenhouse_id", required = false) String greenhouseId,
                                            @RequestParam(value = "zone_id", required = false) String zoneId,
                                            Authentication authentication) {
        String tenantId = AuthContext.requireTenantId(authentication);

        if ((greenhouseId != null && !greenhouseId.isBlank()) || (zoneId != null && !zoneId.isBlank())) {
            return sensorReadingStore.getByScope(tenantId, greenhouseId, zoneId);
        }
        return sensorReadingStore.getAll(tenantId);
    }

    /**
     * Returns a bucketed time-series for a single sensor key.
     *
     * <p>Query parameters:
     * <ul>
     *   <li>{@code greenhouse_id} — required</li>
     *   <li>{@code sensor_key}    — required (e.g. {@code soil_moist}, {@code air_temp})</li>
     *   <li>{@code zone_id}       — optional filter</li>
     *   <li>{@code device_id}     — optional filter</li>
     *   <li>{@code from}          — ISO 8601 start (default: 24 h ago)</li>
     *   <li>{@code to}            — ISO 8601 end   (default: now)</li>
     *   <li>{@code granularity}   — {@code raw | minute | hourly | daily} (default: {@code hourly})</li>
     * </ul>
     */
    @GetMapping("/history")
    public ResponseEntity<SensorHistoryResponse> history(
            @RequestParam("greenhouse_id") String greenhouseId,
            @RequestParam("sensor_key") String sensorKey,
            @RequestParam(value = "zone_id",     required = false) String zoneId,
            @RequestParam(value = "device_id",   required = false) String deviceId,
            @RequestParam(value = "from",        required = false) String fromStr,
            @RequestParam(value = "to",          required = false) String toStr,
            @RequestParam(value = "granularity", defaultValue = "hourly") String granularity,
            Authentication authentication) {

        AuthContext.requireTenantId(authentication);

        Instant to   = parseInstantOrDefault(toStr,   Instant.now());
        Instant from = parseInstantOrDefault(fromStr, to.minus(24, ChronoUnit.HOURS));

        // Clamp to 90-day retention window
        Instant earliest = Instant.now().minus(90, ChronoUnit.DAYS);
        if (from.isBefore(earliest)) {
            from = earliest;
        }

        return ResponseEntity.ok(
                sensorReadingStore.getHistory(greenhouseId, zoneId, deviceId, sensorKey, from, to, granularity));
    }

    private static Instant parseInstantOrDefault(String value, Instant fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return fallback;
        }
    }
}
