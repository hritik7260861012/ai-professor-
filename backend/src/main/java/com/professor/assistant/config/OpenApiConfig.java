package com.professor.assistant.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata for the OpenAPI document served at /v3/api-docs
 * and rendered by the Swagger UI at /swagger-ui.html.
 *
 * <p>Spring MVC picks up the {@link OpenAPI} bean automatically.
 */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI apiInfo() {
    return new OpenAPI()
        .info(
            new Info()
                .title("AI Professor Assistant API")
                .version("1.0.0")
                .description(
                    "REST API for the AI Professor Assistant platform. "
                        + "Phase 1 delivers project scaffolding, a health "
                        + "endpoint, consistent structured errors, and the "
                        + "initial database schema.")
                .contact(new Contact().name("AI Professor Assistant"))
                .license(new License().name("MIT")));
  }
}
