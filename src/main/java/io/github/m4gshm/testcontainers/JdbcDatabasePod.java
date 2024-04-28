package io.github.m4gshm.testcontainers;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.JdbcDatabaseContainer;


/**
 * Base relation database pod engine that provides JDBC connection config.
 *
 * @param <SELF> - child class type.
 */
@Slf4j
public abstract class JdbcDatabasePod<SELF extends JdbcDatabasePod<SELF>> extends JdbcDatabaseContainer<SELF> implements PodAware {

    private PodContainerDelegate<SELF> podEngine;
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
        podEngine = new PodContainerDelegate<>((SELF) this, dockerImageName);
    }

    @Delegate
    public PodContainerDelegate<SELF> getPod() {
        return podEngine;
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

}
