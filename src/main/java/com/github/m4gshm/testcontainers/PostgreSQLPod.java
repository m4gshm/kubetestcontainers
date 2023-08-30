package com.github.m4gshm.testcontainers;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

import static java.time.Duration.ofSeconds;

@Slf4j
public class PostgreSQLPod<T extends PostgreSQLPod<T>> extends GenericPod<T> {

    public static final int POSTGRESQL_DEFAULT_PORT = 5432;
    public static final long POSTGRES_USER_ID = 999L;
    public static final long POSTGRES_GROUP_ID = 999L;
    private final String driverName = "org.postgresql.Driver";

    private String host = "localhost";
    @Getter
    private int jdbcPort = POSTGRESQL_DEFAULT_PORT;
    private String databaseName = "test";
    private String username = "test";
    private String password = "test";

    public PostgreSQLPod() {
        this("postgres:9.6.12");
    }

    PostgreSQLPod(final String dockerImageName) {
        super(dockerImageName);

        runAsNonRoot = true;
        runAsUser = POSTGRES_USER_ID;
        fsGroup = POSTGRES_GROUP_ID;

        waitStrategy = new PodLogMessageWaitStrategy()
                .withRegEx(".*database system is ready to accept connections.*\\s")
                .withStartupTimeout(ofSeconds(60));

        setCommand("postgres", "-c", "fsync=off");
    }

    public T withJdbcPort(int jdbcPort) {
        this.jdbcPort = jdbcPort;
        return self();
    }

    @Override
    public T withDatabaseName(String databaseName) {
        this.databaseName = databaseName;
        return self();
    }

    @Override
    public T withUsername(String username) {
        this.username = username;
        return self();
    }

    @Override
    public T withPassword(String password) {
        this.password = password;
        return self();
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
    public String getDriverClassName() {
        return driverName;
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public String getDatabaseName() {
        return databaseName;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public void start() {
        addExposedPort(jdbcPort);
        super.start();
    }

    @Override
    protected String getTestQueryString() {
        return "SELECT 1";
    }

    @Override
    protected List<EnvVar> getEnvVars() {
        return List.of(
                new EnvVarBuilder().withName("POSTGRES_USER").withValue(username).build(),
                new EnvVarBuilder().withName("POSTGRES_PASSWORD").withValue(password).build(),
                new EnvVarBuilder().withName("POSTGRES_DB").withValue(databaseName).build()
        );
    }
}
