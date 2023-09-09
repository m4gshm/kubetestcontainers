package com.github.m4gshm.testcontainers;

public interface PodEngineAware {
    void setPod(PodEngine<?> pod);
}
