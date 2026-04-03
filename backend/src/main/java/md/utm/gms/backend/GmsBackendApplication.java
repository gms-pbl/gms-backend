package md.utm.gms.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.integration.config.EnableIntegration;

@SpringBootApplication
@EnableIntegration
@ConfigurationPropertiesScan
public class GmsBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(GmsBackendApplication.class, args);
    }
}
