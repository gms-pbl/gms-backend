package md.utm.gms.backend.mqtt.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayTelemetryPayload {

    @JsonProperty("event_id")
    private String eventId;

    @JsonProperty("tenant_id")
    private String tenantId;

    @JsonProperty("greenhouse_id")
    private String greenhouseId;

    @JsonProperty("gateway_id")
    private String gatewayId;

    @JsonProperty("zone_id")
    private String zoneId;

    @JsonProperty("zone_name")
    private String zoneName;

    @JsonProperty("device_id")
    private String deviceId;

    private String kind;

    private Instant timestamp;

    private Map<String, Double> metrics;
}
