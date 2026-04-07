package md.utm.gms.backend.mqtt.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Deserialisation target for the MQTT alert payload.
 *
 * <p>Wire format (JSON):
 * <pre>
 * {
 *   "alert_id":   "uuid (optional — generated server-side if absent)",
 *   "sensor_key": "soil_moisture | air_temperature | ...",
 *   "severity":   "CRITICAL | WARNING | INFO",
 *   "message":    "Human-readable description of the alert condition",
 *   "timestamp":  "2024-06-01T12:00:00.000Z"
 * }
 * </pre>
 *
 * <p>Published by the edge controller on topic:
 * {@code gms/{site_id}/{greenhouse_id}/{zone_id}/alert}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertPayload {

    @JsonProperty("alert_id")
    private String alertId;

    @JsonProperty("sensor_key")
    private String sensorKey;

    private String severity;

    private String message;

    private Instant timestamp;
}
