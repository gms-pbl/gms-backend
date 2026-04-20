package md.utm.gms.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class ZoneCommandRequest {

    @JsonProperty("zone_id")
    private String zoneId;

    @JsonProperty("device_id")
    private String deviceId;

    @NotBlank
    private String action;

    private Map<String, Object> payload;
}
