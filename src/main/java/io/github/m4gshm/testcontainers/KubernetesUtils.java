package io.github.m4gshm.testcontainers;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.internal.ExecWebSocketListener;
import io.fabric8.kubernetes.client.http.WebSocket;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Boolean.TRUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toMap;

@Slf4j
@UtilityClass
public class KubernetesUtils {
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

    public static Map<Integer, LocalPortForward> startPortForward(
            PodResource pod, InetAddress inetAddress, Collection<Integer> ports) {
        var podName = pod.get().getMetadata().getName();
        return ports.stream().collect(toMap(port -> port, port -> {
            var localPortForward = inetAddress != null
                    ? pod.portForward(port, inetAddress, 0)
                    : pod.portForward(port);
            var localAddress = localPortForward.getLocalAddress();
            var localPort = localPortForward.getLocalPort();
            if (localPortForward.errorOccurred()) {
                var clientThrowables = localPortForward.getClientThrowables();
                if (!clientThrowables.isEmpty()) {
                    var throwable = clientThrowables.iterator().next();
                    throw new StartPodException("port forward client error", podName, throwable);
                }
                var serverThrowables = localPortForward.getServerThrowables();
                if (!serverThrowables.isEmpty()) {
                    var throwable = serverThrowables.iterator().next();
                    throw new StartPodException("port forward server error", podName, throwable);
                }
            } else {
                log.info("port forward local {}:{} to remote {}:{}, ", localAddress.getHostAddress(), localPort,
                        podName, port);
            }
            return localPortForward;
        }));
    }


    @SneakyThrows
    public boolean removeFile(PodResource podResource, int requestTimeout, String tarName) {
        try (var exec = waitEmptyQueue(podResource.redirectingError().exec("rm", tarName))) {
            var exitCode = exec.exitCode().get(requestTimeout, MILLISECONDS);
            var deleted = exitCode != 0;
            if (deleted) {
                log.warn("deleting of temporary file {} finished with unexpected code {}, errOut: {}",
                        tarName, exitCode, getError(exec));
            }
            return deleted;
        }
    }

    @SneakyThrows
    public static ExecResult exec(PodResource podResource, int requestTimeout, Charset outputCharset, String... command) {
        var hasShellCall = command.length > 1 && command[0].equals("sh") && command[1].equals("-c");
        if (!hasShellCall) {
            var newCmd = new String[command.length + 2];
            newCmd[0] = "sh";
            newCmd[1] = "-c";
            System.arraycopy(command, 0, newCmd, 2, command.length);
            command = newCmd;
        }

        try (var execWatch = podResource.redirectingOutput().redirectingError().exec(command)) {
            var exited = execWatch.exitCode();
            var exitCode = exited.get(requestTimeout, MILLISECONDS);
            ;
            String errOut;
            try {
                errOut = new String(execWatch.getError().readAllBytes(), outputCharset);
            } catch (IOException e) {
                errOut = "";
                log.info("err output read error", e);
            }
            String output;
            try {
                output = new String(execWatch.getOutput().readAllBytes(), outputCharset);
            } catch (IOException e) {
                output = "";
                log.info("output read error", e);
            }

            return new ExecResult(exitCode, output, errOut);
        }
    }


    @SneakyThrows
    public static void uploadBase64(PodResource podResource, int requestTimeout, String escapedTarPath, byte[] payload) {
        var encoded = Base64.getEncoder().encodeToString(payload);
        try (var uploadWatch = podResource.terminateOnError().exec("sh", "-c", "echo " + encoded + "| base64 -d >" + "'" + escapedTarPath + "'")) {
            var code = uploadWatch.exitCode().get(requestTimeout, MILLISECONDS);
            if (code != 0) {
                throw new UploadFileException("Unexpected exit code " + code + ", file '" + escapedTarPath + "'");
            }
            checkSize(podResource, requestTimeout, escapedTarPath, payload.length);
        }
    }

    @SneakyThrows
    public static void uploadStdIn(PodResource podResource, int requestTimeout, String escapedTarPath, byte[] payload) {
        try (var exec = podResource.redirectingInput().terminateOnError().exec("cp", "/dev/stdin", escapedTarPath)) {
            var input = exec.getInput();
            input.write(payload);
            input.flush();
            waitEmptyQueue(exec);
            checkSize(podResource, requestTimeout, escapedTarPath, payload.length);
        }
    }

    @SneakyThrows
    public static long getFileSize(PodResource podResource, int requestTimeout, String filePath) {
        var byteCount = new ByteArrayOutputStream();
        try (var exec = podResource.writingOutput(byteCount).terminateOnError().exec("sh", "-c", "wc -c < " + filePath)) {
            var exitCode = exec.exitCode().get(requestTimeout, MILLISECONDS);
            var remoteSizeRaw = byteCount.toString(UTF_8).trim();
            waitEmptyQueue(exec);
            return Integer.parseInt(remoteSizeRaw);
        }
    }

    @SneakyThrows
    public static void checkSize(PodResource podResource, int requestTimeout, String filePath, long expected) {
        var size = getFileSize(podResource, requestTimeout, filePath);
        if (size != expected) {
            Thread.sleep(100);
            size = getFileSize(podResource, requestTimeout, filePath);
        }
        if (size != expected) {
            throw new UploadFileException("Unexpected file size " + size + ", expected " + expected + ", file '" + filePath + "'");
        }
    }

    public record ExecResult(int exitCode, String output, String error) {
    }

}
