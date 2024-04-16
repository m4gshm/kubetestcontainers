package io.github.m4gshm.testcontainers;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.internal.ExecWebSocketListener;
import io.fabric8.kubernetes.client.http.WebSocket;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Boolean.TRUE;
import static java.nio.charset.StandardCharsets.UTF_8;

@UtilityClass
public class PodEngineUtils {
    public static final String RUNNING = "Running";
    public static final String PENDING = "Pending";
    public static final String UNKNOWN = "Unknown";

    public static String getError(ExecWatch exec) {
        try {
            return new String(exec.getError().readAllBytes(), UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    @NotNull
    public static String getOut(ExecWatch exec) {
        try {
            return new String(exec.getOutput().readAllBytes(), UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    public static String createExecCommandForUpload(String file) {
        String directoryTrimmedFromFilePath = file.substring(0, file.lastIndexOf('/'));
        final String directory = directoryTrimmedFromFilePath.isEmpty() ? "/" : directoryTrimmedFromFilePath;
        return String.format("cat - > %s", shellQuote(file));
    }

    public static String shellQuote(String value) {
        return "'" + escapeQuotes(value) + "'";
    }

    public static String escapeQuotes(String value) {
        return value.replace("'", "'\\''");
    }

    @SneakyThrows
    public static ExecWatch waitEmptyQueue(ExecWatch exec) {
        var webSocketRefFld = ExecWebSocketListener.class.getDeclaredField("webSocketRef");
        webSocketRefFld.setAccessible(true);
        var webSocketRef = (AtomicReference<WebSocket>) webSocketRefFld.get(exec);
        var webSocket = webSocketRef.get();
        var inQueue = webSocket.queueSize();
        while (inQueue > 0) {
            Thread.yield();
            inQueue = webSocket.queueSize();
        }
        return exec;
    }

    public static PodResource createPod(KubernetesClient kubernetesClient, Pod build) {
        return resource(kubernetesClient, kubernetesClient.resource(build).create());
    }

    public static PodResource resource(KubernetesClient kubernetesClient, Pod item) {
        return kubernetesClient.pods().resource(item);
    }

    public static boolean isRunning(Pod pod) {
        return pod != null && RUNNING.equals(pod.getStatus().getPhase());
    }

    public static @Nullable ContainerStatus getFirstNotReadyContainer(PodStatus status) {
        return status.getContainerStatuses().stream().filter(containerStatus -> {
            var ready = TRUE.equals(containerStatus.getReady());
            return !ready;
        }).findFirst().orElse(null);
    }

}
