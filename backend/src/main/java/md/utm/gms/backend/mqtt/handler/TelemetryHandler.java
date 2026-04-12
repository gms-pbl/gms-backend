package md.utm.gms.backend.mqtt.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import md.utm.gms.backend.api.dto.SensorReadingResponse;
import md.utm.gms.backend.mqtt.dto.GatewayTelemetryPayload;
import md.utm.gms.backend.mqtt.dto.TelemetryPayload;
import md.utm.gms.backend.store.SensorReadingStore;
import md.utm.gms.backend.zones.ZoneRegistryStore;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Processes inbound telemetry messages received from edge devices over MQTT.
 *
 * <p>Current behaviour: deserialises the payload and stores the latest reading
 * per sensor key in {@link SensorReadingStore} for REST polling by the dashboard.
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
    private final SensorReadingStore sensorReadingStore;
    private final ZoneRegistryStore zoneRegistryStore;

    @ServiceActivator(inputChannel = "telemetryChannel")
    public void handle(Message<String> message) {
        try {
            if (isGatewayTelemetry(message.getPayload())) {
                handleGatewayTelemetry(message.getPayload());
            } else {
                handleLegacyTelemetry(message.getPayload());
            }

            // TODO (DF-1): persist to InfluxDB — measurement: sensor_reading
            //              tags:   site_id, greenhouse_id, zone_id, sensor_id, parameter
            //              fields: value (float), unit (string), quality (string)

        } catch (Exception e) {
            log.error("Failed to process telemetry  payload='{}' error='{}'",
                    message.getPayload(), e.getMessage(), e);
        }
    }

    private void handleLegacyTelemetry(String payloadJson) throws Exception {
        TelemetryPayload payload = objectMapper.readValue(payloadJson, TelemetryPayload.class);
        sensorReadingStore.update(SensorReadingResponse.from(payload));

        log.info("Legacy telemetry sensorKey={} value={} {} quality={} ts={}",
                payload.getParameter(),
                payload.getValue(),
                payload.getUnit(),
                payload.getQuality(),
                payload.getTimestamp());
    }

    private void handleGatewayTelemetry(String payloadJson) throws Exception {
        GatewayTelemetryPayload payload = objectMapper.readValue(payloadJson, GatewayTelemetryPayload.class);

        if (isBlank(payload.getTenantId()) || isBlank(payload.getGreenhouseId()) || isBlank(payload.getDeviceId())) {
            log.warn("Gateway telemetry missing tenant/greenhouse/device fields: {}", payloadJson);
            return;
        }

        if (zoneRegistryStore.isSuppressed(
                payload.getTenantId(),
                payload.getGreenhouseId(),
                payload.getDeviceId(),
                payload.getTimestamp())) {
            log.debug("Ignoring stale telemetry greenhouse={} device={} ts={}",
                    payload.getGreenhouseId(),
                    payload.getDeviceId(),
                    payload.getTimestamp());
            return;
        }

        zoneRegistryStore.touchFromTelemetry(
                payload.getTenantId(),
                payload.getGreenhouseId(),
                payload.getDeviceId(),
                payload.getZoneId(),
                payload.getZoneName(),
                payload.getTimestamp());

        if (payload.getMetrics() == null || payload.getMetrics().isEmpty()) {
            return;
        }

        Instant now = payload.getTimestamp() != null ? payload.getTimestamp() : Instant.now();
        for (Map.Entry<String, Double> metric : payload.getMetrics().entrySet()) {
            if (metric.getValue() == null) {
                continue;
            }

            SensorReadingResponse reading = SensorReadingResponse.builder()
                    .sensorKey(metric.getKey())
                    .greenhouseId(payload.getGreenhouseId())
                    .zoneId(payload.getZoneId())
                    .deviceId(payload.getDeviceId())
                    .value(metric.getValue())
                    .unit("raw")
                    .status("OK")
                    .lastUpdatedAt(now)
                    .build();

            sensorReadingStore.update(reading);
        }

        log.info("Gateway telemetry greenhouse={} zone={} device={} kind={} metrics={}",
                payload.getGreenhouseId(),
                payload.getZoneId(),
                payload.getDeviceId(),
                payload.getKind(),
                payload.getMetrics().size());
    }

    private static boolean isGatewayTelemetry(String payloadJson) {
        return payloadJson.contains("\"metrics\"")
                && payloadJson.contains("\"greenhouse_id\"")
                && payloadJson.contains("\"device_id\"");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
