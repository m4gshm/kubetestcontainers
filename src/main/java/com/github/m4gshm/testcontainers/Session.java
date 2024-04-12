package com.github.m4gshm.testcontainers;

import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.client.dsl.PodResource;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Runtime.getRuntime;
import static org.testcontainers.DockerClientFactory.TESTCONTAINERS_THREAD_GROUP;

@Slf4j
public class Session {

    private final static Session INSTANCE = new Session();
    private final String id = UUID.randomUUID().toString();
    private final Map<String, PodResource> pods = new ConcurrentHashMap<>();

    private Session() {
        getRuntime().addShutdownHook(new Thread(TESTCONTAINERS_THREAD_GROUP, this::deletePods));
    }

    public static Session instance() {
        return INSTANCE;
    }

    public String id() {
        return id;
    }

    public void registerPodForDelayedDeleting(String hash, PodResource pod) {
        pods.put(hash, pod);
    }

    public void deletePods() {
        for (var pod : pods.values()) {
            var podName = pod.get().getMetadata().getName();
            try {
                log.debug("removing pod {}", podName);
                var statusDetails = pod.delete();
                if (log.isDebugEnabled())
                    if (statusDetails != null) {
                        var statuses = statusDetails.stream().map(StatusDetails::getName).toList();
                        log.debug("pod {} is removed with statuses {}", podName, statuses);
                    } else {
                        log.debug("pod {} is removed", podName);
                    }
            } catch (Exception e) {
                log.error("error on pod deleting {}", podName, e);
            }
        }
    }

    public PodResource find(String hash) {
        return pods.get(hash);
    }
}
