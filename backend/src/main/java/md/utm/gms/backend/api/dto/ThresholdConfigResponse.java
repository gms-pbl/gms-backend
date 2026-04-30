package md.utm.gms.backend.api.dto;

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
public class ThresholdConfigResponse {

    @JsonProperty("tenant_id")
    private String tenantId;

    @JsonProperty("greenhouse_id")
    private String greenhouseId;

    @JsonProperty("zone_id")
    private String zoneId;

    @JsonProperty("config_version")
    private long configVersion;

    @JsonProperty("command_id")
    private String commandId;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    private Map<String, SensorThreshold> thresholds;

    @JsonProperty("apply_status")
    private ThresholdApplyStatusResponse applyStatus;
}
