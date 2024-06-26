package io.github.m4gshm.testcontainers.kuber;

import io.github.m4gshm.testcontainers.AbstractUploadAndExecBashScriptTest;
import io.github.m4gshm.testcontainers.GenericPod;
import io.github.m4gshm.testcontainers.PodContainerDelegate;
import org.testcontainers.containers.GenericContainer;

import static io.github.m4gshm.testcontainers.AbstractPod.Reuse.SESSION;

public class KubernetesUploadAndExecBashScriptTest extends AbstractUploadAndExecBashScriptTest {

    protected GenericContainer<?> newContainer() {
        return new GenericPod<>("alpine:3.18.3")
                .withCommand("sleep 2m")
                .withDeletePodOnError(true)
                .withReuse(SESSION);
    }

}
