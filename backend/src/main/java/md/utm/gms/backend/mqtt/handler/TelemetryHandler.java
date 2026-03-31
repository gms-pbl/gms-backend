package md.utm.gms.backend.mqtt.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import md.utm.gms.backend.mqtt.dto.TelemetryPayload;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Processes inbound telemetry messages received from edge devices over MQTT.
 *
 * <p>Next steps (separate work-packages):
 * <ul>
 *   <li>Persist to InfluxDB ({@code sensor_reading} measurement).
 *   <li>Evaluate threshold rules and emit alert events.
 *   <li>Forward to WebSocket broadcast channel for dashboard real-time feed.
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryHandler {

    private final ObjectMapper objectMapper;

    @ServiceActivator(inputChannel = "telemetryChannel")
    public void handle(Message<String> message) {
        try {
            TelemetryPayload payload = objectMapper.readValue(
                    message.getPayload(), TelemetryPayload.class);

            log.info("Telemetry  sensorId={}  parameter={}  value={} {}  quality={}  ts={}",
                    payload.getSensorId(),
                    payload.getParameter(),
                    payload.getValue(),
                    payload.getUnit(),
                    payload.getQuality(),
                    payload.getTimestamp());

            // TODO (DF-1): persist to InfluxDB — measurement: sensor_reading
            //              tags:   site_id, greenhouse_id, zone_id, sensor_id, parameter
            //              fields: value (float), unit (string), quality (string)

        } catch (Exception e) {
            log.error("Failed to process telemetry message: payload='{}' error='{}'",
                    message.getPayload(), e.getMessage(), e);
        }
    }
}
