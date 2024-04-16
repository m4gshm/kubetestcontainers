package io.github.m4gshm.testcontainers;

import lombok.NonNull;

/**
 * A generator of unique pod name interface.
 */
public interface PodNameGenerator {
    String generatePodName(@NonNull String dockerImageName);
}
