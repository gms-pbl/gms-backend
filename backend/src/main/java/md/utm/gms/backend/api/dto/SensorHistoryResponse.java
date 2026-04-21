package md.utm.gms.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * REST response for {@code GET /v1/dashboard/history}.
 *
 * <pre>
 * {
 *   "sensor_key":    "soil_moist",
 *   "greenhouse_id": "gh-001",
 *   "zone_id":       "zone-a",
 *   "device_id":     null,
 *   "unit":          "%",
 *   "granularity":   "hourly",
 *   "from":          "2026-04-20T00:00:00Z",
 *   "to":            "2026-04-21T00:00:00Z",
 *   "points": [
 *     { "timestamp": "2026-04-20T00:00:00Z", "value": 45.2 },
 *     { "timestamp": "2026-04-20T01:00:00Z", "value": 44.8 }
 *   ]
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorHistoryResponse {

    @JsonProperty("sensor_key")
    private String sensorKey;

    @JsonProperty("greenhouse_id")
    private String greenhouseId;

    @JsonProperty("zone_id")
    private String zoneId;

    @JsonProperty("device_id")
    private String deviceId;

    private String unit;
    private String granularity;
    private Instant from;
    private Instant to;
    private List<SensorHistoryPoint> points;
}
