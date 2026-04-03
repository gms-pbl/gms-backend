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

    private TopicDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new TopicDispatcher(telemetryChannel, alertChannel, statusChannel, configChannel);
    }

    @Test
    void telemetryTopicRoutesToTelemetryChannel() {
        dispatcher.dispatch(messageWithTopic("gms/site1/gh1/zone1/telemetry"));
        verify(telemetryChannel).send(any());
        verifyNoInteractions(alertChannel, statusChannel, configChannel);
    }

    @Test
    void alertTopicRoutesToAlertChannel() {
        dispatcher.dispatch(messageWithTopic("gms/site1/gh1/zone1/alert"));
        verify(alertChannel).send(any());
        verifyNoInteractions(telemetryChannel, statusChannel, configChannel);
    }

    @Test
    void statusTopicRoutesToStatusChannel() {
        dispatcher.dispatch(messageWithTopic("gms/site1/gh1/zone1/status"));
        verify(statusChannel).send(any());
        verifyNoInteractions(telemetryChannel, alertChannel, configChannel);
    }

    @Test
    void configTopicRoutesToConfigChannel() {
        dispatcher.dispatch(messageWithTopic("gms/site1/gh1/zone1/config"));
        verify(configChannel).send(any());
        verifyNoInteractions(telemetryChannel, alertChannel, statusChannel);
    }

    @Test
    void unknownTypeDoesNotRouteToAnyChannel() {
        dispatcher.dispatch(messageWithTopic("gms/site1/gh1/zone1/unknown"));
        verifyNoInteractions(telemetryChannel, alertChannel, statusChannel, configChannel);
    }

    @Test
    void missingTopicHeaderDoesNotRouteToAnyChannel() {
        Message<String> message = MessageBuilder.withPayload("{}").build();
        dispatcher.dispatch(message);
        verifyNoInteractions(telemetryChannel, alertChannel, statusChannel, configChannel);
    }

    // -------------------------------------------------------------------------

    private static Message<String> messageWithTopic(String topic) {
        return MessageBuilder.withPayload("{}")
                .setHeader(MqttHeaders.RECEIVED_TOPIC, topic)
                .build();
    }
}
