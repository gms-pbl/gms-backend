package md.utm.gms.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ZoneUnassignRequest {

    @NotBlank
    @JsonProperty("tenant_id")
    private String tenantId;

    @NotBlank
    @JsonProperty("greenhouse_id")
    private String greenhouseId;

    @NotBlank
    @JsonProperty("device_id")
    private String deviceId;
}
