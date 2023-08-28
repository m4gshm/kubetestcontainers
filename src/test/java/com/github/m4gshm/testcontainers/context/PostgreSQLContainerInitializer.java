package com.github.m4gshm.testcontainers.context;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;

@Slf4j
public class PostgreSQLContainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public JdbcDatabaseContainer<?> newPostgresContainer() {
        return new PostgreSQLContainer<>().withExposedPorts(1234);
    }

    public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
        var container = newPostgresContainer();
        configurableApplicationContext.addApplicationListener(event -> {
            if (event instanceof ContextClosedEvent) {
                log.info("stop testcontainer {}, jdbc url {} ", container.getContainerName(), container.getJdbcUrl());
                container.stop();
            }
        });
        container.start();

        TestPropertyValues.of(
                "spring.datasource.url=" + container.getJdbcUrl(),
                "spring.datasource.username=" + container.getUsername(),
                "spring.datasource.password=" + container.getPassword()
        ).applyTo(configurableApplicationContext.getEnvironment());

        log.info("start testcontainer {}, jdbc url {} ", container.getContainerName(), container.getJdbcUrl());
    }

}
