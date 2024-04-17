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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

@Getter
@Setter
public class PodBuilderFactory {
    private final Map<String, String> labels = new HashMap<>();
    private final Map<Integer, Integer> hostPorts = new HashMap<>();

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
    private List<Integer> exposedPorts = new ArrayList<>();
    private boolean hostPortEnabled = false;

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
                .withPorts(getContainerPorts())
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

    @NotNull
    protected List<ContainerPort> getContainerPorts() {
        return getExposedPorts().stream().map(port -> {
            var portBuilder = new ContainerPortBuilder()
                    .withContainerPort(port)
                    .withProtocol(getPortProtocol());
            if (isHostPortEnabled()) {
                portBuilder.withHostPort(getHostPorts().get(port));
            }
            return portBuilder.build();
        }).toList();
    }

    public void addHostPort(Integer port, Integer hostPort) {
        getHostPorts().put(port, hostPort);
    }

    public void addLabel(String label, String value) {
        getLabels().put(label, value);
    }
}
