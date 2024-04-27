package io.github.m4gshm.testcontainers;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.ListOptionsBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.kubernetes.client.dsl.PodResource;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.shaded.com.google.common.hash.Hashing;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.github.m4gshm.testcontainers.AbstractPod.Reuse.GLOBAL;
import static io.github.m4gshm.testcontainers.AbstractPod.Reuse.SESSION;
import static io.github.m4gshm.testcontainers.KubernetesUtils.PENDING;
import static io.github.m4gshm.testcontainers.KubernetesUtils.RUNNING;
import static io.github.m4gshm.testcontainers.KubernetesUtils.UNKNOWN;
import static io.github.m4gshm.testcontainers.KubernetesUtils.createPod;
import static io.github.m4gshm.testcontainers.KubernetesUtils.getFirstNotReadyContainer;
import static io.github.m4gshm.testcontainers.KubernetesUtils.resource;
import static io.github.m4gshm.testcontainers.PodContainerUtils.config;
import static java.lang.Boolean.getBoolean;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static lombok.AccessLevel.PROTECTED;

/**
 * The base pod start/stop api implementation.
 */
@Slf4j
public abstract class AbstractPod {
    public static final String ORG_TESTCONTAINERS_TYPE = "org.testcontainers.type";
    public static final String ORG_TESTCONTAINERS_NAME = "org.testcontainers.name";
    public static final String KUBECONTAINERS = "kubecontainers";
    private static final String ORG_TESTCONTAINERS_HASH = "org.testcontainers.hash";
    private static final String ORG_TESTCONTAINERS_DELETE_ON_STOP = "org.testcontainers.deleteOnStop";
    private static final String ORG_TESTCONTAINERS_SESSION_LIMITED = "org.testcontainers.sessionLimited";
    protected final PodBuilderFactory podBuilderFactory = new PodBuilderFactory();
    @Getter
    @Setter
    protected PodNameGenerator podNameGenerator;
    @Getter
    protected JsonMapper jsonMapper = config(new JsonMapper());
    protected KubernetesClientBuilder kubernetesClientBuilder;
    protected KubernetesClient kubernetesClient;
    @Getter
    @Setter
    protected Duration startupTimeout = ofSeconds(60);
    protected PodResource pod;
    protected boolean localPortForwardEnabled = true;
    protected Map<Integer, LocalPortForward> localPortForwards = Map.of();
    @Getter
    protected String podName;
    protected boolean deletePodOnStop = false;
    protected boolean deletePodOnError = false;
    @Getter(PROTECTED)
    protected boolean started;
    protected InetAddress localPortForwardHost;
    @Getter
    protected Reuse reuse = SESSION;
    protected boolean reused;

    public AbstractPod(@NonNull PodNameGenerator podNameGenerator) {
        this.podNameGenerator = podNameGenerator;
    }

    protected abstract void configure();

    protected abstract List<EnvVar> getEnvVars();

    protected abstract String[] getCommandParts();

    protected abstract void waitUntilContainerStarted();

    public void start() {
        log.trace("pre starting configure pod");
        configure();

        var podName = podNameGenerator.generatePodName(getDockerImageName());
        log.debug("starting pod name '{}'", podName);
        this.podName = podName;

        podBuilderFactory.setArgs(getCommandParts());
        podBuilderFactory.setVars(getEnvVars());
        podBuilderFactory.addLabel(ORG_TESTCONTAINERS_TYPE, KUBECONTAINERS);
        getExposedPorts().forEach(podBuilderFactory::addPort);
        var podBuilder = podBuilderFactory.newPodBuilder();

        var hash = hash(podBuilder.build());
        var session = Session.instance();
        podBuilder
                .editMetadata()
                .withName(podName)
                .addToLabels(Map.of(
                        ORG_TESTCONTAINERS_NAME, podName,
                        ORG_TESTCONTAINERS_HASH, hash,
                        ORG_TESTCONTAINERS_SESSION_LIMITED, String.valueOf(reuse == SESSION),
                        ORG_TESTCONTAINERS_DELETE_ON_STOP, String.valueOf(deletePodOnStop)
                ))
                .endMetadata();

        var kubernetesClient = kubernetesClient();
        final PodResource findPod;
        var reuse = this.reuse;
        if (reuse == SESSION) {
            findPod = session.find(hash);
        } else if (reuse == GLOBAL) {
            var options = new ListOptionsBuilder().withLabelSelector(ORG_TESTCONTAINERS_HASH).build();
            var podList = kubernetesClient.pods().list(options);
            findPod = podList.getItems().stream().filter(p -> {
                                var labels = p.getMetadata().getLabels();
                                var deleteOnStop = getBoolean(labels.get(ORG_TESTCONTAINERS_DELETE_ON_STOP));
                                var sessionLimited = getBoolean(labels.get(ORG_TESTCONTAINERS_SESSION_LIMITED));
                                return KubernetesUtils.isRunning(p)
                                        && hash.equals(labels.get(ORG_TESTCONTAINERS_HASH))
                                        && !(deleteOnStop || sessionLimited)
                                        && getFirstNotReadyContainer(p.getStatus()) == null;
                            }
                    )
                    .map(p -> resource(kubernetesClient, p))
                    .findFirst().orElse(null);
        } else {
            findPod = null;
        }

        final boolean reused;
        if (findPod != null) {
            var pod = findPod.get();
            if (pod == null) {
                reused = false;
                log.debug("reusable pod doesn't exists, need to recreate");
            } else {
                reused = true;
                var metadata = pod.getMetadata();
                var uid = metadata.getUid();
                var name = metadata.getName();
                log.info("reuse first appropriated pod '{}' (uid {}), phase {}, reuse type {}",
                        name, uid, pod.getStatus().getPhase(), reuse);
            }
        } else {
            reused = false;
        }

        this.reused = reused;

        final PodResource podResource;
        if (reused) {
            podResource = findPod;
        } else {
            podResource = createPod(kubernetesClient, podBuilder.build());
            if (!deletePodOnStop && reuse == SESSION) {
                session.registerPodForDelayedDeleting(hash, podResource);
            }
        }
        this.pod = podResource;

        doBeforeStart();
        log.trace("wait until pod started, {}", podName);
        waitUntilPodStarted();
        if (localPortForwardEnabled) {
            startPortForward();
        }
        log.trace("wait until container started, {}", podName);
        waitUntilContainerStarted();
        this.started = true;
        doAfterStart();
        log.debug("pod has been started, {}", podName);
    }

    public void stop() {
        for (var localPortForward : localPortForwards.values()) {
            try {
                localPortForward.close();
            } catch (IOException e) {
                log.error("close port forward error, {}", e.getMessage(), e);
            }
        }
        if (!reused && deletePodOnStop) {
            var podResource = getPodResource();
            if (podResource != null) {
                var pod = podResource.get();
                log.debug("delete pod on stop {}", pod != null ? pod.getMetadata().getName() : "'Not Found'");
                podResource.delete();
            }
        }
    }

    public String getDockerImageName() {
        return podBuilderFactory.getDockerImageName();
    }

    protected void doBeforeStart() {
    }

    protected void doAfterStart() {
    }

    protected KubernetesClient kubernetesClient() {
        var kubernetesClient = this.kubernetesClient;
        if (kubernetesClient == null) {
            var kubernetesClientBuilder = this.kubernetesClientBuilder;
            if (kubernetesClientBuilder == null) {
                this.kubernetesClientBuilder = kubernetesClientBuilder = new KubernetesClientBuilder();
            }
            this.kubernetesClient = kubernetesClient = kubernetesClientBuilder.build();
        }
        return kubernetesClient;
    }

    @SneakyThrows
    protected String hash(Pod pod) {
        return Hashing.sha1().hashBytes(jsonMapper.writeValueAsBytes(pod)).toString();
    }

    protected void waitUntilPodStarted() {
        var startTime = System.currentTimeMillis();
        var pod = getPodResource().get();

        if (pod == null) {
            throw new StartPodException("not found", podName, "waitUntilPodStarted");
        }

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
                deleteOnError();
                throw new StartPodException("interrupted", podName, phase, e);
            }
            status = getPodResource().get().getStatus();
        }

        try {
            if (!RUNNING.equals(status.getPhase())) {
                deleteOnError();
                throw new StartPodException("unexpected pod status", pod.getMetadata().getName(), status.getPhase());
            }

            var firstNotReadyContainer = getFirstNotReadyContainer(status);

            if (firstNotReadyContainer != null) {
                var terminated = firstNotReadyContainer.getState().getTerminated();
                var exitCode = terminated != null ? terminated.getExitCode() : null;
                var reason = terminated != null ? terminated.getReason() : null;
                throw new StartPodException("container is not ready, container " + firstNotReadyContainer.getName() +
                        (exitCode != null ? ", exitCode " + exitCode : "") + (reason != null ? ", reason " + reason : ""),
                        pod.getMetadata().getName(), status.getPhase());
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

    private void deleteOnError() {
        if (!reused && deletePodOnError) {
            var podResource = getPodResource();
            if (podResource != null) {
                var pod = podResource.get();
                log.debug("delete pod on error {}", pod != null ? pod.getMetadata().getName() : "'Not Found'");
                podResource.delete();
            }
        }
    }

    public String getLogs() {
        return getPodResource().getLog();
    }

    public String getLogs(OutputFrame.OutputType... types) {
        return getLogs();
    }

    protected void startPortForward() {
        var localPortForwardHost = this.localPortForwardHost;
        var ports = getExposedPorts();
        log.debug("starting port forwarding, inetAddress {}, ports {}", localPortForwardHost, ports);
        localPortForwards = KubernetesUtils.startPortForward(getPodResource(), localPortForwardHost, ports);
    }

    protected abstract List<Integer> getExposedPorts();

    protected PodResource getPodResource() {
        if (pod == null) {
            start();
        }
        return pod;
    }

    protected Optional<Pod> getPod() {
        return ofNullable(getPodResource().get());
    }

    protected void assertPodRunning(String funcName) {
        if (!started) {
            throw new IllegalStateException(funcName + " can only be used with running pod");
        }
    }

    protected int getRequestTimeout() {
        return kubernetesClient().getConfiguration().getRequestTimeout();
    }

    public void addPort(Integer port, Integer hostPort) {
        podBuilderFactory.addPort(port, hostPort);
    }

    public boolean isRunning() {
        return getPod().map(KubernetesUtils::isRunning).orElse(false);
    }

    public boolean isHealthy() {
        return getPod().map(pod -> Set.of(PENDING, RUNNING).contains(pod.getStatus().getPhase())).orElse(false);
    }

    public boolean isCreated() {
        return getPod().map(pod -> !UNKNOWN.equals(pod.getStatus().getPhase())).orElse(false);
    }

    public Integer getMappedPort(int originalPort) {
        var port = (Integer) originalPort;
        return localPortForwardEnabled
                ? getLocalPortForward(originalPort).getLocalPort()
                : ofNullable(getContainerPort(port)).map(ContainerPort::getHostPort).orElse(port);
    }

    protected ContainerPort getContainerPort(Integer port) {
        return getContainer().getPorts().stream().filter(p -> port.equals(p.getContainerPort()))
                .findFirst().orElse(null);
    }

    protected io.fabric8.kubernetes.api.model.Container getContainer() {
        var containerName = podBuilderFactory.getPodContainerName();
        var containers = getPod().map(pod -> pod.getSpec().getContainers()).orElse(List.of());
        return containers.stream().filter(c -> containerName.equals(c.getName())).findFirst().orElseThrow(
                () -> new IllegalStateException("container not found (" + containerName + "), available " +
                        containers.stream().map(Container::getName).toList()));
    }

    public String getMappedPortHost(int originalPort) {
        return getLocalPortForward(originalPort).getLocalAddress().getHostName();
    }

    public String getHost() {
        return localPortForwardEnabled ? getLocalPortForwards().values().stream().findFirst()
                .map(LocalPortForward::getLocalAddress)
                .map(localAddress -> localAddress instanceof Inet6Address
                        ? "[" + localAddress.getHostName() + "]"
                        : localAddress.getHostName())
                .orElse("localhost") : getPodIP();
    }

    protected Map<Integer, LocalPortForward> getLocalPortForwards() {
        return requireNonNull(localPortForwards, "port forwarding has not been started yet");
    }

    protected LocalPortForward getLocalPortForward(int originalPort) {
        return requireNonNull(getLocalPortForwards().get(originalPort), "Requested port (" + originalPort + ") is not mapped");
    }

    public String getPodIP() {
        return getPod().map(pod -> pod.getStatus().getHostIP()).orElse(null);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + toStringFields() + '}';
    }

    public String toStringFields() {
        return ", dockerImageName='" + podBuilderFactory.getDockerImageName() + '\'' +
                ", runAsNonRoot=" + podBuilderFactory.getRunAsNonRoot() +
                ", runAsUser=" + podBuilderFactory.getRunAsUser() +
                ", fsGroup=" + podBuilderFactory.getFsGroup() +
                ", privilegedMode=" + podBuilderFactory.isPrivilegedMode() +
                ", imagePullPolicy='" + podBuilderFactory.getImagePullPolicy() + '\'' +
                ", startupTimeout=" + startupTimeout +
                ", portProtocol='" + podBuilderFactory.getPortProtocol() + '\'' +
                ", localPortForwards=" + localPortForwards +
                ", imagePullSecretName='" + podBuilderFactory.getImagePullSecretName() + '\'' +
                ", podBuilderCustomizer=" + podBuilderFactory.getPodBuilderCustomizer() +
                ", podContainerName='" + podBuilderFactory.getPodContainerName() + '\'' +
                ", podName='" + podName + '\'' +
                ", deletePodOnStop=" + deletePodOnStop +
                ", started=" + started;
    }

    public enum Reuse {
        NEVER, SESSION, GLOBAL;
    }

}
