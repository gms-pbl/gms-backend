package md.utm.gms.backend.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A single aggregated or raw data point in a sensor history series.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorHistoryPoint {

    private Instant timestamp;
    private double value;
}
