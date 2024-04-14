package io.github.m4gshm.testcontainers;

import org.testcontainers.containers.Container;

/**
 * A generator of unique pod name interface.
 */
public interface PodNameGenerator {
    String generatePodName(Container<?> container);
}
