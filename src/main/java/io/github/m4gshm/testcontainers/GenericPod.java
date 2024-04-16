package io.github.m4gshm.testcontainers;

import io.github.m4gshm.testcontainers.wait.PodPortWaitStrategy;
import lombok.NonNull;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;

/**
 * General purpose pod engine implementation.
 *
 * @param <SELF> - implementation type.
 */
@Slf4j
public class GenericPod<SELF extends GenericPod<SELF>> extends GenericContainer<SELF> implements PodAware {

    @Delegate
    private final PodEngine<SELF> podEngine;

    public GenericPod(@NonNull String dockerImageName) {
        super(dockerImageName);
        podEngine = new PodEngine<>((SELF) this, dockerImageName);
        waitStrategy = new PodPortWaitStrategy();
    }

    @Override
    public PodEngine<SELF> getPod() {
        return podEngine;
    }

    @Override
    protected void doStart() {
        podEngine.start();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" + podEngine.toStringFields() + "}";
    }

}
