package md.utm.gms.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class ZoneAssignRequest {

    @NotBlank
    @JsonProperty("device_id")
    private String deviceId;

    @JsonProperty("zone_id")
    private String zoneId;

    @JsonProperty("zone_name")
    private String zoneName;

    private Map<String, Object> metadata;
}
