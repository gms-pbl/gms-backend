package md.utm.gms.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gmsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Greenhouse Management System API")
                        .description("GMS cloud backend — sensor telemetry, alert management, and actuator control.")
                        .version("1.0.0-pilot")
                        .contact(new Contact()
                                .name("UTM GMS Team")
                                .email("gms@utm.md")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Local dev (dev profile)"),
                        new Server().url("http://localhost:8080").description("Local prod (default profile)"),
                        new Server().url("https://api.gms.utm.md").description("Production")));
    }
}