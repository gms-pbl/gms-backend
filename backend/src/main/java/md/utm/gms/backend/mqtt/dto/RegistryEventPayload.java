package md.utm.gms.backend.mqtt.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistryEventPayload {

    @JsonProperty("event_id")
    private String eventId;

    @JsonProperty("command_id")
    private String commandId;

    private String type;

    @JsonProperty("tenant_id")
    private String tenantId;

    @JsonProperty("greenhouse_id")
    private String greenhouseId;

    @JsonProperty("device_id")
    private String deviceId;

    @JsonProperty("zone_id")
    private String zoneId;

    @JsonProperty("zone_name")
    private String zoneName;

    @JsonProperty("firmware_version")
    private String firmwareVersion;

    private Instant timestamp;

    private Map<String, Object> metadata;
}
