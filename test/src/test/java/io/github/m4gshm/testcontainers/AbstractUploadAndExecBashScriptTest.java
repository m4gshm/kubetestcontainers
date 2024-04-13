package io.github.m4gshm.testcontainers;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.utility.MountableFile.forClasspathResource;

@Slf4j
@Disabled
public abstract class AbstractUploadAndExecBashScriptTest {
    @NotNull
    @SneakyThrows
    private static String readString(InputStream inputStream) {
        try (var srcStream = inputStream) {
            return new String(srcStream.readAllBytes());
        }
    }

    protected static void ls(GenericContainer<? extends GenericContainer<?>> container, String dir) throws IOException, InterruptedException {
        var execResult = container.execInContainer("ls -la " + dir);
        log.info("ls {} -> {} ", dir, execResult.getStdout());
    }

    protected abstract GenericContainer<?> newContainer();

    @Test
    @SneakyThrows
    public <T> void uploadAndExecScript() {
        try (var container = newContainer()
                .withCopyToContainer(forClasspathResource("/scripts/test_script.sh", 0777), "/entry.sh")
        ) {
            container.start();
            ls(container, "/entry.sh");
            var execResult = container.execInContainer("/entry.sh");

            var exitCode = execResult.getExitCode();
            assertEquals(0, exitCode, "exitCode: " + exitCode +
                    ", err: " + execResult.getStderr().replace("\r", " "));
            var stdout = execResult.getStdout();
            var result = stdout.trim();
            assertEquals("test output", result);
        }
    }

    @Test
    @SneakyThrows
    public <T> void uploadDirAndExecScript() {
        try (var container = newContainer()
                .withCopyToContainer(forClasspathResource("scripts"), "/scripts")
        ) {
            container.start();
            ls(container, "/scripts");
            var execResult = container.execInContainer("sh", "-c", "cd /scripts && ./test_script.sh");

            var exitCode = execResult.getExitCode();
            assertEquals(0, exitCode, "exitCode: " + exitCode +
                    ", err: " + execResult.getStderr().replace("\r", " "));
            var stdout = execResult.getStdout();
            var result = stdout.trim();
            assertEquals("test output", result);
        }
    }

    @Test
    @SneakyThrows
    public <T> void uploadDownloadOneFile() {
        var srcFile = "/scripts/test_script.sh";
        var containerFile = "/entry.sh";

        var expectedContent = AbstractUploadAndExecBashScriptTest.readString(getClass().getResourceAsStream(srcFile));

        try (var container = newContainer()
                .withCopyToContainer(forClasspathResource(srcFile), containerFile)
        ) {
            var outDir = new File(System.getProperty("java.io.tmpdir"), randomUUID().toString());
            outDir.mkdirs();

            var outFile = new File(outDir, "out.sh");

            container.start();

            ls(container, containerFile);

            container.copyFileFromContainer(containerFile, outFile.getAbsolutePath());

            var content = AbstractUploadAndExecBashScriptTest.readString(new FileInputStream(outFile));

            assertEquals(expectedContent, content);
        }
    }
}
