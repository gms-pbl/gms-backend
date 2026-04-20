package md.utm.gms.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record GreenhouseGatewayConfigResponse(
        @JsonProperty("tenant_id") String tenantId,
        @JsonProperty("greenhouse_id") String greenhouseId,
        @JsonProperty("gateway_id") String gatewayId,
        Map<String, String> env
) {
}
