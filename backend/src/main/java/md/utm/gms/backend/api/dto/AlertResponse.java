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
 *   "sensor_key":   "soil_moisture",
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

    private String severity;

    @JsonProperty("sensor_key")
    private String sensorKey;

    private String message;

    @JsonProperty("triggered_at")
    private Instant triggeredAt;

    private boolean acknowledged;
}
