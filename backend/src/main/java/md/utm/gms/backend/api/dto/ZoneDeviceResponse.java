package md.utm.gms.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import md.utm.gms.backend.zones.ZoneDeviceRecord;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneDeviceResponse {

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

    private String status;

    @JsonProperty("firmware_version")
    private String firmwareVersion;

    @JsonProperty("last_seen_at")
    private Instant lastSeenAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    private Map<String, Object> metadata;

    public static ZoneDeviceResponse from(ZoneDeviceRecord record) {
        return ZoneDeviceResponse.builder()
                .tenantId(record.getTenantId())
                .greenhouseId(record.getGreenhouseId())
                .deviceId(record.getDeviceId())
                .zoneId(record.getZoneId())
                .zoneName(record.getZoneName())
                .status(record.getStatus())
                .firmwareVersion(record.getFirmwareVersion())
                .lastSeenAt(record.getLastSeenAt())
                .updatedAt(record.getUpdatedAt())
                .metadata(record.getMetadata())
                .build();
    }
}
