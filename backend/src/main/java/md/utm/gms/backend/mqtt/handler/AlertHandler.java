package md.utm.gms.backend.mqtt.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Processes inbound alert events received from edge devices over MQTT.
 *
 * <p>Alert events are published by the Portenta edge controller when a sensor
 * value breaches a configured threshold.
 *
 * <p>Next steps (separate work-packages):
 * <ul>
 *   <li>Deserialise alert payload and evaluate severity (informational vs critical).
 *   <li>Persist to InfluxDB ({@code alert_event} measurement).
 *   <li>Push informational alerts to the dashboard WebSocket within 5 s.
 *   <li>Dispatch FCM / Twilio notifications for critical alerts persisting &gt; 60 s.
 * </ul>
 */
@Slf4j
@Component
public class AlertHandler {

    @ServiceActivator(inputChannel = "alertChannel")
    public void handle(Message<String> message) {
        log.warn("Alert received  payload='{}'", message.getPayload());
        // TODO (DF-5): parse alert payload, evaluate severity, trigger notification pipeline
    }
}
