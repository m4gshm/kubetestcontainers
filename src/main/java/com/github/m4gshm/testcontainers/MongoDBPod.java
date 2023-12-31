package com.github.m4gshm.testcontainers;

import lombok.NonNull;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.MongoDBContainer;

import static java.util.Objects.requireNonNull;

@Slf4j
public class MongoDBPod extends MongoDBContainer implements PodAware {

    private PodEngine<MongoDBContainer> podEngine;

    public MongoDBPod() {
        this("mongo:4.0.10");
    }

    public MongoDBPod(@NonNull final String dockerImageName) {
        super(dockerImageName);
        setDockerImageName(dockerImageName);
        requireNonNull(this.podEngine, "podEngine is null");
    }

    @Delegate
    @Override
    public PodEngine<MongoDBContainer> getPod() {
        initPodEngine();
        return podEngine;
    }

    private void initPodEngine() {
        if (podEngine == null) {
            podEngine = new PodEngine<>(this);
        }
    }

}
