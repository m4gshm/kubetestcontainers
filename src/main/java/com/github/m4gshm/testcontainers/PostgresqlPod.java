package com.github.m4gshm.testcontainers;

import com.github.m4gshm.testcontainers.wait.PodLogMessageWaitStrategy;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PostgresqlPod<T extends PostgresqlPod<T>> extends JdbcDatabasePod<T> {

    public static final int POSTGRES_PORT = 5432;
    public static final long POSTGRES_USER_ID = 999L;
    public static final long POSTGRES_GROUP_ID = 999L;

    @Getter
    @Setter
    private int jdbcPort = POSTGRES_PORT;

    public PostgresqlPod() {
        this("postgres:9.6.12");
    }

    PostgresqlPod(final String dockerImageName) {
        super(dockerImageName);

        withRunAsNonRoot(true).withRunAsUser(POSTGRES_USER_ID).withFsGroup(POSTGRES_GROUP_ID);

        waitStrategy = new PodLogMessageWaitStrategy()
                .withRegEx(".*database system is ready to accept connections.*\\s")
                .withStartupTimeout(getPodEngine().getStartupTimeout());

        setCommand("postgres", "-c", "fsync=off");
        setDriverClassName("org.postgresql.Driver");
        setTestQueryString("SELECT 1");

        withDatabaseName("test").withUsername("test").withPassword("test");
    }

    @Override
    protected void waitUntilContainerStarted() {
        waitStrategy.waitUntilReady(this);
        super.waitUntilContainerStarted();
    }

    @Override
    public String getJdbcUrl() {
        return "jdbc:postgresql://" + getHost() + ":" + getMappedPort(getJdbcPort()) + "/" +
                getDatabaseName() + constructUrlParameters("?", "&");
    }

    @Override
    public void start() {
        addExposedPort(getJdbcPort());
        super.start();
    }

    @Override
    protected void configure() {
        withUrlParam("loggerLevel", "OFF");
        addEnv("POSTGRES_DB", "test");
        addEnv("POSTGRES_USER", "test");
        addEnv("POSTGRES_PASSWORD", "test");
    }
}
