package in.qualtechedge.qcp.templates.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Springdoc OpenAPI metadata. Swagger UI is served at /swagger-ui.html.
 *
 * <p>The title comes from {@code spring.application.name} rather than a literal: this is the
 * one place the template's name is published to anyone outside the codebase, so a missed
 * rename here is the version everybody reads.
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${qcp.openapi.description:}")
    private String description;

    @Value("${qcp.openapi.contact:}")
    private String contact;

    @Bean
    public OpenAPI apiInfo() {
        Info info = new Info()
                .title(applicationName + " API")
                .version("0.0.1");
        if (!description.isBlank()) {
            info.description(description);
        }
        if (!contact.isBlank()) {
            info.contact(new Contact().name(contact));
        }
        return new OpenAPI().info(info);
    }
}
