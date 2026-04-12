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
 * <pre>gms/{tenant_id}/{greenhouse_id}/uplink/{type}</pre>
 *
 * <p>Where {@code {type}} is one of:
 * {@code telemetry}, {@code alert}, {@code status}, {@code registry}, {@code command_ack}.
 *
 * <p>The dispatcher also supports legacy topics in the form
 * {@code gms/{site_id}/{greenhouse_id}/{zone_id}/{type}} for backwards compatibility.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TopicDispatcher {

    private final MessageChannel telemetryChannel;
    private final MessageChannel alertChannel;
    private final MessageChannel statusChannel;
    private final MessageChannel configChannel;
    private final MessageChannel registryChannel;
    private final MessageChannel commandAckChannel;

    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public void dispatch(Message<String> message) {
        String topic = message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC, String.class);
        if (topic == null) {
            log.warn("MQTT message received without a topic header — discarding");
            return;
        }

        Route route = resolveRoute(topic);
        log.debug("MQTT dispatch topic={} route={}", topic, route);

        switch (route) {
            case TELEMETRY -> telemetryChannel.send(message);
            case ALERT -> alertChannel.send(message);
            case STATUS -> statusChannel.send(message);
            case CONFIG -> configChannel.send(message);
            case REGISTRY -> registryChannel.send(message);
            case COMMAND_ACK -> commandAckChannel.send(message);
            case IGNORE -> log.debug("Ignoring MQTT topic {}", topic);
            case UNKNOWN -> log.warn("Unknown MQTT route on topic {} — discarding", topic);
        }
    }

    private static Route resolveRoute(String topic) {
        String[] segments = topic.split("/");

        // New contract: gms/{tenant}/{greenhouse}/uplink/{stream}
        if (segments.length >= 5 && "gms".equals(segments[0]) && "uplink".equals(segments[3])) {
            return mapType(segments[4]);
        }

        // Ignore own downlink command echoes
        if (segments.length >= 5 && "gms".equals(segments[0]) && "downlink".equals(segments[3])) {
            return Route.IGNORE;
        }

        // Legacy fallback: gms/{site}/{greenhouse}/{zone}/{type}
        if (segments.length >= 5 && "gms".equals(segments[0])) {
            return mapType(segments[segments.length - 1]);
        }

        // Legacy edge-engine passthrough path (cloud/...) still accepted during migration
        if (topic.startsWith("cloud/")) {
            return mapType(extractType(topic));
        }

        return Route.UNKNOWN;
    }

    private static Route mapType(String type) {
        if (type == null) {
            return Route.UNKNOWN;
        }
        return switch (type) {
            case "telemetry" -> Route.TELEMETRY;
            case "alert" -> Route.ALERT;
            case "status" -> Route.STATUS;
            case "config" -> Route.CONFIG;
            case "registry" -> Route.REGISTRY;
            case "command_ack" -> Route.COMMAND_ACK;
            case "command" -> Route.IGNORE;
            default -> Route.UNKNOWN;
        };
    }

    private static String extractType(String topic) {
        int lastSlash = topic.lastIndexOf('/');
        return lastSlash >= 0 ? topic.substring(lastSlash + 1) : topic;
    }

    private enum Route {
        TELEMETRY,
        ALERT,
        STATUS,
        CONFIG,
        REGISTRY,
        COMMAND_ACK,
        IGNORE,
        UNKNOWN
    }
}
