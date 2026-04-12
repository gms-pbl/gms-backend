package md.utm.gms.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class ZoneAssignRequest {

    @NotBlank
    @JsonProperty("tenant_id")
    private String tenantId;

    @NotBlank
    @JsonProperty("greenhouse_id")
    private String greenhouseId;

    @NotBlank
    @JsonProperty("device_id")
    private String deviceId;

    @JsonProperty("zone_id")
    private String zoneId;

    @JsonProperty("zone_name")
    private String zoneName;

    private Map<String, Object> metadata;
}
