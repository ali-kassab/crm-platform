package com.crmplatform.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Real PostgreSQL, Redis and an S3-compatible store (S3Mock, ADR-0014) for integration tests. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17.12-alpine")).withReuse(true);
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>(DockerImageName.parse("redis:8.2.11-alpine")).withExposedPorts(6379).withReuse(true);
    }

    @Bean
    GenericContainer<?> s3() {
        return new GenericContainer<>(DockerImageName.parse("adobe/s3mock:5.2.3"))
                .withExposedPorts(9090)
                .waitingFor(Wait.forHttp("/").forPort(9090).forStatusCodeMatching(code -> code < 500))
                .withReuse(true);
    }

    @Bean
    DynamicPropertyRegistrar s3Properties(GenericContainer<?> s3) {
        return registry -> registry.add("app.storage.endpoint",
                () -> "http://" + s3.getHost() + ":" + s3.getMappedPort(9090));
    }
}
