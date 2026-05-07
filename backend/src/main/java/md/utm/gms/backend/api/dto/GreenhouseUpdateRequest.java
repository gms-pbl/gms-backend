package md.utm.gms.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GreenhouseUpdateRequest(
        String name,
        @JsonProperty("gateway_id") String gatewayId,
        Double latitude,
        Double longitude,
        String address,
        String description
) {

    public boolean hasUpdates() {
        return (name != null && !name.isBlank())
                || (gatewayId != null && !gatewayId.isBlank())
                || latitude != null
                || longitude != null
                || (address != null && !address.isBlank())
                || (description != null && !description.isBlank());
    }
}
