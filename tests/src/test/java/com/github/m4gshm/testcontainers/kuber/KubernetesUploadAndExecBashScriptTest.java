package com.github.m4gshm.testcontainers.kuber;

import com.github.m4gshm.testcontainers.GenericPod;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.testcontainers.utility.MountableFile;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class KubernetesUploadAndExecBashScriptTest {

    @Test
    @SneakyThrows
    public <T> void uploadAndExecScript() {
        try (var container = new GenericPod<>("alpine:3.18.3").withCommand("sleep 2m").withDeletePodOnStop(false)) {
            container.start();
            container.copyFileToContainer(MountableFile.forClasspathResource("/test_script.sh", 777), "/entry.sh");
            var execResult = container.execInContainer("chmod 777 /entry.sh && /entry.sh");

            var stdout = execResult.getStdout();
            var result = stdout.trim();
            assertEquals("test output", result);
        }
    }
}
