package io.github.m4gshm.testcontainers;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.JdbcDatabaseContainer;

import static java.util.Objects.requireNonNull;


/**
 * Base relation database pod engine that provides JDBC connection config.
 *
 * @param <SELF> - child class type.
 */
@Slf4j
public abstract class JdbcDatabasePod<SELF extends JdbcDatabasePod<SELF>> extends JdbcDatabaseContainer<SELF> implements PodAware {

    private PodEngine<SELF> podEngine;
    @Getter
    @Setter
    private String driverClassName;
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
    public PodEngine<SELF> getPod() {
        initPodEngine();
        return podEngine;
    }

    private void initPodEngine() {
        if (podEngine == null) {
            podEngine = new PodEngine<>((SELF) this);
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
    public SELF withUsername(String username) {
        setUsername(username);
        return self();
    }

    @Override
    public SELF withPassword(String password) {
        setPassword(password);
        return self();
    }

    @Override
    public SELF withDatabaseName(String dbName) {
        setDatabaseName(dbName);
        return self();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" + podEngine.toStringFields() + "}";
    }

    private interface Excludes {
        void start();
    }

}
