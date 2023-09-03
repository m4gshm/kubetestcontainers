package com.github.m4gshm.testcontainers;

import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.JdbcDatabaseContainer;

import static java.time.Duration.ofSeconds;

@Slf4j
public class PostgreSQLPodInitializer extends AbstractJdbcDatabaseContainerInitializer {

    @Override
    protected JdbcDatabaseContainer<?> newContainer() {
        return new PostgreSQLPod<>().withStartupTimeout(ofSeconds(30)).withDeletePodOnStop(false);
    }

}
