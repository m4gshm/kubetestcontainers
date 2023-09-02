package com.github.m4gshm.testcontainers;

public interface PodAware {
    void withPod(GenericPod<?> pod);
}
