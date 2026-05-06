package md.utm.gms.backend.mqtt.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import md.utm.gms.backend.mqtt.dto.GatewayStatusPayload;
import md.utm.gms.backend.store.GatewayStatusStore;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Processes inbound gateway status messages received over MQTT.
 *
 * <p>Status messages report the connectivity state, firmware version, and
 * last-seen timestamp of each Mini PC gateway. On each received heartbeat the
 * latest state is upserted into {@code gms.gateway_status} so operators can
 * detect connectivity loss from the dashboard.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatusHandler {

    private final ObjectMapper objectMapper;
    private final GatewayStatusStore gatewayStatusStore;

    @ServiceActivator(inputChannel = "statusChannel")
    public void handle(Message<String> message) {
        try {
            GatewayStatusPayload payload = objectMapper.readValue(
                    message.getPayload(), GatewayStatusPayload.class);

            TopicScope scope = TopicScope.from(
                    message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC, String.class));

            String tenantId     = firstNonBlank(payload.getTenantId(),     scope.tenantId);
            String greenhouseId = firstNonBlank(payload.getGreenhouseId(), scope.greenhouseId);
            String gatewayId    = firstNonBlank(payload.getGatewayId(),    greenhouseId);

            gatewayStatusStore.upsert(
                    tenantId,
                    greenhouseId,
                    gatewayId,
                    payload.getStatus(),
                    payload.getFirmwareVersion(),
                    payload.getTimestamp());

            log.info("Gateway status  tenant={}  greenhouse={}  gateway={}  status={}  firmware={}",
                    tenantId, greenhouseId, gatewayId,
                    payload.getStatus(), payload.getFirmwareVersion());

        } catch (Exception e) {
            log.error("Failed to process gateway status  payload='{}' error='{}'",
                    message.getPayload(), e.getMessage(), e);
        }
    }

    private static final class TopicScope {
        private final String tenantId;
        private final String greenhouseId;

        private TopicScope(String tenantId, String greenhouseId) {
            this.tenantId = tenantId;
            this.greenhouseId = greenhouseId;
        }

        static TopicScope from(String topic) {
            if (topic == null || topic.isBlank()) {
                return new TopicScope("tenant-demo", "greenhouse-demo");
            }
            // gms/{tenant}/{greenhouse}/uplink/status
            String[] segments = topic.split("/");
            if (segments.length >= 3 && "gms".equals(segments[0])) {
                return new TopicScope(segments[1], segments[2]);
            }
            return new TopicScope("tenant-demo", "greenhouse-demo");
        }
    }

    private static String firstNonBlank(String primary, String fallback) {
        return (primary != null && !primary.isBlank()) ? primary.trim() : fallback;
    }
}
