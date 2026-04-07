package md.utm.gms.backend.api.controller;

import lombok.RequiredArgsConstructor;
import md.utm.gms.backend.api.dto.SensorReadingResponse;
import md.utm.gms.backend.store.SensorReadingStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Provides the real-time sensor snapshot consumed by the React dashboard.
 *
 * <p>The dashboard polls {@code GET /v1/dashboard/live} every 10 seconds.
 * Each response contains the most recent reading for every sensor key seen
 * since the backend started (or since the last restart).
 */
@RestController
@RequestMapping("/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final SensorReadingStore sensorReadingStore;

    /**
     * Returns the latest reading for every sensor currently tracked in memory.
     *
     * <p>Once InfluxDB persistence is wired up this will query the
     * {@code sensor_reading} measurement for the most recent point per sensor key.
     */
    @GetMapping("/live")
    public List<SensorReadingResponse> live() {
        return sensorReadingStore.getAll();
    }
}
