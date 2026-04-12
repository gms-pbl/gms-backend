package md.utm.gms.backend.store;

import md.utm.gms.backend.mqtt.dto.CommandAckPayload;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CommandAckStore {

    private final ConcurrentHashMap<String, CommandAckPayload> commandAcks = new ConcurrentHashMap<>();

    public void update(CommandAckPayload payload) {
        if (payload == null || isBlank(payload.getCommandId())) {
            return;
        }
        commandAcks.put(payload.getCommandId(), payload);
    }

    public Optional<CommandAckPayload> findByCommandId(String commandId) {
        if (isBlank(commandId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(commandAcks.get(commandId));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
