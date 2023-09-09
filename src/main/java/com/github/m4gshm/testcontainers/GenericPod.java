package com.github.m4gshm.testcontainers;

import com.github.m4gshm.testcontainers.wait.PodPortWaitStrategy;
import lombok.NonNull;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;

import static java.util.Objects.requireNonNull;

@Slf4j
public class GenericPod<T extends GenericPod<T>> extends GenericContainer<T> {

    private PodEngine<T> podEngine;

    public GenericPod(@NonNull String dockerImageName) {
        super(dockerImageName);
        var podEngine = this.podEngine;
        requireNonNull(podEngine, "podEngine is null");
        waitStrategy = new PodPortWaitStrategy(podEngine, this);
    }

    @Delegate
    public PodEngine<T> getPodEngine() {
        initPodEngine();
        return podEngine;
    }

    private void initPodEngine() {
        if (podEngine == null) {
            podEngine = new PodEngine<>((T) this);
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
