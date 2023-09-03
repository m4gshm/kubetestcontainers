package com.github.m4gshm.testcontainers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.testcontainers.containers.JdbcDatabaseContainer;

public abstract class AbstractJdbcDatabaseContainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    protected abstract JdbcDatabaseContainer<?> newContainer();

    @Override
    public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
        var container = newContainer();
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
