package md.utm.gms.backend.store;

import md.utm.gms.backend.api.dto.SensorReadingResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store holding the latest reading for each sensor key.
 *
 * <p>Keyed by {@code sensor_key} (e.g. {@code soil_moisture}).
 * Each incoming telemetry message replaces the previous entry for that key,
 * so callers always receive the most recent value.
 *
 * <p>This store will be replaced by InfluxDB queries once the persistence
 * work-package is implemented. The controller interface remains unchanged.
 */
@Component
public class SensorReadingStore {

    private final ConcurrentHashMap<String, SensorReadingResponse> readings = new ConcurrentHashMap<>();

    public void update(SensorReadingResponse reading) {
        readings.put(reading.getSensorKey(), reading);
    }

    public List<SensorReadingResponse> getAll() {
        return new ArrayList<>(readings.values());
    }
}
