package md.utm.gms.backend.mqtt.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Deserialisation target for the MQTT gateway status payload.
 *
 * <p>Published by the Mini PC gateway on topic:
 * {@code gms/{tenant_id}/{greenhouse_id}/uplink/status}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayStatusPayload {

    @JsonProperty("event_id")
    private String eventId;

    @JsonProperty("tenant_id")
    private String tenantId;

    @JsonProperty("greenhouse_id")
    private String greenhouseId;

    @JsonProperty("gateway_id")
    private String gatewayId;

    /** Gateway connectivity state: {@code ONLINE}, {@code OFFLINE}, {@code DEGRADED}. */
    private String status;

    @JsonProperty("firmware_version")
    private String firmwareVersion;

    private Instant timestamp;
}
