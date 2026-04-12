package md.utm.gms.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import md.utm.gms.backend.mqtt.dto.TelemetryPayload;

import java.time.Instant;

/**
 * REST response body for a single sensor reading.
 *
 * <p>Field names match the shape the React dashboard consumes:
 * <pre>
 * {
 *   "sensor_key":    "soil_moisture",
 *   "value":         62.4,
 *   "unit":          "%",
 *   "status":        "OK",
 *   "lastUpdatedAt": "2026-04-07T10:00:00Z"
 * }
 * </pre>
 *
 * <p>{@code status} is derived from the MQTT {@link TelemetryPayload.Quality} field:
 * {@code VALID} → {@code OK}, {@code STALE} / {@code INTERPOLATED} → {@code WARN}.
 * Full threshold-based evaluation (WARN / ERR) will be added in the threshold work-package.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorReadingResponse {

    @JsonProperty("sensor_key")
    private String sensorKey;

    @JsonProperty("greenhouse_id")
    private String greenhouseId;

    @JsonProperty("zone_id")
    private String zoneId;

    @JsonProperty("device_id")
    private String deviceId;

    private double value;

    private String unit;

    private String status;

    private Instant lastUpdatedAt;

    public static SensorReadingResponse from(TelemetryPayload payload) {
        return SensorReadingResponse.builder()
                .sensorKey(payload.getParameter())
                .value(payload.getValue())
                .unit(payload.getUnit())
                .status(qualityToStatus(payload.getQuality()))
                .lastUpdatedAt(payload.getTimestamp() != null ? payload.getTimestamp() : Instant.now())
                .build();
    }

    private static String qualityToStatus(TelemetryPayload.Quality quality) {
        if (quality == null) return "OK";
        return switch (quality) {
            case VALID -> "OK";
            case STALE, INTERPOLATED -> "WARN";
        };
    }
}
