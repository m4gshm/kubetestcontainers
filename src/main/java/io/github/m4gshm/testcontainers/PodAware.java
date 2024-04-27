package io.github.m4gshm.testcontainers;

public interface PodAware {
    PodContainerDelegate<?> getPod();
}
