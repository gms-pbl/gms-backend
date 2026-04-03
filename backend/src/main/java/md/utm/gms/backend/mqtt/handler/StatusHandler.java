package md.utm.gms.backend.mqtt.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Processes inbound device status messages received from edge devices over MQTT.
 *
 * <p>Status messages report the connectivity state, firmware version, and
 * last-seen timestamp of each Mini PC gateway.
 *
 * <p>Next steps (separate work-packages):
 * <ul>
 *   <li>Deserialise status payload.
 *   <li>Update device registry / last-seen timestamps in PostgreSQL.
 *   <li>Trigger connectivity-loss alert if a heartbeat is missed.
 * </ul>
 */
@Slf4j
@Component
public class StatusHandler {

    @ServiceActivator(inputChannel = "statusChannel")
    public void handle(Message<String> message) {
        log.info("Status received  payload='{}'", message.getPayload());
        // TODO: parse status, update device registry / last-seen timestamps in PostgreSQL
    }
}
