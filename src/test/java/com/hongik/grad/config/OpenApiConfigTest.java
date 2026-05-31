package com.hongik.grad.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

  private final OpenAPI openApi = new OpenApiConfig().hongikGradOpenApi();

  @Test
  void registersBearerJwtSecurityScheme() {
    SecurityScheme scheme = openApi.getComponents().getSecuritySchemes().get("bearerAuth");
    assertThat(scheme).isNotNull();
    assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
    assertThat(scheme.getScheme()).isEqualTo("bearer");
    assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
  }

  @Test
  void appliesBearerSchemeGlobally() {
    assertThat(openApi.getSecurity()).anySatisfy(req -> assertThat(req).containsKey("bearerAuth"));
  }
}
