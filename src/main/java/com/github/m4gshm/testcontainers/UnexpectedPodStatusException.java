package com.github.m4gshm.testcontainers;

public class UnexpectedPodStatusException extends StartPodException {
    public UnexpectedPodStatusException(String podName, String phase) {
        super("unexpected pod status", podName, phase);
    }
}
