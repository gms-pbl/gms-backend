package md.utm.gms.backend.mqtt.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Deserialisation target for the MQTT telemetry payload.
 *
 * <p>Wire format (JSON):
 * <pre>
 * {
 *   "sensor_id":  "string",
 *   "parameter":  "soil_moisture | air_temperature | air_humidity | co2_ppm | ...",
 *   "value":       3.14,
 *   "unit":       "string",
 *   "quality":    "VALID | STALE | INTERPOLATED",
 *   "timestamp":  "2024-06-01T12:00:00.000Z"
 * }
 * </pre>
 *
 * <p>Published by the Mini PC edge gateway on topic:
 * {@code gms/{site_id}/{greenhouse_id}/{zone_id}/telemetry}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryPayload {

    @JsonProperty("sensor_id")
    private String sensorId;

    private String parameter;

    private double value;

    private String unit;

    private Quality quality;

    /** ISO 8601 timestamp. Requires {@code jackson-datatype-jsr310} on classpath. */
    private Instant timestamp;

    public enum Quality {
        VALID,
        STALE,
        INTERPOLATED
    }
}
