package md.utm.gms.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ZoneSyncRequest {

    @JsonProperty("gateway_id")
    private String gatewayId;

    public String gatewayIdOrDefault(String fallbackGreenhouseId) {
        if (gatewayId == null || gatewayId.isBlank()) {
            return fallbackGreenhouseId;
        }
        return gatewayId;
    }
}
