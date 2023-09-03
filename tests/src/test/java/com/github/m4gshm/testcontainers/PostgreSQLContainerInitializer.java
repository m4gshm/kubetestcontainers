package com.github.m4gshm.testcontainers;

import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;

@Slf4j
public class PostgreSQLContainerInitializer extends AbstractJdbcDatabaseContainerInitializer {

    @Override
    protected JdbcDatabaseContainer<?> newContainer() {
        return new PostgreSQLContainer<>();
    }

}
