package io.github.m4gshm.testcontainers;

import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.LocalObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodSecurityContextBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.SecurityContextBuilder;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static lombok.AccessLevel.NONE;

@Getter
@Setter
public class PodBuilderFactory {
    private Map<String, String> labels = new LinkedHashMap<>();
    private String dockerImageName;
    private Boolean runAsNonRoot;
    private Long runAsUser;
    private Long runAsGroup;
    private Long fsGroup;
    private boolean privilegedMode;
    private boolean allowPrivilegeEscalation;
    private String imagePullPolicy = "Always";
    private String imagePullSecretName;
    private UnaryOperator<PodBuilder> podBuilderCustomizer;
    private UnaryOperator<ContainerBuilder> containerBuilderCustomizer;
    private String podContainerName = "main";
    private List<String> entryPoint;
    private String portProtocol = "TCP";
    private List<EnvVar> vars;
    private String[] args;
    @Getter(NONE)
    @Setter(NONE)
    private Map<Integer, ContainerPort> ports = new LinkedHashMap<>();

    private static ContainerPort newContainerPort(String portProtocol, Integer port, Integer hostPort) {
        var portBuilder = new ContainerPortBuilder()
                .withContainerPort(port)
                .withProtocol(portProtocol);
        if (hostPort != null) {
            portBuilder.withHostPort(hostPort);
        }
        return portBuilder.build();
    }

    public PodBuilder newPodBuilder() {
        var containerBuilder = new ContainerBuilder()
                .withImagePullPolicy(getImagePullPolicy())
                .withImage(getDockerImageName())
                .withSecurityContext(new SecurityContextBuilder()
                        .withRunAsNonRoot(getRunAsNonRoot())
                        .withRunAsUser(getRunAsUser())
                        .withRunAsGroup(getRunAsGroup())
                        .withPrivileged(isPrivilegedMode())
                        .withAllowPrivilegeEscalation(isAllowPrivilegeEscalation())
                        .build())
                .withName(getPodContainerName())
                .withArgs(getArgs())
                .withCommand(getEntryPoint())
                .withPorts(new ArrayList<>(getPorts()))
                .withEnv(getVars());
        if (getContainerBuilderCustomizer() != null) {
            containerBuilder = getContainerBuilderCustomizer().apply(containerBuilder);
        }
        var podBuilder = new PodBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .addToLabels(getLabels())
                        .build())
                .withSpec(new PodSpecBuilder()
                        .withSecurityContext(new PodSecurityContextBuilder()
                                .withRunAsNonRoot(getRunAsNonRoot())
                                .withRunAsUser(getRunAsUser())
                                .withFsGroup(getFsGroup())
                                .build())
                        .withImagePullSecrets(new LocalObjectReferenceBuilder()
                                .withName(getImagePullSecretName())
                                .build())
                        .withContainers(containerBuilder.build())
                        .build());

        if (getPodBuilderCustomizer() != null) {
            podBuilder = getPodBuilderCustomizer().apply(podBuilder);
        }
        return podBuilder;
    }

    protected Collection<ContainerPort> getPorts() {
        return ports.values();
    }

    public void addPort(Integer port, Integer hostPort) {
        ports.put(port, newContainerPort(getPortProtocol(), port, hostPort));
    }

    public void addPort(Integer port) {
        addPort(port, null);
    }

    public void addLabel(String label, String value) {
        getLabels().put(label, value);
    }
}
