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
 *   "sensor_key": "soil_moist | air_temp | ...",
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

    @JsonProperty("gateway_id")
    private String gatewayId;

    @JsonProperty("zone_id")
    private String zoneId;

    @JsonProperty("device_id")
    private String deviceId;

    private String source;

    @JsonProperty("threshold_version")
    private Long thresholdVersion;

    @JsonProperty("current_value")
    private Double currentValue;

    @JsonProperty("threshold_min")
    private Double thresholdMin;

    @JsonProperty("threshold_max")
    private Double thresholdMax;

    private String severity;

    private String message;

    private Instant timestamp;
}
