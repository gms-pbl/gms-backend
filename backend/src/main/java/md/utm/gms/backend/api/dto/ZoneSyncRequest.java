package md.utm.gms.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ZoneSyncRequest {

    @NotBlank
    @JsonProperty("tenant_id")
    private String tenantId;

    @NotBlank
    @JsonProperty("greenhouse_id")
    private String greenhouseId;

    @JsonProperty("gateway_id")
    private String gatewayId;

    public String gatewayIdOrDefault(String fallbackGreenhouseId) {
        if (gatewayId == null || gatewayId.isBlank()) {
            return fallbackGreenhouseId;
        }
        return gatewayId;
    }
}
