package com.github.m4gshm.testcontainers;

public class StartPodException extends RuntimeException {
    public StartPodException(String errorDescription, String podName, String phase) {
        this(errorDescription, podName, phase, null);
    }

    public StartPodException(String errorDescription, String podName, String phase, Throwable cause) {
        super(errorDescription + ", " + "podName " + podName + ", phase " + phase, cause);
    }

}
