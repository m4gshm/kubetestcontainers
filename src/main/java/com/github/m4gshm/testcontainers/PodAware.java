package com.github.m4gshm.testcontainers;

public interface PodAware {
    PodEngine<?> getPod();
}
