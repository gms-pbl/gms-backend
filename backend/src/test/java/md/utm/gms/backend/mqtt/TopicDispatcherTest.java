package md.utm.gms.backend.mqtt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TopicDispatcherTest {

    @Mock private MessageChannel telemetryChannel;
    @Mock private MessageChannel alertChannel;
    @Mock private MessageChannel statusChannel;
    @Mock private MessageChannel configChannel;
    @Mock private MessageChannel registryChannel;
    @Mock private MessageChannel commandAckChannel;

    private TopicDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new TopicDispatcher(
                telemetryChannel,
                alertChannel,
                statusChannel,
                configChannel,
                registryChannel,
                commandAckChannel);
    }

    @Test
    void telemetryTopicRoutesToTelemetryChannel() {
        dispatcher.dispatch(messageWithTopic("gms/tenant-a/greenhouse-a/uplink/telemetry"));
        verify(telemetryChannel).send(any());
        verifyNoInteractions(alertChannel, statusChannel, configChannel, registryChannel, commandAckChannel);
    }

    @Test
    void alertTopicRoutesToAlertChannel() {
        dispatcher.dispatch(messageWithTopic("gms/tenant-a/greenhouse-a/uplink/alert"));
        verify(alertChannel).send(any());
        verifyNoInteractions(telemetryChannel, statusChannel, configChannel, registryChannel, commandAckChannel);
    }

    @Test
    void statusTopicRoutesToStatusChannel() {
        dispatcher.dispatch(messageWithTopic("gms/tenant-a/greenhouse-a/uplink/status"));
        verify(statusChannel).send(any());
        verifyNoInteractions(telemetryChannel, alertChannel, configChannel, registryChannel, commandAckChannel);
    }

    @Test
    void configTopicRoutesToConfigChannel() {
        dispatcher.dispatch(messageWithTopic("gms/tenant-a/greenhouse-a/uplink/config"));
        verify(configChannel).send(any());
        verifyNoInteractions(telemetryChannel, alertChannel, statusChannel, registryChannel, commandAckChannel);
    }

    @Test
    void registryTopicRoutesToRegistryChannel() {
        dispatcher.dispatch(messageWithTopic("gms/tenant-a/greenhouse-a/uplink/registry"));
        verify(registryChannel).send(any());
        verifyNoInteractions(telemetryChannel, alertChannel, statusChannel, configChannel, commandAckChannel);
    }

    @Test
    void commandAckTopicRoutesToCommandAckChannel() {
        dispatcher.dispatch(messageWithTopic("gms/tenant-a/greenhouse-a/uplink/command_ack"));
        verify(commandAckChannel).send(any());
        verifyNoInteractions(telemetryChannel, alertChannel, statusChannel, configChannel, registryChannel);
    }

    @Test
    void downlinkTopicIsIgnored() {
        dispatcher.dispatch(messageWithTopic("gms/tenant-a/greenhouse-a/downlink/command"));
        verifyNoInteractions(telemetryChannel, alertChannel, statusChannel, configChannel, registryChannel, commandAckChannel);
    }

    @Test
    void legacyTopicStillRoutes() {
        dispatcher.dispatch(messageWithTopic("gms/site1/gh1/zone1/telemetry"));
        verify(telemetryChannel).send(any());
    }

    @Test
    void unknownTypeDoesNotRouteToAnyChannel() {
        dispatcher.dispatch(messageWithTopic("gms/tenant-a/greenhouse-a/uplink/unknown"));
        verifyNoInteractions(telemetryChannel, alertChannel, statusChannel, configChannel, registryChannel, commandAckChannel);
    }

    @Test
    void missingTopicHeaderDoesNotRouteToAnyChannel() {
        Message<String> message = MessageBuilder.withPayload("{}").build();
        dispatcher.dispatch(message);
        verifyNoInteractions(telemetryChannel, alertChannel, statusChannel, configChannel, registryChannel, commandAckChannel);
    }

    // -------------------------------------------------------------------------

    private static Message<String> messageWithTopic(String topic) {
        return MessageBuilder.withPayload("{}")
                .setHeader(MqttHeaders.RECEIVED_TOPIC, topic)
                .build();
    }
}
