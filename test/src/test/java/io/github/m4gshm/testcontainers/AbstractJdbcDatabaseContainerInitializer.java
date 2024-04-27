package io.github.m4gshm.testcontainers;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.JdbcDatabaseContainer;

public abstract class AbstractJdbcDatabaseContainerInitializer
        extends AbstractContainerInitializer<JdbcDatabaseContainer<?>> {

    @Override
    protected void initContext(
            ConfigurableApplicationContext configurableApplicationContext,
            JdbcDatabaseContainer<? extends JdbcDatabaseContainer<?>> container
    ) {
        TestPropertyValues.of(
                "spring.datasource.url=" + container.getJdbcUrl(),
                "spring.datasource.username=" + container.getUsername(),
                "spring.datasource.password=" + container.getPassword()
        ).applyTo(configurableApplicationContext.getEnvironment());

        log.info("start testcontainer {}, jdbc url {} ", container.getContainerName(), container.getJdbcUrl());
    }
}
