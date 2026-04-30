package md.utm.gms.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThresholdApplyStatusResponse {

    @JsonProperty("tenant_id")
    private String tenantId;

    @JsonProperty("greenhouse_id")
    private String greenhouseId;

    @JsonProperty("zone_id")
    private String zoneId;

    @JsonProperty("gateway_id")
    private String gatewayId;

    @JsonProperty("config_version")
    private long configVersion;

    @JsonProperty("command_id")
    private String commandId;

    private String status;

    private String reason;

    @JsonProperty("ack_timestamp")
    private Instant ackTimestamp;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
