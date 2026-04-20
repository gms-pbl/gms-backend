package md.utm.gms.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GreenhouseUpdateRequest(
        String name,
        @JsonProperty("gateway_id") String gatewayId
) {

    public boolean hasUpdates() {
        return (name != null && !name.isBlank()) || (gatewayId != null && !gatewayId.isBlank());
    }
}
