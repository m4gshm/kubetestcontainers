package io.github.m4gshm.testcontainers;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.ListOptionsBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.kubernetes.client.RequestConfig;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.internal.OperationSupport;
import io.github.m4gshm.testcontainers.wait.PodLogMessageWaitStrategy;
import io.github.m4gshm.testcontainers.wait.PodPortWaitStrategy;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.jetbrains.annotations.NotNull;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.shaded.com.google.common.hash.Hashing;
import org.testcontainers.utility.MountableFile;
import org.testcontainers.utility.ThrowingFunction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY;
import static com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS;
import static io.github.m4gshm.testcontainers.DefaultPodNameGenerator.newDefaultPodNameGenerator;
import static io.github.m4gshm.testcontainers.PodEngine.Reuse.GLOBAL;
import static io.github.m4gshm.testcontainers.PodEngine.Reuse.SESSION;
import static io.github.m4gshm.testcontainers.KubernetesUtils.PENDING;
import static io.github.m4gshm.testcontainers.KubernetesUtils.RUNNING;
import static io.github.m4gshm.testcontainers.KubernetesUtils.UNKNOWN;
import static io.github.m4gshm.testcontainers.KubernetesUtils.createPod;
import static io.github.m4gshm.testcontainers.KubernetesUtils.escapeQuotes;
import static io.github.m4gshm.testcontainers.KubernetesUtils.getError;
import static io.github.m4gshm.testcontainers.KubernetesUtils.getFirstNotReadyContainer;
import static io.github.m4gshm.testcontainers.KubernetesUtils.resource;
import static io.github.m4gshm.testcontainers.KubernetesUtils.shellQuote;
import static io.github.m4gshm.testcontainers.KubernetesUtils.waitEmptyQueue;
import static java.lang.Boolean.getBoolean;
import static java.lang.String.format;
import static java.lang.reflect.Proxy.newProxyInstance;
import static java.net.InetAddress.getByName;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static lombok.AccessLevel.PROTECTED;
import static org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.BIGNUMBER_POSIX;
import static org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.LONGFILE_POSIX;

/**
 * The core of Kubernetes base {@code Container} implementations.
 *
 * @param <T> a container type
 */
@Slf4j
public class PodEngine<T extends Container<T>> {
    public static final String ORG_TESTCONTAINERS_TYPE = "org.testcontainers.type";
    public static final String ORG_TESTCONTAINERS_NAME = "org.testcontainers.name";
    public static final String KUBECONTAINERS = "kubecontainers";
    private static final String ORG_TESTCONTAINERS_HASH = "org.testcontainers.hash";
    private static final String ORG_TESTCONTAINERS_DELETE_ON_STOP = "org.testcontainers.deleteOnStop";
    private static final String ORG_TESTCONTAINERS_SESSION_LIMITED = "org.testcontainers.sessionLimited";
    private final T container;
    private final Map<Transferable, String> copyToTransferableContainerPathMap = new HashMap<>();
    private final PodBuilderFactory podBuilderFactory = new PodBuilderFactory();
    @Getter
    @Setter
    protected PodNameGenerator podNameGenerator;
    @Getter
    protected JsonMapper jsonMapper = config(new JsonMapper());
    protected KubernetesClientBuilder kubernetesClientBuilder;
    protected KubernetesClient kubernetesClient;
    @Getter
    protected Duration startupTimeout = ofSeconds(60);
    private PodResource pod;
    private boolean localPortForwardEnabled = true;
    private Map<Integer, LocalPortForward> localPortForwards = Map.of();
    @Getter
    private String podName;
    private boolean deletePodOnStop = false;
    @Getter(PROTECTED)
    private boolean started;
    private InetAddress localPortForwardHost;
    @Getter
    private Reuse reuse = SESSION;
    private boolean reused;

    public PodEngine(@NonNull T container) {
        this(container, null);
    }

    public PodEngine(@NonNull T container, String dockerImageName) {
        this(container, dockerImageName, newDefaultPodNameGenerator());
    }

    public PodEngine(@NonNull T container, String dockerImageName,
                     @NonNull PodNameGenerator podNameGenerator) {
        podBuilderFactory.setDockerImageName(dockerImageName);
        this.container = container;
        this.podNameGenerator = podNameGenerator;

        if (container instanceof GenericContainer<?>) {
            var rawWaitStrategy = invokeContainerMethod("getWaitStrategy");
            var replacePodWaiters = replacePodWaiters((WaitStrategy) rawWaitStrategy);
            setFieldValue(GenericContainer.class, container, "waitStrategy", replacePodWaiters);
        }
    }

    private static JsonMapper config(JsonMapper jsonMapper) {
        return jsonMapper.rebuild()
                .enable(SORT_PROPERTIES_ALPHABETICALLY)
                .enable(ORDER_MAP_ENTRIES_BY_KEYS)
                .build();
    }

    @SneakyThrows
    private static <T> T getFieldValue(Class<?> type, Object object, String fieldName) {
        var regExField = type.getDeclaredField(fieldName);
        regExField.setAccessible(true);
        return (T) regExField.get(object);
    }

    @SneakyThrows
    private static void setFieldValue(Class<?> type, Object object, String fieldName, Object value) {
        var regExField = type.getDeclaredField(fieldName);
        regExField.setAccessible(true);
        regExField.set(object, value);
    }

    private static WaitStrategy replacePodWaiters(@NotNull WaitStrategy waitStrategy) {
        return waitStrategy instanceof LogMessageWaitStrategy
                ? new PodLogMessageWaitStrategy(getFieldValue(waitStrategy.getClass(), waitStrategy, "regEx"))
                : waitStrategy instanceof HostPortWaitStrategy
                ? new PodPortWaitStrategy(getFieldValue(waitStrategy.getClass(), waitStrategy, "ports"))
                : waitStrategy;
    }

    private int getRequestTimeout() {
        return kubernetesClient().getConfiguration().getRequestTimeout();
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

    public String getDockerImageName() {
        return podBuilderFactory.getDockerImageName();
    }

    public void setDockerImageName(String dockerImageName) {
        podBuilderFactory.setDockerImageName(dockerImageName);
    }

    public DockerClient getDockerClient() {
        throw new UnsupportedOperationException("getDockerClient");
    }

    public T withImagePullPolicy(ImagePullPolicy imagePullPolicy) {
        if (imagePullPolicy == null) {
            podBuilderFactory.setImagePullPolicy("Never");
        } else if (imagePullPolicy.getClass().equals(PullPolicy.alwaysPull().getClass())) {
            podBuilderFactory.setImagePullPolicy("Always");
        } else {
            podBuilderFactory.setImagePullPolicy("IfNotPresent");
        }
        return container;
    }

    public T withKubernetesClient(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
        return container;
    }

    public T withKubernetesClientBuilder(KubernetesClientBuilder kubernetesClientBuilder) {
        this.kubernetesClientBuilder = kubernetesClientBuilder;
        return container;
    }

    public T withJsonMapper(JsonMapper jsonMapper) {
        this.jsonMapper = config(jsonMapper);
        return container;
    }

    public T withRunAsNonRoot(Boolean runAsNonRoot) {
        podBuilderFactory.setRunAsNonRoot(runAsNonRoot);
        return container;
    }

    public T withRunAsUser(Long runAsUser) {
        podBuilderFactory.setRunAsUser(runAsUser);
        return container;
    }

    public T withRunAsGroup(Long runAsGroup) {
        podBuilderFactory.setRunAsGroup(runAsGroup);
        return container;
    }

    public T withFsGroup(Long fsGroup) {
        podBuilderFactory.setFsGroup(fsGroup);
        return container;
    }

    public T withPodBuilderCustomizer(UnaryOperator<PodBuilder> podBuilderCustomizer) {
        podBuilderFactory.setPodBuilderCustomizer(podBuilderCustomizer);
        return container;
    }

    public T withContainerBuilderCustomizer(UnaryOperator<ContainerBuilder> podContainerBuilderCustomizer) {
        podBuilderFactory.setContainerBuilderCustomizer(podContainerBuilderCustomizer);
        return container;
    }

    public T withPodContainerName(String podContainerName) {
        podBuilderFactory.setPodContainerName(podContainerName);
        return container;
    }

    public T withImagePullSecretName(String imagePullSecretName) {
        podBuilderFactory.setImagePullSecretName(imagePullSecretName);
        return container;
    }

    public T withPortProtocol(String portProtocol) {
        podBuilderFactory.setPortProtocol(portProtocol);
        return container;
    }

    public T localPortForwardEnable() {
        this.localPortForwardEnabled = true;
        return container;
    }

    public T localPortForwardDisable() {
        this.localPortForwardEnabled = false;
        return container;
    }

    public T withPodNameGenerator(PodNameGenerator podNameGenerator) {
        this.podNameGenerator = podNameGenerator;
        return container;
    }


    @SneakyThrows
    public T withLocalPortForwardHost(String host) {
        return withLocalPortForwardHost(getByName(host));
    }

    public T withLocalPortForwardHost(InetAddress localPortForwardHost) {
        this.localPortForwardHost = localPortForwardHost;
        return container;
    }

    @SneakyThrows
    public T waitingFor(@NonNull WaitStrategy waitStrategy) {
        setContainerField("waitStrategy", replacePodWaiters(waitStrategy));
        return container;
    }

    public T withPrivilegedMode(boolean privilegedMode) {
        podBuilderFactory.setPrivilegedMode(privilegedMode);
        return container;
    }

    public T withAllowPrivilegeEscalation(boolean allowPrivilegeEscalation) {
        podBuilderFactory.setAllowPrivilegeEscalation(allowPrivilegeEscalation);
        return container;
    }

    public void setStartupTimeout(Duration startTimeout) {
        this.startupTimeout = startTimeout;
    }

    public T withDeletePodOnStop(boolean deletePodOnStop) {
        this.deletePodOnStop = deletePodOnStop;
        return container;
    }

    public T withCopyFileToContainer(MountableFile mountableFile, String containerPath) {
        return withCopyToContainer(mountableFile, containerPath);
    }

    public T withCopyToContainer(Transferable transferable, String containerPath) {
        copyToTransferableContainerPathMap.put(transferable, containerPath);
        return container;
    }

    public T withCreateContainerCmdModifier(Consumer<CreateContainerCmd> modifier) {
        var createContainerCmd = newCreateContainerCmd();
        modifier.accept(createContainerCmd);
        return container;
    }

    public T withReuse(Reuse reuse) {
        this.reuse = reuse;
        return container;
    }

    @NotNull
    private CreateContainerCmd newCreateContainerCmd() {
        var classLoader = getClass().getClassLoader();
        return (CreateContainerCmd) newProxyInstance(classLoader, new Class[]{CreateContainerCmd.class}, (
                proxy, method, args
        ) -> {
            var methodName = method.getName();
            return switch (methodName) {
                case "withEntrypoint" -> {
                    var firstArg = args[0];
                    if (firstArg instanceof String[] strings) {
                        podBuilderFactory.setEntryPoint(List.of(strings));
                    } else if (firstArg instanceof List<?> list) {
                        podBuilderFactory.setEntryPoint(list.stream().map(String::valueOf).toList());
                    }
                    yield null;
                }
                default -> throw new UnsupportedOperationException(methodName);
            };
        });
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
        return getPod().flatMap(pod -> pod.getSpec().getContainers().stream().filter(c -> containerName.equals(c.getName()))
                .findFirst()).orElseThrow(() -> new IllegalStateException("container '" + containerName + "' not found"));
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
        return localPortForwardEnabled ? getLocalPortForwards().values().stream().findFirst()
                .map(LocalPortForward::getLocalAddress)
                .map(localAddress -> localAddress instanceof Inet6Address
                        ? "[" + localAddress.getHostName() + "]"
                        : localAddress.getHostName())
                .orElse("localhost") : getPodIP();
    }

    public String getPodIP() {
        return getPod().map(pod -> pod.getStatus().getHostIP()).orElse(null);
    }

    public void start() {
        configure();

        var podName = podNameGenerator.generatePodName(getDockerImageName());
        this.podName = podName;

        podBuilderFactory.setArgs(container.getCommandParts());
        podBuilderFactory.setVars(container.getEnvMap().entrySet().stream().map(e ->
                new EnvVarBuilder().withName(e.getKey()).withValue(e.getValue()).build()).toList());
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

        var containerInfo = new InspectContainerResponse();

        //todo fill containerInfo
        containerIsStarting(containerInfo, false);

        waitUntilPodStarted();
        if (localPortForwardEnabled) {
            startPortForward();
        }
        waitUntilContainerStarted();
        this.started = true;

        copyToTransferableContainerPathMap.forEach(this::copyFileToContainer);

        //todo fill containerInfo
        containerIsStarted(containerInfo, false);
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

    public void stop() {
        for (var localPortForward : localPortForwards.values()) {
            try {
                localPortForward.close();
            } catch (IOException e) {
                log.error("close port forward error, {}", e.getMessage(), e);
            }
        }
        if (!reused) {
            if (deletePodOnStop) {
                var podResource = this.getPodResource();
                if (podResource != null) {
                    Pod pod = podResource.get();
                    log.debug("delete pod on stop {}", pod != null ? pod.getMetadata().getName() : "'Not Found'");
                    podResource.delete();
                }
            }
        }
    }

    @SneakyThrows
    public void copyFileToContainer(Transferable transferable, String containerPath) {
        assertPodRunning("copyFileToContainer");
        var payload = new ByteArrayOutputStream();
        try (var tar = new TarArchiveOutputStream(payload)) {
            tar.setLongFileMode(LONGFILE_POSIX);
            tar.setBigNumberMode(BIGNUMBER_POSIX);
            transferable.transferTo(tar, containerPath);
        }

        uploadTmpTar(payload.toByteArray());
        log.info("file {} copied to pod {}", containerPath, pod.get().getMetadata().getName());
    }

    @SneakyThrows
    public <T> T copyFileFromContainer(String containerPath, ThrowingFunction<InputStream, T> function) {
        assertPodRunning("copyFileFromContainer");
        try (var inputStream = pod.file(containerPath).read()) {
            return function.apply(inputStream);
        }
    }

    @SneakyThrows
    public void uploadTmpTar(byte[] payload) {
        var tmpDir = "/tmp";
        var tarName = tmpDir + "/" + this.podName + ".tar";
        log.debug("tar uploading {}", tarName);

        var escapedTarPath = escapeQuotes(tarName);
        try (var exec = pod.terminateOnError().exec("touch", escapedTarPath)) {
            waitEmptyQueue(exec);
        }

        uploadStdIn(payload, escapedTarPath);
//        uploadBase64(payload, escapedTarPath);

        var unpackDir = "/";
        var extractTarCmd = format("mkdir -p %1$s; tar -C %1$s -xmf %2$s; e=$?; rm %2$s; exit $e",
                shellQuote(unpackDir), tarName);

        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        try (var exec = pod.redirectingInput().writingOutput(out).writingError(err).exec("sh", "-c", extractTarCmd)) {
            waitEmptyQueue(exec);
            var exitedCode = exec.exitCode();
            var exitCode = exitedCode.get(getRequestTimeout(), MILLISECONDS);
            ;
            var unpacked = exitCode == 0;
            if (!unpacked) {
                throw new UploadFileException("unpack temporary tar " + tarName +
                        ", exit code " + exitCode +
                        ", out '" + out.toString(UTF_8) + "'" +
                        ", errOut '" + err.toString(UTF_8) + "'");
            } else {
                log.debug("upload tar -> {}", out.toString(UTF_8));
            }
        }
    }

    @SneakyThrows
    private void uploadStdIn(byte[] payload, String escapedTarPath) {
        try (var exec = pod.redirectingInput().terminateOnError().exec("cp", "/dev/stdin", escapedTarPath)) {
            var input = exec.getInput();
            input.write(payload);
            input.flush();
            waitEmptyQueue(exec);
            checkSize(escapedTarPath, payload.length);
        }
    }

    @SneakyThrows
    private void uploadBase64(byte[] payload, String escapedTarPath) {
        var encoded = Base64.getEncoder().encodeToString(payload);
        try (var uploadWatch = pod.terminateOnError().exec("sh", "-c", "echo " + encoded + "| base64 -d >" + "'" + escapedTarPath + "'")) {
            var code = uploadWatch.exitCode().get(getRequestConfig().getRequestTimeout(), MILLISECONDS);
            if (code != 0) {
                throw new UploadFileException("Unexpected exit code " + code + ", file '" + escapedTarPath + "'");
            }
            checkSize(escapedTarPath, payload.length);
        }
    }

    private RequestConfig getRequestConfig() {
        return ((OperationSupport) pod).getRequestConfig();
    }

    @SneakyThrows
    private void checkSize(String filePath, long expected) {
        var size = getSize(filePath);
        if (size != expected) {
            Thread.sleep(100);
            size = getSize(filePath);
        }
        if (size != expected) {
            throw new UploadFileException("Unexpected file size " + size + ", expected " + expected + ", file '" + filePath + "'");
        }
    }

    @SneakyThrows
    private long getSize(String filePath) {
        var byteCount = new ByteArrayOutputStream();
        try (var exec = pod.writingOutput(byteCount).terminateOnError().exec("sh", "-c", "wc -c < " + filePath)) {
            var exitCode = exec.exitCode().get(getRequestTimeout(), MILLISECONDS);
            var remoteSizeRaw = byteCount.toString(UTF_8).trim();
            waitEmptyQueue(exec);
            return Integer.parseInt(remoteSizeRaw);
        }
    }

    @SneakyThrows
    public boolean removeFile(String tarName) {
        try (var exec = waitEmptyQueue(pod.redirectingError().exec("rm", tarName))) {
            var exitCode = exec.exitCode().get(getRequestTimeout(), MILLISECONDS);
            var deleted = exitCode != 0;
            if (deleted) {
                log.warn("deleting of temporary file {} finished with unexpected code {}, errOut: {}",
                        tarName, exitCode, getError(exec));
            }
            return deleted;
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

        try (var execWatch = pod.redirectingOutput().redirectingError().exec(command)) {
            var exited = execWatch.exitCode();
            var exitCode = exited.get(getRequestTimeout(), MILLISECONDS);
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
            var constructor = Container.ExecResult.class.getDeclaredConstructor(int.class, String.class, String.class);
            constructor.setAccessible(true);

            return constructor.newInstance(exitCode, output, errOut);
        }
    }

    public Container.ExecResult execInContainerWithUser(String user, String... command) {
        return execInContainerWithUser(UTF_8, user, command);
    }

    public Container.ExecResult execInContainerWithUser(Charset outputCharset, String user, String... command) {
        throw new UnsupportedOperationException("execInContainerWithUser");
    }

    public T withLabel(String key, String value) {
        if (key.startsWith("org.testcontainers")) {
            throw new IllegalArgumentException("The org.testcontainers namespace is reserved for interal use");
        }
        podBuilderFactory.getLabels().put(key, value);
        return container;
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

    protected void waitUntilContainerStarted() {
        invokeContainerMethod("waitUntilContainerStarted");
    }

    protected void configure() {
        invokeContainerMethod("configure");
    }

    @SneakyThrows
    protected void containerIsStarting(InspectContainerResponse containerInfo, boolean reused) {
        var containerIsStarted = GenericContainer.class.getDeclaredMethod(
                "containerIsStarting", InspectContainerResponse.class, boolean.class
        );
        containerIsStarted.setAccessible(true);
        containerIsStarted.invoke(container, containerInfo, reused);
    }

    @SneakyThrows
    protected void containerIsStarted(InspectContainerResponse containerInfo, boolean reused) {
        var containerIsStarted = GenericContainer.class.getDeclaredMethod(
                "containerIsStarted", InspectContainerResponse.class, boolean.class
        );
        containerIsStarted.setAccessible(true);
        containerIsStarted.invoke(container, containerInfo, reused);
    }

    @SneakyThrows
    private Object invokeContainerMethod(String name) {
        if (container instanceof GenericContainer<?>) {
            var method = GenericContainer.class.getDeclaredMethod(name);
            method.setAccessible(true);
            return method.invoke(container);
        } else {
            throw new IllegalStateException("no method '" + name + "' in container " + container.getClass());
        }
    }

    @SneakyThrows
    private void setContainerField(String name, Object value) {
        if (container instanceof GenericContainer<?>) {
            var field = GenericContainer.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(container, value);
        } else {
            throw new IllegalStateException("no field '" + name + "' in container " + container.getClass());
        }
    }

    protected void startPortForward() {
        localPortForwards = KubernetesUtils.startPortForward(getPodResource(), localPortForwardHost, getExposedPorts());
    }

    protected List<Integer> getExposedPorts() {
        return container.getExposedPorts();
    }

    protected Map<Integer, LocalPortForward> getLocalPortForwards() {
        return requireNonNull(localPortForwards, "port forwarding has not been started yet");
    }

    protected LocalPortForward getLocalPortForward(int originalPort) {
        return requireNonNull(getLocalPortForwards().get(originalPort), "Requested port (" + originalPort + ") is not mapped");
    }

    protected PodResource getPodResource() {
        if (pod == null) {
            start();
        }
        return pod;
    }

    protected Optional<Pod> getPod() {
        return ofNullable(getPodResource().get());
    }

    private void assertPodRunning(String funcName) {
        if (getContainerId() == null) {
            throw new IllegalStateException(funcName + " can only be used with running pod");
        }
    }

    public void addHostPort(Integer port, Integer hostPort) {
        podBuilderFactory.addHostPort(port, hostPort);
    }

    public enum Reuse {
        NEVER, SESSION, GLOBAL;
    }

}
