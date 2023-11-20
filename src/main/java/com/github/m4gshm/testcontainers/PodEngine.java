package com.github.m4gshm.testcontainers;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.m4gshm.testcontainers.wait.PodLogMessageWaitStrategy;
import com.github.m4gshm.testcontainers.wait.PodPortWaitStrategy;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.ListOptionsBuilder;
import io.fabric8.kubernetes.api.model.LocalObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodSecurityContextBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.SecurityContextBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.kubernetes.client.RequestConfig;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.internal.ExecWebSocketListener;
import io.fabric8.kubernetes.client.dsl.internal.OperationSupport;
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
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY;
import static com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS;
import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static java.lang.reflect.Proxy.newProxyInstance;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toMap;
import static lombok.AccessLevel.PROTECTED;
import static org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.BIGNUMBER_POSIX;
import static org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.LONGFILE_POSIX;

@Slf4j
public class PodEngine<T extends Container<T>> {
    public static final String RUNNING = "Running";
    public static final String PENDING = "Pending";
    public static final String UNKNOWN = "Unknown";
    public static final String ORG_TESTCONTAINERS_TYPE = "org.testcontainers.type";
    public static final String ORG_TESTCONTAINERS_NAME = "org.testcontainers.name";
    public static final String KUBECONTAINERS = "kubecontainers";
    private static final String ORG_TESTCONTAINERS_HASH = "org.testcontainers.hash";
    protected final PodNameGenerator podNameGenerator;
    private final T container;
    private final Map<Transferable, String> copyToTransferableContainerPathMap = new HashMap<>();
    private final Map<String, String> labels = new HashMap<>();
    private final Map<Integer, Integer> hostPorts = new HashMap<>();
    @Getter
    protected JsonMapper jsonMapper = config(new JsonMapper());
    protected KubernetesClientBuilder kubernetesClientBuilder;
    protected KubernetesClient kubernetesClient;
    @Getter
    @Setter
    protected String dockerImageName;
    @Getter
    protected Boolean runAsNonRoot;
    @Getter
    protected Long runAsUser;
    @Getter
    protected Long runAsGroup;
    @Getter
    protected Long fsGroup;
    protected boolean privilegedMode;
    protected boolean allowPrivilegeEscalation;
    protected String imagePullPolicy = "Always";
    @Getter
    protected Duration startupTimeout = ofSeconds(60);
    @Getter
    protected String portProtocol = "TCP";
    private PodResource pod;
    private boolean localPortForwardEnabled = true;
    private Map<Integer, LocalPortForward> localPortForwards = Map.of();
    private boolean hostPortEnabled = false;
    @Getter
    private String imagePullSecretName;
    @Getter
    private UnaryOperator<PodBuilder> podBuilderCustomizer;
    @Getter
    private UnaryOperator<ContainerBuilder> containerBuilderCustomizer;
    @Getter
    private String podContainerName = "main";
    private String podName;
    private boolean deletePodOnStop = true;
    @Getter(PROTECTED)
    private boolean started;
    private InetAddress localPortForwardHost;
    private List<String> entryPoint;
    @Getter
    private boolean shouldBeReused;
    private boolean reused;

    public PodEngine(@NonNull T container) {
        this(container, null);
    }

    public PodEngine(@NonNull T container, String dockerImageName) {
        this(container, dockerImageName, new DefaultPodNameGenerator());
    }

    public PodEngine(@NonNull T container, String dockerImageName,
                     @NonNull PodNameGenerator podNameGenerator) {
        this.container = container;
        this.podNameGenerator = podNameGenerator;
        this.dockerImageName = dockerImageName;
    }

    private static JsonMapper config(JsonMapper jsonMapper) {
        return jsonMapper.rebuild()
                .enable(SORT_PROPERTIES_ALPHABETICALLY)
                .enable(ORDER_MAP_ENTRIES_BY_KEYS)
                .build();
    }

    @SneakyThrows
    private static <T> T getFieldValue(Object object, String fieldName) {
        var regExField = object.getClass().getDeclaredField(fieldName);
        regExField.setAccessible(true);
        return (T) regExField.get(object);
    }

    private static WaitStrategy replacePodWaiters(@NotNull WaitStrategy waitStrategy) {
        return waitStrategy instanceof LogMessageWaitStrategy
                ? new PodLogMessageWaitStrategy(getFieldValue(waitStrategy, "regEx"))
                : waitStrategy instanceof HostPortWaitStrategy
                ? new PodPortWaitStrategy(getFieldValue(waitStrategy, "ports"))
                : waitStrategy;
    }

    private static String getError(ExecWatch exec) {
        try {
            return new String(exec.getError().readAllBytes(), UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    @NotNull
    private static String getOut(ExecWatch exec) {
        try {
            return new String(exec.getOutput().readAllBytes(), UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    static String createExecCommandForUpload(String file) {
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
                ", podResource=" + getPod() +
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

    public T withKubernetesClientBuilder(KubernetesClientBuilder kubernetesClientBuilder) {
        this.kubernetesClientBuilder = kubernetesClientBuilder;
        return container;
    }

    public T withJsonMapper(JsonMapper jsonMapper) {
        this.jsonMapper = config(jsonMapper);
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

    public T withRunAsGroup(Long runAsGroup) {
        this.runAsGroup = runAsGroup;
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

    public T withContainerBuilderCustomizer(UnaryOperator<ContainerBuilder> podContainerBuilderCustomizer) {
        this.containerBuilderCustomizer = podContainerBuilderCustomizer;
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

    public T localPortForwardEnable() {
        this.localPortForwardEnabled = true;
        return container;
    }

    public T localPortForwardDisable() {
        this.localPortForwardEnabled = false;
        return container;
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
        this.privilegedMode = privilegedMode;
        return container;
    }

    public T withAllowPrivilegeEscalation(boolean allowPrivilegeEscalation) {
        this.allowPrivilegeEscalation = allowPrivilegeEscalation;
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

    public T withReuse(boolean reusable) {
        this.shouldBeReused = reusable;
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
                        this.entryPoint = List.of(strings);
                    } else if (firstArg instanceof List<?> list) {
                        this.entryPoint = list.stream().map(String::valueOf).toList();
                    }
                    yield null;
                }
                default -> throw new UnsupportedOperationException(methodName);
            };
        });
    }

    public boolean isRunning() {
        var podResource = this.getPod();
        return podResource != null && RUNNING.equals(podResource.get().getStatus().getPhase());
    }

    public boolean isHealthy() {
        var podResource = this.getPod();
        return podResource != null && Set.of(PENDING, RUNNING).contains(podResource.get().getStatus().getPhase());
    }

    public boolean isCreated() {
        var podResource = this.getPod();
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
        var containerName = podContainerName;
        return getPod().get().getSpec().getContainers().stream().filter(c -> containerName.equals(c.getName()))
                .findFirst().orElseThrow(() -> new IllegalStateException("container '" + containerName + "' not found"));
    }

    public String getMappedPortHost(int originalPort) {
        return getLocalPortForward(originalPort).getLocalAddress().getHostName();
    }

    public String getLogs() {
        return getPod().getLog();
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
        return getPod().get().getStatus().getHostIP();
    }

    public void start() {
        configure();
        var podName = podNameGenerator.generatePodName();
        this.podName = podName;
        var podBuilder = newPodBuilder(podName);

        var hash = hash(podBuilder.build());

        podBuilder
                .editMetadata()
                .withName(podName)
                .addToLabels(Map.of(
                        ORG_TESTCONTAINERS_NAME, podName,
                        ORG_TESTCONTAINERS_HASH, hash
                ))
                .endMetadata();

        var kubernetesClient = kubernetesClient();
        final Pod pod;
        if (shouldBeReused) {
            var options = new ListOptionsBuilder().withLabelSelector(ORG_TESTCONTAINERS_HASH).build();
            var podList = kubernetesClient.pods().list(options);
            var findPod = podList.getItems().stream().filter(p ->
                            hash.equals(p.getMetadata().getLabels().get(ORG_TESTCONTAINERS_HASH)))
                    .findFirst().orElse(null);
            if (findPod == null) {
                pod = kubernetesClient.resource(podBuilder.build()).create();
            } else {
                reused = true;
                pod = findPod;
                var metadata = pod.getMetadata();
                var uid = metadata.getUid();
                var name = metadata.getName();
                log.info("reuse first appropriated pod '" + name + "' (uid " + uid + ")");
            }
        } else {
            pod = kubernetesClient.resource(podBuilder.build()).create();
        }
        this.pod = kubernetesClient.pods().resource(pod);

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

    protected PodBuilder newPodBuilder(String podName) {
        var containerBuilder = new ContainerBuilder()
                .withImagePullPolicy(imagePullPolicy)
                .withImage(getDockerImageName())
                .withSecurityContext(new SecurityContextBuilder()
                        .withRunAsNonRoot(runAsNonRoot)
                        .withRunAsUser(runAsUser)
                        .withRunAsGroup(runAsGroup)
                        .withPrivileged(privilegedMode)
                        .withAllowPrivilegeEscalation(allowPrivilegeEscalation)
                        .build())
                .withName(podContainerName)
                .withArgs(getArgs())
                .withCommand(entryPoint)
                .withPorts(getContainerPorts())
                .withEnv(getVars());
        if (containerBuilderCustomizer != null) {
            containerBuilder = containerBuilderCustomizer.apply(containerBuilder);
        }

        var podBuilder = new PodBuilder()
                .withMetadata(new ObjectMetaBuilder()
//                        .withName(podName)
                        .addToLabels(labels)
                        .addToLabels(Map.of(
                                ORG_TESTCONTAINERS_TYPE, KUBECONTAINERS//,
//                                ORG_TESTCONTAINERS_NAME, podName
                        ))
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
                        .withContainers(containerBuilder.build())
                        .build());

        if (podBuilderCustomizer != null) {
            podBuilder = podBuilderCustomizer.apply(podBuilder);
        }
        return podBuilder;
    }

    @NotNull
    protected List<ContainerPort> getContainerPorts() {
        return getExposedPorts().stream().map(port -> {
            var portBuilder = new ContainerPortBuilder()
                    .withContainerPort(port)
                    .withProtocol(portProtocol);
            if (hostPortEnabled) {
                portBuilder.withHostPort(hostPorts.get(port));
            }
            return portBuilder.build();
        }).toList();
    }

    @NotNull
    private List<EnvVar> getVars() {
        return container.getEnvMap().entrySet().stream().map(e ->
                new EnvVarBuilder().withName(e.getKey()).withValue(e.getValue()).build()).toList();
    }

    private String[] getArgs() {
        return container.getCommandParts();
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
        if (!reused && deletePodOnStop) {
            var podResource = this.getPod();
            if (podResource != null) {
                podResource.delete();
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
        try (var ignored = pod.terminateOnError().exec("touch", escapedTarPath)) {
        }

        uploadBase64(payload, escapedTarPath);

        var unpackDir = "/";
        var extractTarCmd = format("mkdir -p %1$s; tar -C %1$s -xmf %2$s; e=$?; rm %2$s; exit $e",
                shellQuote(unpackDir), tarName);

        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        try (var exec = pod.redirectingInput().writingOutput(out).writingError(err).exec("sh", "-c", extractTarCmd)) {
            var exitedCode = exec.exitCode();
            var exitCode = exitedCode.get();
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
        try (var uploadWatch = (ExecWebSocketListener) pod.redirectingInput().terminateOnError().exec("cp", "/dev/stdin", escapedTarPath)) {
            var input = uploadWatch.getInput();
            input.write(payload);
            input.flush();
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
        try (var countWatch = pod.writingOutput(byteCount).terminateOnError().exec("sh", "-c", "wc -c < " + filePath)) {
            var exitCode = countWatch.exitCode().get();
            var remoteSizeRaw = new String(byteCount.toByteArray(), UTF_8).trim();
            return Integer.parseInt(remoteSizeRaw);
        }
    }

    @SneakyThrows
    public boolean removeFile(String tarName) {
        try (var exec = pod.redirectingError().exec("rm", tarName)) {
            var exitCode = exec.exitCode().get();
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
            var exitCode = exited.get();
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
        labels.put(key, value);
        return container;
    }

    protected void waitUntilPodStarted() {
        var startTime = System.currentTimeMillis();
        var pod = getPod().get();
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
                getPod().delete();
                throw new StartPodException("interrupted", podName, phase, e);
            }
            status = getPod().get().getStatus();
        }

        try {
            if (!RUNNING.equals(status.getPhase())) {
                getPod().delete();
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
    private void invokeContainerMethod(String name) {
        if (container instanceof GenericContainer<?>) {
            var method = GenericContainer.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(container);
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
        var inetAddress = localPortForwardHost;
        var exposedPorts = getExposedPorts();
        localPortForwards = exposedPorts.stream().collect(toMap(port -> port, port -> {
            var pod = getPod();
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
                log.info("port forward local {}:{} to remote {}.{}:{}, ", localAddress.getHostAddress(), localPort,
                        podName, podContainerName, port);
            }
            return localPortForward;
        }));
    }

    private List<Integer> getExposedPorts() {
        return container.getExposedPorts();
    }

    protected Map<Integer, LocalPortForward> getLocalPortForwards() {
        return requireNonNull(localPortForwards, "port forwarding has not been started yet");
    }

    protected LocalPortForward getLocalPortForward(int originalPort) {
        return requireNonNull(getLocalPortForwards().get(originalPort), "Requested port (" + originalPort + ") is not mapped");
    }

    protected PodResource getPod() {
        if (pod == null) {
            start();
        }
        return pod;
    }

    private void assertPodRunning(String funcName) {
        if (getContainerId() == null) {
            throw new IllegalStateException(funcName + " can only be used with running pod");
        }
    }

    public void addHostPort(Integer port, Integer hostPort) {
        this.hostPorts.put(port, hostPort);
    }

}
