package md.utm.gms.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record GreenhouseResponse(
        @JsonProperty("tenant_id") String tenantId,
        @JsonProperty("greenhouse_id") String greenhouseId,
        @JsonProperty("gateway_id") String gatewayId,
        String name,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt
) {
}
