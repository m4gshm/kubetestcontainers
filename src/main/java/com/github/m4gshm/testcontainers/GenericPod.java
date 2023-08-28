package com.github.m4gshm.testcontainers;

import com.github.dockerjava.api.command.InspectContainerResponse;
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
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

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
    private boolean runAsNonRoot;
    @Getter
    private long runAsUser;
    @Getter
    private long fsGroup;
    @Getter(PROTECTED)
    private PodResource podResource;
    private Map<Integer, LocalPortForward> localPortForwards;
    @Getter
    private String imagePullSecretName;
    @Getter
    private UnaryOperator<PodBuilder> podBuilderCustomizer;
    @Getter
    private String podContainerName = "main";
    private boolean privilegedMode;
    private String podName;
    private String imagePullPolicy = "Always";
    @Getter(PROTECTED)
    private boolean started;
    @Getter
    private Duration startTimeout = Duration.ofSeconds(60);
    @Getter
    private String portProtocol = "TCP";

    public GenericPod(@NonNull String dockerImageName) {
        this(dockerImageName, new KubernetesClientBuilder().build(), new DefaultPodNameGenerator());
    }

    public GenericPod(@NonNull String dockerImageName, @NonNull KubernetesClient kubernetesClient,
                      @NonNull PodNameGenerator podNameGenerator) {
        super(DockerImageName.parse(dockerImageName));
        this.kubernetesClient = kubernetesClient;
        this.podNameGenerator = podNameGenerator;
        this.dockerImageName = dockerImageName;
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

    public T withRunAsNonRoot(boolean runAsNonRoot) {
        this.runAsNonRoot = runAsNonRoot;
        return self();
    }

    public T withRunAsUser(long runAsUser) {
        this.runAsUser = runAsUser;
        return self();
    }

    public T withFsGroup(long fsGroup) {
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
    public T withPrivilegedMode(boolean privilegedMode) {
        this.privilegedMode = privilegedMode;
        return self();
    }

    public T withStartTimeout(Duration startTimeout) {
        this.startTimeout = startTimeout;
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
        var localPortForward = this.localPortForwards.get(originalPort);
        if (localPortForward == null) {
            throw new IllegalArgumentException("Requested port (" + originalPort + ") is not mapped");
        }
        return localPortForward.getLocalPort();
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

        waitReady();
        startPortForward();
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
        var podResource = this.podResource;
        if (podResource != null) {
            podResource.delete();
        }
    }

    protected void waitReady() {
        var startTime = System.currentTimeMillis();
        var pod = podResource.get();
        var status = pod.getStatus();

        while (PENDING.equals(status.getPhase())) {
            var podName = pod.getMetadata().getName();
            var phase = status.getPhase();
            for (var containerStatus : status.getContainerStatuses()) {
                var state = containerStatus.getState();
                var waiting = state.getWaiting();
                var reason = waiting.getReason();
                if (Set.of("CreateContainerError", "CreateContainerConfigError", "PreCreateHookError",
                        "PreStartHookError", "PostStartHookError").contains(reason)) {
                    var containerName = containerStatus.getName();
                    throw new StartPodException("container error waiting status, container '" + containerName +
                            "', message '" + waiting.getMessage() + "', reason '" + reason + "'",
                            podName, phase);
                }
            }

            var time = System.currentTimeMillis();
            if (startTimeout.toMillis() < time - startTime) {
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

        if (!RUNNING.equals(status.getPhase())) {
            podResource.delete();
            throw new UnexpectedPodStatusException(pod.getMetadata().getName(), status.getPhase());
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
            log.info("port forward local {}:{} to remote port {}", localAddress.getHostAddress(), localPort, port);
            return localPortForward;
        }));

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
                ", startTimeout=" + startTimeout +
                ", portProtocol='" + portProtocol + '\'' +
                '}';
    }
}
