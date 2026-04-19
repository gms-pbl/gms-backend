package md.utm.gms.backend.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Typed configuration for the MQTT broker connection.
 * Bound from the {@code gms.mqtt.*} namespace in {@code application.yml}.
 *
 * <p>Override values per environment:
 * <ul>
 *   <li>Production: {@code ssl://host:8883} with TLS sub-section populated.
 *   <li>Development: {@code tcp://localhost:1883} with TLS sub-section absent.
 * </ul>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "gms.mqtt")
public class MqttProperties {

    /** MQTT broker URI. Scheme determines TLS: {@code ssl://} or {@code tcp://}. */
    @NotBlank
    private String brokerUrl;

    /** Prefix for the MQTT client-id; a UUID suffix is appended at runtime to ensure uniqueness. */
    @NotBlank
    private String clientIdPrefix = "gms-backend";

    @NotBlank
    private String username;

    private String password;

    /**
     * Topics the inbound adapter subscribes to.
     * MQTT single-level wildcards ({@code +}) are supported.
     */
    @NotNull
    private List<String> subscriptionTopics;

    /** MQTT Quality of Service level applied to all subscriptions and publishes. */
    @Min(0) @Max(2)
    private int qos = 1;

    /** Connection timeout in milliseconds. */
    private int connectionTimeout = 30_000;

    /** Keep-alive ping interval in seconds. */
    private int keepAliveInterval = 60;

    /**
     * Whether to start a clean session.
     * Set {@code false} in production to receive queued messages after a reconnect.
     */
    private boolean cleanSession = true;

    /**
     * mTLS configuration. When this sub-section is absent ({@code null}) the
     * client connects over plain TCP. When present, an {@link javax.net.ssl.SSLContext}
     * is constructed from the supplied certificate material.
     */
    private Tls tls;

    @Data
    public static class Tls {

        /**
         * Path to the CA / broker certificate in PEM format.
         * Accepts a classpath resource ({@code certs/ca.crt}) or a
         * filesystem path prefixed with {@code file:} ({@code file:/etc/gms/certs/ca.crt}).
         */
        private String caCertPath;

        /**
         * Path to the client certificate bundle in PKCS12 format (.p12).
         * Used together with {@link #keyPassword} to initialise the
         * {@link javax.net.ssl.KeyManager} for mutual TLS.
         */
        private String clientCertPath;

        /** Passphrase for the PKCS12 client certificate bundle. May be empty. */
        private String keyPassword;
    }
}
