package md.utm.gms.backend.api.controller;

import lombok.RequiredArgsConstructor;
import md.utm.gms.backend.api.dto.SensorReadingResponse;
import md.utm.gms.backend.store.SensorReadingStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
                                            @RequestParam(value = "zone_id", required = false) String zoneId) {
        if ((greenhouseId != null && !greenhouseId.isBlank()) || (zoneId != null && !zoneId.isBlank())) {
            return sensorReadingStore.getByScope(greenhouseId, zoneId);
        }
        return sensorReadingStore.getAll();
    }
}
