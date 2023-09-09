package com.github.m4gshm.testcontainers;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.LocalObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodSecurityContextBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.SecurityContextBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.kubernetes.client.dsl.PodResource;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.images.builder.Transferable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.lang.Boolean.TRUE;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static lombok.AccessLevel.PROTECTED;
import static org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.BIGNUMBER_POSIX;
import static org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.LONGFILE_POSIX;

@Slf4j
public class PodEngine<T extends Container<T>> {
    public static final String RUNNING = "Running";
    public static final String PENDING = "Pending";
    public static final String UNKNOWN = "Unknown";
    protected final PodNameGenerator podNameGenerator;
    private final T container;
    protected KubernetesClient kubernetesClient;
    @Getter
    @Setter
    protected String dockerImageName;
    @Getter
    protected Boolean runAsNonRoot;
    @Getter
    protected Long runAsUser;
    @Getter
    protected Long fsGroup;
    protected boolean privilegedMode;
    protected String imagePullPolicy = "Always";
    @Getter
    protected Duration startupTimeout = ofSeconds(60);
    @Getter
    protected String portProtocol = "TCP";
    private PodResource podResource;
    private Map<Integer, LocalPortForward> localPortForwards = Map.of();
    @Getter
    private String imagePullSecretName;
    @Getter
    private UnaryOperator<PodBuilder> podBuilderCustomizer;
    @Getter
    private String podContainerName = "main";
    private String podName;
    private boolean deletePodOnStop = true;
    @Getter(PROTECTED)
    private boolean started;

    public PodEngine(@NonNull T container) {
        this(container, null);
    }

    public PodEngine(@NonNull T container, String dockerImageName) {
        this(container, dockerImageName, new KubernetesClientBuilder().build(), new DefaultPodNameGenerator());
    }

    public PodEngine(@NonNull T container, String dockerImageName, @NonNull KubernetesClient kubernetesClient,
                     @NonNull PodNameGenerator podNameGenerator) {
        this.container = container;
        this.kubernetesClient = kubernetesClient;
        this.podNameGenerator = podNameGenerator;
        this.dockerImageName = dockerImageName;
    }

    @Override
    public String toString() {
        return "PodEngine{" + toStringFields() + '}';
    }

    public String toStringFields() {
        return ", dockerImageName='" + dockerImageName + '\'' +
                ", runAsNonRoot=" + runAsNonRoot +
                ", runAsUser=" + runAsUser +
                ", fsGroup=" + fsGroup +
                ", privilegedMode=" + privilegedMode +
                ", imagePullPolicy='" + imagePullPolicy + '\'' +
                ", startupTimeout=" + startupTimeout +
                ", portProtocol='" + portProtocol + '\'' +
                ", podResource=" + getPodResource() +
                ", localPortForwards=" + localPortForwards +
                ", imagePullSecretName='" + imagePullSecretName + '\'' +
                ", podBuilderCustomizer=" + podBuilderCustomizer +
                ", podContainerName='" + podContainerName + '\'' +
                ", podName='" + podName + '\'' +
                ", deletePodOnStop=" + deletePodOnStop +
                ", started=" + started;
    }

    public DockerClient getDockerClient() {
        throw new UnsupportedOperationException("getDockerClient");
    }

    public T withImagePullPolicy(ImagePullPolicy imagePullPolicy) {
        if (imagePullPolicy == null) {
            this.imagePullPolicy = "Never";
        } else if (imagePullPolicy.getClass().equals(PullPolicy.alwaysPull().getClass())) {
            this.imagePullPolicy = "Always";
        } else {
            this.imagePullPolicy = "IfNotPresent";
        }
        return container;
    }

    public T withKubernetesClient(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
        return container;
    }

    public T withRunAsNonRoot(Boolean runAsNonRoot) {
        this.runAsNonRoot = runAsNonRoot;
        return container;
    }

    public T withRunAsUser(Long runAsUser) {
        this.runAsUser = runAsUser;
        return container;
    }

    public T withFsGroup(Long fsGroup) {
        this.fsGroup = fsGroup;
        return container;
    }

    public T withPodBuilderCustomizer(UnaryOperator<PodBuilder> podBuilderCustomizer) {
        this.podBuilderCustomizer = podBuilderCustomizer;
        return container;
    }

    public T withPodContainerName(String podContainerName) {
        this.podContainerName = podContainerName;
        return container;
    }

    public T withImagePullSecretName(String imagePullSecretName) {
        this.imagePullSecretName = imagePullSecretName;
        return container;
    }

    public T withPortProtocol(String portProtocol) {
        this.portProtocol = portProtocol;
        return container;
    }

    public T waitingFor(@NonNull WaitStrategy waitStrategy) {
        if (waitStrategy instanceof PodEngineAware podEngineAware) {
            podEngineAware.setPod(this);
        }
        return container.waitingFor(waitStrategy);
    }

    public T withPrivilegedMode(boolean privilegedMode) {
        this.privilegedMode = privilegedMode;
        return container;
    }

    public void setStartupTimeout(Duration startTimeout) {
        this.startupTimeout = startTimeout;
    }

    public T withDeletePodOnStop(boolean deletePodOnStop) {
        this.deletePodOnStop = deletePodOnStop;
        return container;
    }

    public boolean isRunning() {
        var podResource = this.getPodResource();
        return podResource != null && RUNNING.equals(podResource.get().getStatus().getPhase());
    }

    public boolean isHealthy() {
        var podResource = this.getPodResource();
        return podResource != null && Set.of(PENDING, RUNNING).contains(podResource.get().getStatus().getPhase());
    }

    public boolean isCreated() {
        var podResource = this.getPodResource();
        return podResource != null && !UNKNOWN.equals(podResource.get().getStatus().getPhase());
    }

    public String getContainerName() {
        return podName;
    }

    public String getContainerId() {
        return getContainerName();
    }

    public InspectContainerResponse getContainerInfo() {
        throw new UnsupportedOperationException("getContainerInfo");
    }

    public Integer getMappedPort(int originalPort) {
        return getLocalPortForward(originalPort).getLocalPort();
    }

    public String getMappedPortHost(int originalPort) {
        return getLocalPortForward(originalPort).getLocalAddress().getHostName();
    }

    public String getLogs() {
        return getPodResource().getLog();
    }

    public String getLogs(OutputFrame.OutputType... types) {
        return getLogs();
    }

    public String getHost() {
        return getLocalPortForwards().values().stream().findFirst()
                .map(lp -> lp.getLocalAddress().getHostName()).orElse("localhost");
    }

    public void start() {
        configure();
        var podBuilder = new PodBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName(podName = podNameGenerator.generatePodName())
                        .build())
                .withSpec(new PodSpecBuilder()
                        .withSecurityContext(new PodSecurityContextBuilder()
                                .withRunAsNonRoot(runAsNonRoot)
                                .withRunAsUser(runAsUser)
                                .withFsGroup(fsGroup)
                                .build())
                        .withImagePullSecrets(new LocalObjectReferenceBuilder()
                                .withName(imagePullSecretName)
                                .build())
                        .withContainers(new ContainerBuilder()
                                .withImagePullPolicy(imagePullPolicy)
                                .withImage(getDockerImageName())
                                .withSecurityContext(new SecurityContextBuilder()
                                        .withRunAsNonRoot(runAsNonRoot)
                                        .withRunAsUser(runAsUser)
                                        .withPrivileged(privilegedMode)
                                        .build())
                                .withName(podContainerName)
                                .withArgs(container.getCommandParts())
                                .withPorts(buildContainerPorts())
                                .withEnv(container.getEnvMap().entrySet().stream().map(e ->
                                        new EnvVarBuilder().withName(e.getKey()).withValue(e.getValue()).build()).toList())
                                .build())
                        .build());

        if (podBuilderCustomizer != null) {
            podBuilder = podBuilderCustomizer.apply(podBuilder);
        }

        var kubernetesClient = this.kubernetesClient;

        var pod = kubernetesClient.resource(podBuilder.build()).create();
        podResource = kubernetesClient.pods().resource(pod);

        waitUntilPodStarted();
        startPortForward();
        waitUntilContainerStarted();
        this.started = true;
    }

    public void stop() {
        for (var localPortForward : localPortForwards.values()) {
            try {
                localPortForward.close();
            } catch (IOException e) {
                log.error("close port forward error, {}", e.getMessage(), e);
            }
        }
        if (deletePodOnStop) {
            var podResource = this.getPodResource();
            if (podResource != null) {
                podResource.delete();
            }
        }
    }

    @SneakyThrows
    public void copyFileToContainer(Transferable transferable, String containerPath) {
        if (getContainerId() == null) {
            throw new IllegalStateException("copyFileToContainer can only be used with created / running container");
        }
        var payload = new ByteArrayOutputStream();
        try (var tar = new TarArchiveOutputStream(payload)) {
            tar.setLongFileMode(LONGFILE_POSIX);
            tar.setBigNumberMode(BIGNUMBER_POSIX);
            transferable.transferTo(tar, containerPath);
        }
        try (var tar = new TarArchiveInputStream(new ByteArrayInputStream(payload.toByteArray()))) {
            for (var entry = tar.getNextTarEntry(); entry != null; entry = tar.getNextTarEntry()) {
                podResource.file(containerPath).upload(tar);
            }
        }
    }

    @SneakyThrows
    public Container.ExecResult execInContainer(Charset outputCharset, String... command) {
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
            var exitCode = exited.get();
            var watchError = execWatch.getError();
            var errOut = new String(watchError.readAllBytes(), outputCharset);
            var watchOutput = execWatch.getOutput();
            var output = new String(watchOutput.readAllBytes(), outputCharset);

            var constructor = Container.ExecResult.class.getDeclaredConstructor(int.class, String.class, String.class);
            constructor.setAccessible(true);

            return constructor.newInstance(exitCode, output, errOut);
        }
    }


    protected void waitUntilPodStarted() {
        var startTime = System.currentTimeMillis();
        var pod = getPodResource().get();
        var status = pod.getStatus();

        while (PENDING.equals(status.getPhase())) {
            var podName = pod.getMetadata().getName();
            var phase = status.getPhase();
            for (var containerStatus : status.getContainerStatuses()) {
                var state = containerStatus.getState();
                var waiting = state.getWaiting();
                if (waiting != null) {
                    var reason = waiting.getReason();
                    if (Set.of("CreateContainerError", "CreateContainerConfigError", "PreCreateHookError",
                            "PreStartHookError", "PostStartHookError").contains(reason)) {
                        var containerName = containerStatus.getName();
                        throw new StartPodException("container error waiting status, container '" + containerName +
                                "', message '" + waiting.getMessage() + "', reason '" + reason + "'",
                                podName, phase);
                    }
                }
            }

            var time = System.currentTimeMillis();
            if (startupTimeout.toMillis() < time - startTime) {
                throw new StartTimeoutException(podName, phase);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                getPodResource().delete();
                throw new StartPodException("interrupted", podName, phase, e);
            }
            status = getPodResource().get().getStatus();
        }

        try {
            if (!RUNNING.equals(status.getPhase())) {
                getPodResource().delete();
                throw new StartPodException("unexpected pod status", pod.getMetadata().getName(), status.getPhase());
            }

            for (var containerStatus : status.getContainerStatuses()) {
                var ready = TRUE.equals(containerStatus.getReady());
                if (!ready) {
                    var terminated = containerStatus.getState().getTerminated();
                    var exitCode = terminated != null ? terminated.getExitCode() : null;
                    var reason = terminated != null ? terminated.getReason() : null;
                    throw new StartPodException("container is not ready, container " + containerStatus.getName() +
                            (exitCode != null ? ", exitCode " + exitCode : "") + (reason != null ? ", reason " + reason : ""),
                            pod.getMetadata().getName(), status.getPhase());
                }
            }
        } catch (StartPodException se) {
            try {
                var logs = getLogs();
                if (!logs.isEmpty()) {
                    log.error("failed pod logs:\n{}", logs);
                }
            } catch (Exception lre) {
                log.error("log reading error", lre);
            }
            throw se;
        }
    }

    protected void waitUntilContainerStarted() {
        invokeContainerMethod("waitUntilContainerStarted");
    }

    protected void configure() {
        invokeContainerMethod("configure");
    }

    @SneakyThrows
    private void invokeContainerMethod(String name) {
        if (container instanceof GenericContainer<?>) {
            var method = GenericContainer.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(container);
        } else {
            log.warn("no method {} in container {}", name, container.getClass());
        }
    }

    protected List<ContainerPort> buildContainerPorts() {
        return container.getExposedPorts().stream().map(port -> new ContainerPortBuilder()
                .withContainerPort(port)
                .withProtocol(portProtocol)
                .build()).toList();
    }

    protected void startPortForward() {
        var exposedPorts = container.getExposedPorts();
        localPortForwards = exposedPorts.stream().collect(toMap(port -> port, port -> {
            var localPortForward = getPodResource().portForward(port);
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
                log.info("port forward local {}:{} to remote {}.{}:{}, ", localAddress.getHostAddress(), localPort,
                        podName, podContainerName, port);
            }
            return localPortForward;
        }));
    }

    protected Map<Integer, LocalPortForward> getLocalPortForwards() {
        return requireNonNull(localPortForwards, "port forwarding has not been started yet");
    }

    protected LocalPortForward getLocalPortForward(int originalPort) {
        return requireNonNull(getLocalPortForwards().get(originalPort), "Requested port (" + originalPort + ") is not mapped");
    }

    protected PodResource getPodResource() {
        if (podResource == null) {
            start();
        }
        return podResource;
    }
}
