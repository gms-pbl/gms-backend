package md.utm.gms.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record GreenhouseCreateRequest(
        @NotBlank String name,
        @JsonProperty("greenhouse_id") String greenhouseId,
        @JsonProperty("gateway_id") String gatewayId,
        Double latitude,
        Double longitude
) {
}
