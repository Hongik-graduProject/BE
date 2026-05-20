package com.hongik.grad.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Spring Boot 3.1+ {@link ServiceConnection}-driven Testcontainers wiring. Importing this
 * configuration exposes a PostgreSQL 15 container whose JDBC URL and credentials are auto-injected
 * into the Spring {@code DataSource}. No manual {@code @DynamicPropertySource} required.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgresContainer() {
    return new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"));
  }
}
