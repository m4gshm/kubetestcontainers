package io.github.m4gshm.testcontainers;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.github.m4gshm.testcontainers.wait.PodLogMessageWaitStrategy;
import io.github.m4gshm.testcontainers.wait.PodPortWaitStrategy;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.MountableFile;

import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static io.github.m4gshm.testcontainers.DefaultPodNameGenerator.newDefaultPodNameGenerator;
import static io.github.m4gshm.testcontainers.PodContainerUtils.config;
import static io.github.m4gshm.testcontainers.PodContainerUtils.newCreateContainerCmd;
import static java.net.InetAddress.getByName;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * The delegate is used by container implementations that uses Kubernetes;
 *
 * @param <T> a container type
 */
@Slf4j
public class PodContainerDelegate<T extends Container<T>> extends AbstractPod {
    private final T container;

    public PodContainerDelegate(@NonNull T container) {
        this(container, null);
    }

    public PodContainerDelegate(@NonNull T container, String dockerImageName) {
        this(container, dockerImageName, newDefaultPodNameGenerator());
    }

    public PodContainerDelegate(@NonNull T container, String dockerImageName,
                                @NonNull PodNameGenerator podNameGenerator) {
        super(podNameGenerator);
        podBuilderFactory.setDockerImageName(dockerImageName);
        this.container = container;

        if (container instanceof GenericContainer<?>) {
            var rawWaitStrategy = invokeContainerMethod("getWaitStrategy");
            var replacePodWaiters = replacePodWaiters((WaitStrategy) rawWaitStrategy);
            setFieldValue(GenericContainer.class, container, "waitStrategy", replacePodWaiters);
        }
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

    @SneakyThrows
    protected void containerIsStarted(InspectContainerResponse containerInfo, boolean reused) {
        var containerIsStarted = GenericContainer.class.getDeclaredMethod(
                "containerIsStarted", InspectContainerResponse.class, boolean.class
        );
        containerIsStarted.setAccessible(true);
        containerIsStarted.invoke(container, containerInfo, reused);
    }

    @SneakyThrows
    protected Object invokeContainerMethod(String name) {
        if (container instanceof GenericContainer<?>) {
            var method = GenericContainer.class.getDeclaredMethod(name);
            method.setAccessible(true);
            return method.invoke(container);
        } else {
            throw new IllegalStateException("no method '" + name + "' in container " + container.getClass());
        }
    }

    @Override
    protected void waitUntilContainerStarted() {
        invokeContainerMethod("waitUntilContainerStarted");
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
        modifier.accept(newCreateContainerCmd(getClass().getClassLoader(), podBuilderFactory));
        return container;
    }

    public T withReuse(Reuse reuse) {
        this.reuse = reuse;
        return container;
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

    @Override
    protected @NotNull List<EnvVar> getEnvVars() {
        return container.getEnvMap().entrySet().stream().map(e ->
                new EnvVarBuilder().withName(e.getKey()).withValue(e.getValue()).build()).toList();
    }

    @Override
    protected String[] getCommandParts() {
        return container.getCommandParts();
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

    @Override
    protected void configure() {
        invokeContainerMethod("configure");
    }

    @Override
    @SneakyThrows
    protected void containerIsStarting(InspectContainerResponse containerInfo, boolean reused) {
        var containerIsStarted = GenericContainer.class.getDeclaredMethod(
                "containerIsStarting", InspectContainerResponse.class, boolean.class
        );
        containerIsStarted.setAccessible(true);
        containerIsStarted.invoke(container, containerInfo, reused);
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

    @Override
    protected List<Integer> getExposedPorts() {
        return container.getExposedPorts();
    }

}
