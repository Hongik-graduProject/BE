package com.hongik.grad.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI hongikGradOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Hongik Grad Backend API")
                .version("0.1.0")
                .description("Disclosure-driven theme analysis service backend.")
                .license(new License().name("Proprietary")));
  }
}
