package md.utm.gms.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ZoneUnassignRequest {

    @NotBlank
    @JsonProperty("device_id")
    private String deviceId;
}
