package com.github.m4gshm.testcontainers;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class PostgreSQLPod<T extends PostgreSQLPod<T>> extends GenericPod<T> {

    public static final int POSTGRESQL_DEFAULT_PORT = 5432;
    public static final String IMAGE = "postgres";
    public static final String DEFAULT_TAG = "9.6.12";
    public static final long POSTGRES_USER_ID = 999L;
    public static final long POSTGRES_GROUP_ID = 999L;
    private static final Integer POSTGRESQL_PORT = 5432;
    private final String driverName = "org.postgresql.Driver";

    private String host = "localhost";
    private String databaseName = "test";
    private String username = "test";
    private String password = "test";

    public PostgreSQLPod() {
        this(IMAGE + ":" + DEFAULT_TAG);
    }

    PostgreSQLPod(final String dockerImageName) {
        super(dockerImageName);

        withRunAsNonRoot(true);
        withRunAsUser(POSTGRES_USER_ID);
        withFsGroup(POSTGRES_GROUP_ID);

        addExposedPort(POSTGRESQL_PORT);
    }

    @Override
    public T withInitScript(String initScriptPath) {
        return super.withInitScript(initScriptPath);
    }

    @Override
    public T withDatabaseName(String databaseName) {
        this.databaseName = databaseName;
        return self();
    }

    public T withUsername(String username) {
        this.username = username;
        return self();
    }

    public T withPassword(String password) {
        this.password = password;
        return self();
    }

    @Override
    public String getJdbcUrl() {
        return "jdbc:postgresql://" + getHost() + ":" + getMappedPort(POSTGRESQL_DEFAULT_PORT) + "/" +
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
