package io.github.m4gshm.testcontainers;

import lombok.NonNull;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.MongoDBContainer;

/**
 * Kubernetes based extension of the {@link org.testcontainers.containers.MongoDBContainer}.
 */
@Slf4j
public class MongoDBPod extends MongoDBContainer implements PodAware {

    private PodContainerDelegate<MongoDBContainer> podEngine;

    public MongoDBPod() {
        this("mongo:4.0.10");
    }

    public MongoDBPod(@NonNull final String dockerImageName) {
        super(dockerImageName);
        podEngine = new PodContainerDelegate<>(this, dockerImageName);
    }

    @Delegate
    @Override
    public PodContainerDelegate<MongoDBContainer> getPod() {
        return podEngine;
    }

}
