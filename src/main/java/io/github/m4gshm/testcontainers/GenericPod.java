package io.github.m4gshm.testcontainers;

import io.github.m4gshm.testcontainers.wait.PodPortWaitStrategy;
import lombok.NonNull;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;

import static java.util.Objects.requireNonNull;

/**
 * General purpose pod engine implementation.
 * @param <SELF> - implementation type.
 */
@Slf4j
public class GenericPod<SELF extends GenericPod<SELF>> extends GenericContainer<SELF> implements PodAware {

    private PodEngine<SELF> podEngine;

    public GenericPod(@NonNull String dockerImageName) {
        super(dockerImageName);
        var podEngine = this.podEngine;
        requireNonNull(podEngine, "podEngine is null");
        waitStrategy = new PodPortWaitStrategy();
    }

    @Delegate
    @Override
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
    protected void doStart() {
        configure();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" + podEngine.toStringFields() + "}";
    }

}
