package md.utm.gms.backend.mqtt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;

/**
 * Routes inbound MQTT messages to the appropriate downstream channel based on
 * the last path segment of the MQTT topic.
 *
 * <p>Expected topic pattern:
 * <pre>gms/{site_id}/{greenhouse_id}/{zone_id}/{type}</pre>
 *
 * <p>Where {@code {type}} is one of:
 * {@code telemetry}, {@code alert}, {@code status}, {@code config}, {@code command}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TopicDispatcher {

    private final MessageChannel telemetryChannel;
    private final MessageChannel alertChannel;
    private final MessageChannel statusChannel;
    private final MessageChannel configChannel;

    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public void dispatch(Message<String> message) {
        String topic = message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC, String.class);
        if (topic == null) {
            log.warn("MQTT message received without a topic header — discarding");
            return;
        }

        String type = extractType(topic);
        log.debug("MQTT dispatch  topic={}  type={}", topic, type);

        switch (type) {
            case "telemetry" -> telemetryChannel.send(message);
            case "alert"     -> alertChannel.send(message);
            case "status"    -> statusChannel.send(message);
            case "config"    -> configChannel.send(message);
            case "command"   ->
                // The backend publishes commands outbound; receiving one here means
                // the broker echoed it back. Log and discard.
                log.debug("Received echo of outbound command on topic {} — ignoring", topic);
            default -> log.warn("Unknown MQTT message type '{}' on topic {} — discarding", type, topic);
        }
    }

    /**
     * Returns the last path segment of an MQTT topic string.
     * e.g. {@code "gms/site1/gh1/zone1/telemetry"} → {@code "telemetry"}
     */
    private static String extractType(String topic) {
        int lastSlash = topic.lastIndexOf('/');
        return lastSlash >= 0 ? topic.substring(lastSlash + 1) : topic;
    }
}
