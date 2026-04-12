package md.utm.gms.backend.zones;

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
public class ZoneDeviceRecord {

    private String tenantId;
    private String greenhouseId;
    private String deviceId;

    private String zoneId;
    private String zoneName;

    private String firmwareVersion;
    private String status;

    private Instant lastSeenAt;
    private Instant updatedAt;

    private Map<String, Object> metadata;

    public boolean isAssigned() {
        return zoneId != null && !zoneId.isBlank();
    }
}
