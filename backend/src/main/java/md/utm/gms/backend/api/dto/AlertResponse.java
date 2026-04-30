package md.utm.gms.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * REST response body for a single alert.
 *
 * <p>Field names match the shape the React dashboard consumes:
 * <pre>
 * {
 *   "id":           "a1",
 *   "severity":     "CRITICAL",
 *   "sensor_key":   "soil_moist",
 *   "message":      "Soil moisture critically low: 12% (min 30%)",
 *   "triggered_at": "2026-04-06T08:14:00Z",
 *   "acknowledged": false
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertResponse {

    private String id;

    @JsonProperty("tenant_id")
    private String tenantId;

    @JsonProperty("greenhouse_id")
    private String greenhouseId;

    @JsonProperty("gateway_id")
    private String gatewayId;

    @JsonProperty("zone_id")
    private String zoneId;

    @JsonProperty("device_id")
    private String deviceId;

    private String severity;

    @JsonProperty("sensor_key")
    private String sensorKey;

    private String message;

    private String source;

    @JsonProperty("threshold_version")
    private Long thresholdVersion;

    @JsonProperty("current_value")
    private Double currentValue;

    @JsonProperty("threshold_min")
    private Double thresholdMin;

    @JsonProperty("threshold_max")
    private Double thresholdMax;

    @JsonProperty("triggered_at")
    private Instant triggeredAt;

    private boolean acknowledged;
}
