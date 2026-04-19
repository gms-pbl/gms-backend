package md.utm.gms.backend.mqtt.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import md.utm.gms.backend.api.dto.AlertResponse;
import md.utm.gms.backend.mqtt.dto.AlertPayload;
import md.utm.gms.backend.store.AlertStore;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Processes inbound alert events received from edge devices over MQTT.
 *
 * <p>Current behaviour: deserialises the payload and persists it through
 * {@link AlertStore} so it is immediately visible in the dashboard.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertHandler {

    private final ObjectMapper objectMapper;
    private final AlertStore alertStore;

    @ServiceActivator(inputChannel = "alertChannel")
    public void handle(Message<String> message) {
        try {
            AlertPayload payload = objectMapper.readValue(
                    message.getPayload(), AlertPayload.class);

            String id = payload.getAlertId() != null
                    ? payload.getAlertId()
                    : UUID.randomUUID().toString();

            alertStore.add(AlertResponse.builder()
                    .id(id)
                    .severity(payload.getSeverity())
                    .sensorKey(payload.getSensorKey())
                    .message(payload.getMessage())
                    .triggeredAt(payload.getTimestamp() != null ? payload.getTimestamp() : Instant.now())
                    .acknowledged(false)
                    .build());

            log.warn("Alert  severity={}  sensorKey={}  message={}",
                    payload.getSeverity(), payload.getSensorKey(), payload.getMessage());

        } catch (Exception e) {
            log.error("Failed to process alert  payload='{}' error='{}'",
                    message.getPayload(), e.getMessage(), e);
        }
    }
}
