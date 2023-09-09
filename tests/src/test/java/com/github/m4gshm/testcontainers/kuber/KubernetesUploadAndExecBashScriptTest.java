package com.github.m4gshm.testcontainers.kuber;

import com.github.m4gshm.testcontainers.GenericPod;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.testcontainers.utility.MountableFile;

public class KubernetesUploadAndExecBashScriptTest {

    @Test
    @SneakyThrows
    public <T> void uploadAndExecScript() {
        try (var pod = new GenericPod<>("alpine:3.18.3")) {
            pod.copyFileToContainer(MountableFile.forClasspathResource("/test_script.sh", 777), "/entry.sh");
            pod.execInContainer("/entry.sh");
        }
    }
}
