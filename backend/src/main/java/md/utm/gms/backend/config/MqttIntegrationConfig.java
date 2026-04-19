package md.utm.gms.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.endpoint.EventDrivenConsumer;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;

import java.util.UUID;

/**
 * Spring Integration channel topology and MQTT adapter wiring.
 *
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  MQTT Broker                                                        │
 * │     │  (subscribes: gms/+/+/uplink/# + compatibility topics)         │
 * │     ▼                                                               │
 * │  MqttPahoMessageDrivenChannelAdapter                                │
 * │     │                                                               │
 * │     ▼                                                               │
 * │  mqttInboundChannel  ──►  TopicDispatcher (@ServiceActivator)       │
 * │                                │        │        │        │         │
 * │                                ▼        ▼        ▼        ▼         │
 * │                          telemetry   alert   status   config        │
 * │                           Channel   Channel  Channel  Channel       │
 * │                                │                                   │
 * │                                ▼                                   │
 * │                         registry / commandAck channels             │
 * │                                                                     │
 * │  CommandService  ──►  mqttOutboundChannel                           │
 * │                              │                                      │
 * │                              ▼                                      │
 * │                    MqttPahoMessageHandler  ──►  MQTT Broker         │
 * └─────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>All channels are {@link DirectChannel} (synchronous, single-threaded).
 * Swap any channel for an {@code ExecutorChannel} to decouple the MQTT receive
 * thread from handler processing without touching handler code.
 */
@Configuration
@RequiredArgsConstructor
public class MqttIntegrationConfig {

    private final MqttProperties props;
    private final MqttPahoClientFactory mqttClientFactory;

    // ── Channels ──────────────────────────────────────────────────────────────

    @Bean public MessageChannel mqttInboundChannel()  { return new DirectChannel(); }
    @Bean public MessageChannel mqttOutboundChannel() { return new DirectChannel(); }
    @Bean public MessageChannel telemetryChannel()    { return new DirectChannel(); }
    @Bean public MessageChannel alertChannel()        { return new DirectChannel(); }
    @Bean public MessageChannel statusChannel()       { return new DirectChannel(); }
    @Bean public MessageChannel configChannel()       { return new DirectChannel(); }
    @Bean public MessageChannel registryChannel()     { return new DirectChannel(); }
    @Bean public MessageChannel commandAckChannel()   { return new DirectChannel(); }

    // ── Inbound adapter ───────────────────────────────────────────────────────

    /**
     * Subscribes to all wildcard topics in {@code gms.mqtt.subscription-topics}
     * and forwards each arriving message to {@link #mqttInboundChannel()}.
     */
    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttInboundAdapter() {
        String clientId = props.getClientIdPrefix() + "-in-" + UUID.randomUUID();
        String[] topics = props.getSubscriptionTopics().toArray(String[]::new);

        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId, mqttClientFactory, topics);

        adapter.setCompletionTimeout(5_000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(props.getQos());
        adapter.setOutputChannel(mqttInboundChannel());
        return adapter;
    }

    // ── Outbound handler ──────────────────────────────────────────────────────

    /**
     * Publishes messages placed on {@link #mqttOutboundChannel()} to the broker.
     * The destination topic must be supplied via the {@code mqtt_topic} message header
     * ({@code MqttHeaders.TOPIC}).
     */
    @Bean
    public MqttPahoMessageHandler mqttOutboundHandler() {
        String clientId = props.getClientIdPrefix() + "-out-" + UUID.randomUUID();
        MqttPahoMessageHandler handler =
                new MqttPahoMessageHandler(clientId, mqttClientFactory);
        handler.setAsync(true);
        handler.setDefaultQos(props.getQos());
        return handler;
    }

    /**
     * Wires the outbound handler to consume from {@link #mqttOutboundChannel()}.
     * An explicit {@code EventDrivenConsumer} is required because
     * {@link MqttPahoMessageHandler} is a {@link org.springframework.messaging.MessageHandler},
     * not a channel interceptor.
     */
    @Bean
    public EventDrivenConsumer mqttOutboundEndpoint() {
        return new EventDrivenConsumer(
                (DirectChannel) mqttOutboundChannel(), mqttOutboundHandler());
    }
}
