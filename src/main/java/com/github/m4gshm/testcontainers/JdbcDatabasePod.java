package com.github.m4gshm.testcontainers;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.JdbcDatabaseContainer;

import static java.util.Objects.requireNonNull;

@Slf4j
public class JdbcDatabasePod<T extends JdbcDatabasePod<T>> extends JdbcDatabaseContainer<T> implements PodAware {

    private PodEngine<T> podEngine;
    @Getter
    @Setter
    private String driverClassName;
    @Getter
    @Setter
    private String jdbcUrl;
    @Getter
    @Setter
    private String username;
    @Getter
    @Setter
    private String password;
    @Getter
    @Setter
    private String databaseName;
    @Getter
    @Setter
    private String testQueryString;

    public JdbcDatabasePod(@NonNull String dockerImageName) {
        super(dockerImageName);
        setDockerImageName(dockerImageName);
        requireNonNull(this.podEngine, "podEngine is null");
    }

    @Delegate(excludes = Excludes.class)
    public PodEngine<T> getPod() {
        initPodEngine();
        return podEngine;
    }

    private void initPodEngine() {
        if (podEngine == null) {
            podEngine = new PodEngine<>((T) this);
        }
    }

    @Override
    public void start() {
        getPod().start();
    }

    @Override
    protected void doStart() {
        configure();
    }

    @Override
    public T withUsername(String username) {
        setUsername(username);
        return self();
    }

    @Override
    public T withPassword(String password) {
        setPassword(password);
        return self();
    }

    @Override
    public T withDatabaseName(String dbName) {
        setDatabaseName(dbName);
        return self();
    }

    @Override
    public T withStartupTimeoutSeconds(int startupTimeoutSeconds) {
        return super.withStartupTimeoutSeconds(startupTimeoutSeconds);
    }

    @Override
    public T withConnectTimeoutSeconds(int connectTimeoutSeconds) {
        return super.withConnectTimeoutSeconds(connectTimeoutSeconds);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" + podEngine.toStringFields() + "}";
    }

    private interface Excludes {
        void start();
    }

}
