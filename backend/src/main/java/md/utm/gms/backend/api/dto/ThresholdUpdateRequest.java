package md.utm.gms.backend.api.dto;

import java.util.Map;

public record ThresholdUpdateRequest(
        Map<String, SensorThreshold> thresholds
) {
}
