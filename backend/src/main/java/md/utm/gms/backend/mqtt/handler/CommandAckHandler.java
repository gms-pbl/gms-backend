package md.utm.gms.backend.mqtt.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import md.utm.gms.backend.mqtt.dto.CommandAckPayload;
import md.utm.gms.backend.store.CommandAckStore;
import md.utm.gms.backend.store.ThresholdApplyStatusStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class CommandAckHandler {

    private static final Logger log = LoggerFactory.getLogger(CommandAckHandler.class);

    private final ObjectMapper objectMapper;
    private final CommandAckStore commandAckStore;
    private final ThresholdApplyStatusStore thresholdApplyStatusStore;

    public CommandAckHandler(ObjectMapper objectMapper,
                             CommandAckStore commandAckStore,
                             ThresholdApplyStatusStore thresholdApplyStatusStore) {
        this.objectMapper = objectMapper;
        this.commandAckStore = commandAckStore;
        this.thresholdApplyStatusStore = thresholdApplyStatusStore;
    }

    @ServiceActivator(inputChannel = "commandAckChannel")
    public void handle(Message<String> message) {
        try {
            CommandAckPayload payload = objectMapper.readValue(message.getPayload(), CommandAckPayload.class);

            if (isBlank(payload.getCommandId())) {
                log.warn("Command ACK missing command_id payload='{}'", message.getPayload());
                return;
            }

            commandAckStore.update(payload);
            thresholdApplyStatusStore.updateFromAck(payload);
            log.info("Command ACK command_id={} status={} device={} zone={} reason={} ts={}",
                    payload.getCommandId(),
                    payload.getStatus(),
                    payload.getDeviceId(),
                    payload.getZoneId(),
                    payload.getReason(),
                    payload.getTimestamp());
        } catch (Exception e) {
            log.error("Failed to process command_ack payload='{}' error='{}'",
                    message.getPayload(),
                    e.getMessage());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
