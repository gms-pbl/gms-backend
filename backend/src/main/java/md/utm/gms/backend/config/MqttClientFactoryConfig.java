package md.utm.gms.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * Produces the {@link MqttPahoClientFactory} shared by both the inbound adapter
 * and the outbound message handler.
 *
 * <p>TLS/mTLS is configured when {@code gms.mqtt.tls} is present in the active profile.
 * When the sub-section is absent the factory uses plain TCP — no code changes required
 * between development and production.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MqttClientFactoryConfig {

    private final MqttProperties props;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() throws Exception {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(buildConnectOptions());
        return factory;
    }

    // -------------------------------------------------------------------------

    private MqttConnectOptions buildConnectOptions() throws Exception {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{props.getBrokerUrl()});
        options.setUserName(props.getUsername());
        if (props.getPassword() != null) {
            options.setPassword(props.getPassword().toCharArray());
        }
        options.setConnectionTimeout(props.getConnectionTimeout() / 1_000); // Paho expects seconds
        options.setKeepAliveInterval(props.getKeepAliveInterval());
        options.setCleanSession(props.isCleanSession());
        options.setAutomaticReconnect(true);

        // TLS is active only when a non-blank CA cert path is provided.
        // An empty string in application-dev.yml overrides the production defaults,
        // allowing plain TCP connections without any code changes.
        if (props.getTls() != null && props.getTls().getCaCertPath() != null
                && !props.getTls().getCaCertPath().isBlank()) {
            log.info("MQTT: configuring mTLS (caCert={}, clientCert={})",
                    props.getTls().getCaCertPath(), props.getTls().getClientCertPath());
            options.setSocketFactory(buildSslContext(props.getTls()).getSocketFactory());
        } else {
            log.info("MQTT: TLS disabled — plain TCP connection to {}", props.getBrokerUrl());
        }

        return options;
    }

    /**
     * Builds an {@link SSLContext} for mutual TLS.
     *
     * <p>Trust chain: loads the CA certificate into a transient {@link KeyStore} so the
     * client can validate the broker's server certificate.
     *
     * <p>Client identity: loads the PKCS12 bundle containing the client certificate and
     * private key so the broker can authenticate this backend instance.
     */
    private SSLContext buildSslContext(MqttProperties.Tls tls) throws Exception {
        // Trust store: verify broker certificate
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        try (InputStream caCertStream = openResource(tls.getCaCertPath())) {
            X509Certificate caCert = (X509Certificate)
                    CertificateFactory.getInstance("X.509").generateCertificate(caCertStream);
            trustStore.setCertificateEntry("emqx-ca", caCert);
        }
        TrustManagerFactory tmf = TrustManagerFactory
                .getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        // Key store: present client certificate for mTLS
        char[] keyPass = tls.getKeyPassword() != null
                ? tls.getKeyPassword().toCharArray()
                : new char[0];
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream ksStream = openResource(tls.getClientCertPath())) {
            keyStore.load(ksStream, keyPass);
        }
        KeyManagerFactory kmf = KeyManagerFactory
                .getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyPass);

        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return sslContext;
    }

    /**
     * Opens a resource from the classpath, or from the filesystem when the path
     * is prefixed with {@code file:}.
     */
    private InputStream openResource(String path) throws IOException {
        if (path.startsWith("file:")) {
            return Files.newInputStream(Path.of(path.substring(5)));
        }
        InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
        if (stream == null) {
            throw new IOException("Resource not found on classpath: " + path);
        }
        return stream;
    }
}
