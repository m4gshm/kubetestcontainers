package com.github.m4gshm.testcontainers.kuber;

import com.github.m4gshm.testcontainers.AbstractUploadAndExecBashScriptTest;
import com.github.m4gshm.testcontainers.GenericPod;
import org.testcontainers.containers.GenericContainer;

import static com.github.m4gshm.testcontainers.PodEngine.Reuse.GLOBAL;

public class KubernetesUploadAndExecBashScriptTest extends AbstractUploadAndExecBashScriptTest {

    protected GenericContainer<?> newContainer() {
        return new GenericPod<>("alpine:3.18.3")
                .withCommand("sleep 2m")
                .withDeletePodOnStop(false)
                .withReuse(GLOBAL);
    }

}
