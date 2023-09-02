package com.github.m4gshm.testcontainers;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.m4gshm.testcontainers.wait.PodPortWaitStrategy;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.kubernetes.client.dsl.PodResource;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
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

@Slf4j
public abstract class GenericPod<T extends GenericPod<T>> extends JdbcDatabaseContainer<T> {
    public static final String RUNNING = "Running";
    public static final String PENDING = "Pending";
    public static final String UNKNOWN = "Unknown";
    protected final PodNameGenerator podNameGenerator;
    protected final String dockerImageName;
    protected KubernetesClient kubernetesClient;
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
    @Getter(PROTECTED)
    private PodResource podResource;
    private Map<Integer, LocalPortForward> localPortForwards;
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

    public GenericPod(@NonNull String dockerImageName) {
        this(dockerImageName, new KubernetesClientBuilder().build(), new DefaultPodNameGenerator());
    }

    public GenericPod(@NonNull String dockerImageName, @NonNull KubernetesClient kubernetesClient,
                      @NonNull PodNameGenerator podNameGenerator) {
        super(DockerImageName.parse(dockerImageName));
        this.kubernetesClient = kubernetesClient;
        this.podNameGenerator = podNameGenerator;
        this.dockerImageName = dockerImageName;
        this.waitStrategy = new PodPortWaitStrategy(this).withStartupTimeout(startupTimeout);
    }

    @Override
    public DockerClient getDockerClient() {
        throw new UnsupportedOperationException("getDockerClient");
    }

    @Override
    public T withImagePullPolicy(ImagePullPolicy imagePullPolicy) {
        if (imagePullPolicy == null) {
            this.imagePullPolicy = "Never";
        } else if (imagePullPolicy.getClass().equals(PullPolicy.alwaysPull().getClass())) {
            this.imagePullPolicy = "Always";
        } else {
            this.imagePullPolicy = "IfNotPresent";
        }
        return self();
    }

    public T withRunAsNonRoot(Boolean runAsNonRoot) {
        this.runAsNonRoot = runAsNonRoot;
        return self();
    }

    public T withRunAsUser(Long runAsUser) {
        this.runAsUser = runAsUser;
        return self();
    }

    public T withFsGroup(Long fsGroup) {
        this.fsGroup = fsGroup;
        return self();
    }

    public T withPodBuilderCustomizer(UnaryOperator<PodBuilder> podBuilderCustomizer) {
        this.podBuilderCustomizer = podBuilderCustomizer;
        return self();
    }

    public T withPodContainerName(String podContainerName) {
        this.podContainerName = podContainerName;
        return self();
    }

    public T withImagePullSecretName(String imagePullSecretName) {
        this.imagePullSecretName = imagePullSecretName;
        return self();
    }

    public T withPortProtocol(String portProtocol) {
        this.portProtocol = portProtocol;
        return self();
    }

    @Override
    public T waitingFor(@NonNull WaitStrategy waitStrategy) {
        if (waitStrategy instanceof PodAware podAware) {
            podAware.withPod(this);
        }
        return super.waitingFor(waitStrategy);
    }

    @Override
    public T withPrivilegedMode(boolean privilegedMode) {
        this.privilegedMode = privilegedMode;
        return self();
    }

    public T withStartupTimeout(Duration startTimeout) {
        this.startupTimeout = startTimeout;
        this.waitStrategy = this.waitStrategy.withStartupTimeout(startupTimeout);
        return self();
    }

    public T withDeletePodOnStop(boolean deletePodOnStop) {
        this.deletePodOnStop = deletePodOnStop;
        return self();
    }

    @Override
    public @NotNull String getDockerImageName() {
        return dockerImageName;
    }

    @Override
    public boolean isRunning() {
        var podResource = this.podResource;
        return podResource != null && RUNNING.equals(podResource.get().getStatus().getPhase());
    }

    @Override
    public boolean isHealthy() {
        var podResource = this.podResource;
        return podResource != null && Set.of(PENDING, RUNNING).contains(podResource.get().getStatus().getPhase());
    }

    @Override
    public boolean isCreated() {
        var podResource = this.podResource;
        return podResource != null && !UNKNOWN.equals(podResource.get().getStatus().getPhase());
    }

    @Override
    public String getContainerName() {
        return podName;
    }

    @Override
    public String getContainerId() {
        return podName;
    }

    @Override
    public InspectContainerResponse getContainerInfo() {
        throw new UnsupportedOperationException("getContainerInfo");
    }

    @Override
    public Integer getMappedPort(int originalPort) {
        return getLocalPortForward(originalPort).getLocalPort();
    }

    public String getMappedPortHost(int originalPort) {
        return getLocalPortForward(originalPort).getLocalAddress().getHostName();
    }

    @Override
    public String getLogs() {
        return podResource.getLog();
    }

    @Override
    public String getLogs(OutputFrame.OutputType... types) {
        return getLogs();
    }

    @Override
    public String getHost() {
        return getLocalPortForwards().values().stream().findFirst()
                .map(lp -> lp.getLocalAddress().getHostName()).orElse("localhost");
    }

    @Override
    public void start() {
        var podName = podNameGenerator.generatePodName();
        this.podName = podName;
        var podBuilder = new PodBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName(podName)
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
                                .withImage(dockerImageName)
                                .withSecurityContext(new SecurityContextBuilder()
                                        .withRunAsNonRoot(runAsNonRoot)
                                        .withRunAsUser(runAsUser)
                                        .withPrivileged(privilegedMode)
                                        .build())
                                .withName(podContainerName)
                                .withArgs(getCommandParts())
                                .withPorts(buildContainerPorts())
                                .withEnv(getEnvVars())
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
        runInitScriptIfRequired();
        this.started = true;
    }

    @Override
    public void stop() {
        for (var localPortForward : localPortForwards.values()) {
            try {
                localPortForward.close();
            } catch (IOException e) {
                log.error("close port forward error, {}", e.getMessage(), e);
            }
        }
        if (deletePodOnStop) {
            var podResource = this.podResource;
            if (podResource != null) {
                podResource.delete();
            }
        }
    }

    protected void waitUntilPodStarted() {
        var startTime = System.currentTimeMillis();
        var pod = podResource.get();
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
                podResource.delete();
                throw new StartPodException("interrupted", podName, phase, e);
            }
            status = podResource.get().getStatus();
        }

        try {
            if (!RUNNING.equals(status.getPhase())) {
                podResource.delete();
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
                    logger().error("failed pod logs:\n{}", logs);
                }
            } catch (Exception lre) {
                log.error("log reading error", lre);
            }
            throw se;
        }
    }

    protected List<ContainerPort> buildContainerPorts() {
        return getExposedPorts().stream().map(port -> new ContainerPortBuilder()
                .withContainerPort(port)
                .withProtocol(portProtocol)
                .build()).toList();
    }

    protected void startPortForward() {
        var exposedPorts = getExposedPorts();
        localPortForwards = exposedPorts.stream().collect(toMap(port -> port, port -> {
            var localPortForward = podResource.portForward(port);
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
                log.info("port forward local {}:{} to remote port {}, ", localAddress.getHostAddress(), localPort, port);
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

    protected abstract List<EnvVar> getEnvVars();

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" +
                "dockerImageName='" + dockerImageName + '\'' +
                ", runAsNonRoot=" + runAsNonRoot +
                ", runAsUser=" + runAsUser +
                ", fsGroup=" + fsGroup +
                ", imagePullSecretName='" + imagePullSecretName + '\'' +
                ", podContainerName='" + podContainerName + '\'' +
                ", privilegedMode=" + privilegedMode +
                ", podName='" + podName + '\'' +
                ", imagePullPolicy='" + imagePullPolicy + '\'' +
                ", started=" + started +
                ", startTimeout=" + startupTimeout +
                ", portProtocol='" + portProtocol + '\'' +
                '}';
    }
}
