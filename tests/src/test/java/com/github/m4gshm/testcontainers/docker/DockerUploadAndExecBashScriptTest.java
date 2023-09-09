package com.github.m4gshm.testcontainers.docker;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DockerUploadAndExecBashScriptTest {

    @Test
    @SneakyThrows
    public <T> void uploadAndExecScript() {
        try (var container = new GenericContainer<>("alpine:3.18.3").withCommand("sleep 2m")) {
            container.start();
            container.copyFileToContainer(MountableFile.forClasspathResource("/test_script.sh", 777), "/entry.sh");
            var execResult = container.execInContainer("/entry.sh");

            var stdout = execResult.getStdout();
            var result = stdout.trim();
            assertEquals("test output", result);
        }
    }
}
