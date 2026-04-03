package md.utm.gms.backend.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Service;

/**
 * Publishes actuator commands to IoT edge devices via MQTT.
 *
 * <p>The caller supplies the full topic in the pattern
 * {@code gms/{site_id}/{greenhouse_id}/{zone_id}/command}
 * and any serialisable command object; this service serialises it to JSON and
 * places it on the outbound channel for delivery by
 * {@link md.utm.gms.backend.config.MqttIntegrationConfig#mqttOutboundHandler()}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommandService {

    private final MessageChannel mqttOutboundChannel;
    private final ObjectMapper objectMapper;

    /**
     * Serialises {@code payload} to JSON and publishes it to {@code topic}.
     *
     * @param topic   full MQTT topic, e.g. {@code gms/site1/gh1/zone1/command}
     * @param payload any serialisable command object
     * @throws IllegalArgumentException if serialisation fails
     */
    public void sendCommand(String topic, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            mqttOutboundChannel.send(
                    MessageBuilder.withPayload(json)
                            .setHeader(MqttHeaders.TOPIC, topic)
                            .build());
            log.debug("Command published  topic={}  payload={}", topic, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialise command for topic {}: {}", topic, e.getMessage(), e);
            throw new IllegalArgumentException("Command serialisation failed", e);
        }
    }
}
