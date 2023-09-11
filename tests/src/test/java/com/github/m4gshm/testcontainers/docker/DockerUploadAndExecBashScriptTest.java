package com.github.m4gshm.testcontainers.docker;

import com.github.m4gshm.testcontainers.AbstractUploadAndExecBashScriptTest;
import org.testcontainers.containers.GenericContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.utility.MountableFile.forClasspathResource;

public class DockerUploadAndExecBashScriptTest extends AbstractUploadAndExecBashScriptTest {

    @Override
    protected GenericContainer<?> newContainer() {
        return new GenericContainer<>("alpine:3.18.3").withCommand("sleep 2m");
    }

}
