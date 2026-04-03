package md.utm.gms.backend.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import md.utm.gms.backend.mqtt.dto.TelemetryPayload;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryPayloadDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void deserializesFullTelemetryPayload() throws Exception {
        String json = """
                {
                  "sensor_id":  "soil-01",
                  "parameter":  "soil_moisture",
                  "value":      42.5,
                  "unit":       "%",
                  "quality":    "VALID",
                  "timestamp":  "2024-06-01T12:00:00Z"
                }
                """;

        TelemetryPayload payload = objectMapper.readValue(json, TelemetryPayload.class);

        assertThat(payload.getSensorId()).isEqualTo("soil-01");
        assertThat(payload.getParameter()).isEqualTo("soil_moisture");
        assertThat(payload.getValue()).isEqualTo(42.5);
        assertThat(payload.getUnit()).isEqualTo("%");
        assertThat(payload.getQuality()).isEqualTo(TelemetryPayload.Quality.VALID);
        assertThat(payload.getTimestamp()).isEqualTo(Instant.parse("2024-06-01T12:00:00Z"));
    }

    @Test
    void deserializesStaleQuality() throws Exception {
        String json = """
                {
                  "sensor_id": "co2-01",
                  "parameter": "co2_ppm",
                  "value":     850.0,
                  "unit":      "ppm",
                  "quality":   "STALE",
                  "timestamp": "2024-06-01T12:01:00Z"
                }
                """;

        TelemetryPayload payload = objectMapper.readValue(json, TelemetryPayload.class);
        assertThat(payload.getQuality()).isEqualTo(TelemetryPayload.Quality.STALE);
    }
}
