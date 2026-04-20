package md.utm.gms.backend.api.controller;

import lombok.RequiredArgsConstructor;
import md.utm.gms.backend.api.dto.AlertResponse;
import md.utm.gms.backend.api.dto.SensorReadingResponse;
import md.utm.gms.backend.store.AlertStore;
import md.utm.gms.backend.store.SensorReadingStore;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DEV-ONLY — populates persistence stores with realistic sample data
 * so Swagger UI can be tested without a running MQTT broker.
 *
 * <p>This controller is <strong>not</strong> present in the production build —
 * {@code @Profile("dev")} ensures Spring never registers it outside the dev profile.
 */
@Profile("dev")
@RestController
@RequestMapping("/v1/test")
@RequiredArgsConstructor
public class TestDataController {

    private static final String DEMO_TENANT = "tenant-demo";
    private static final String DEMO_GREENHOUSE = "greenhouse-demo";

    private final SensorReadingStore sensorReadingStore;
    private final AlertStore alertStore;

    /**
     * Seeds both stores with one realistic reading per sensor key and
     * three sample alerts (CRITICAL, WARNING, INFO).
     *
     * @return a short summary of what was inserted.
     */
    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> seed() {

        // ── Sensor readings ───────────────────────────────────────────────
        List.of(
            reading("soil_moisture",   62.4,  "%",     "OK"),
            reading("air_temperature", 24.1,  "°C",    "OK"),
            reading("air_humidity",    71.0,  "%RH",   "WARN"),
            reading("soil_temp",       21.3,  "°C",    "OK"),
            reading("soil_ec",          1.8,  "dS/m",  "OK"),
            reading("soil_ph",          6.7,  "pH",    "OK"),
            reading("soil_nitrogen",   34.0,  "mg/kg", "WARN"),
            reading("soil_phosphorus", 18.0,  "mg/kg", "OK"),
            reading("soil_potassium", 210.0,  "mg/kg", "OK"),
            reading("soil_salinity",    1.2,  "ppt",   "OK")
        ).forEach(sensorReadingStore::update);

        // ── Alerts ────────────────────────────────────────────────────────
        List.of(
            alert("a1", "CRITICAL", "soil_moisture",
                    "Soil moisture critically low: 12% (min 30%)",
                    Instant.parse("2026-04-06T08:14:00Z"), false),
            alert("a2", "WARNING",  "air_humidity",
                    "Air humidity above threshold: 71%RH (max 65%)",
                    Instant.parse("2026-04-06T09:02:00Z"), false),
            alert("a3", "WARNING",  "soil_nitrogen",
                    "Nitrogen low: 34 mg/kg (min 40 mg/kg)",
                    Instant.parse("2026-04-06T07:45:00Z"), false),
            alert("a4", "INFO",     "air_temperature",
                    "Temperature nominal after morning fluctuation",
                    Instant.parse("2026-04-06T06:30:00Z"), true)
        ).forEach(alertStore::add);

        return ResponseEntity.ok(Map.of(
                "seededReadings", 10,
                "seededAlerts",   4,
                "note", "Stores populated. Use GET /v1/dashboard/live and GET /v1/alerts to verify."
        ));
    }

    /**
     * Clears both stores — useful for resetting between test runs.
     */
    @DeleteMapping("/seed")
    public ResponseEntity<Map<String, String>> clear() {
        // Dismiss all alerts
        alertStore.getAll(DEMO_TENANT).forEach(a -> alertStore.dismiss(DEMO_TENANT, a.getId()));
        // Sensor readings don't have a bulk-clear yet; replace each with a zeroed entry
        List.of(
                "soil_moisture", "air_temperature", "air_humidity", "soil_temp",
                "soil_ec", "soil_ph", "soil_nitrogen", "soil_phosphorus",
                "soil_potassium", "soil_salinity"
        ).forEach(key -> sensorReadingStore.update(reading(key, 0.0, "", "OK")));

        return ResponseEntity.ok(Map.of("note", "Stores cleared."));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static SensorReadingResponse reading(String key, double value,
                                                  String unit, String status) {
        return SensorReadingResponse.builder()
                .sensorKey(key)
                .tenantId(DEMO_TENANT)
                .greenhouseId(DEMO_GREENHOUSE)
                .zoneId(DEMO_GREENHOUSE)
                .deviceId(DEMO_GREENHOUSE)
                .value(value)
                .unit(unit)
                .status(status)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    private static AlertResponse alert(String id, String severity, String sensorKey,
                                       String message, Instant triggeredAt,
                                       boolean acknowledged) {
        return AlertResponse.builder()
                .id(id)
                .tenantId(DEMO_TENANT)
                .greenhouseId(DEMO_GREENHOUSE)
                .severity(severity)
                .sensorKey(sensorKey)
                .message(message)
                .triggeredAt(triggeredAt)
                .acknowledged(acknowledged)
                .build();
    }
}
