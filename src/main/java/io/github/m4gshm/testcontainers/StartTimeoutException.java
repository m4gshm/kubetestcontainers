package io.github.m4gshm.testcontainers;

public class StartTimeoutException extends StartPodException {
    public StartTimeoutException(String podName, String phase) {
        super("start timeout", podName, phase);
    }
}
