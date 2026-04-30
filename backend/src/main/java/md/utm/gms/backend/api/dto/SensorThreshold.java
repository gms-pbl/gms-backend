package md.utm.gms.backend.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorThreshold {
    private ThresholdBounds normal;
    private ThresholdBounds warn;
    private ThresholdBounds critical;
}
