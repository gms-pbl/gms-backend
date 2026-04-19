package md.utm.gms.backend.mqtt.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import md.utm.gms.backend.mqtt.dto.CommandAckPayload;
import md.utm.gms.backend.store.CommandAckStore;
import org.junit.jupiter.api.Test;
import org.springframework.integration.support.MessageBuilder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CommandAckHandlerTest {

    @Test
    void validAckPayloadIsForwardedToStore() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        RecordingCommandAckStore commandAckStore = new RecordingCommandAckStore();
        CommandAckHandler handler = new CommandAckHandler(objectMapper, commandAckStore);

        handler.handle(MessageBuilder.withPayload("""
                {
                  "event_id": "evt-1",
                  "type": "COMMAND_ACK",
                  "tenant_id": "tenant-demo",
                  "greenhouse_id": "greenhouse-demo",
                  "gateway_id": "greenhouse-demo",
                  "command_id": "cmd-1",
                  "device_id": "portenta-1",
                  "zone_id": "zone-1",
                  "status": "FORWARDED",
                  "reason": null,
                  "timestamp": "2026-04-17T10:00:00Z"
                }
                """).build());

        Optional<CommandAckPayload> stored = commandAckStore.findByCommandId("cmd-1");
        assertThat(stored).isPresent();
        assertThat(stored.get().getStatus()).isEqualTo("FORWARDED");
        assertThat(stored.get().getDeviceId()).isEqualTo("portenta-1");
    }

    @Test
    void invalidAckPayloadIsIgnored() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        RecordingCommandAckStore commandAckStore = new RecordingCommandAckStore();
        CommandAckHandler handler = new CommandAckHandler(objectMapper, commandAckStore);

        handler.handle(MessageBuilder.withPayload("{invalid-json}").build());

        assertThat(commandAckStore.findByCommandId("cmd-1")).isEmpty();
    }

    private static final class RecordingCommandAckStore extends CommandAckStore {

        private CommandAckPayload payload;

        private RecordingCommandAckStore() {
            super(null);
        }

        @Override
        public void update(CommandAckPayload payload) {
            this.payload = payload;
        }

        @Override
        public Optional<CommandAckPayload> findByCommandId(String commandId) {
            if (payload == null || commandId == null || !commandId.equals(payload.getCommandId())) {
                return Optional.empty();
            }
            return Optional.of(payload);
        }
    }
}
