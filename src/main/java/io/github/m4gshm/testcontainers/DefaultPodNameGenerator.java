package io.github.m4gshm.testcontainers;

import lombok.RequiredArgsConstructor;
import org.testcontainers.containers.Container;

import java.security.SecureRandom;

import static org.apache.commons.codec.binary.Hex.encodeHexString;

/**
 * Generates pod names like `testcontainer-fa4cacacf1a5121f1fcc7d42d5d3742bf38a0dcfb34e86f9c`.
 */
@RequiredArgsConstructor
public class DefaultPodNameGenerator implements PodNameGenerator {
    private final SecureRandom random;
    private final String prefix;
    private final boolean withImageNamePrefix;

    public static DefaultPodNameGenerator newDefaultPodNameGenerator() {
        return newDefaultPodNameGenerator(true);
    }

    public static DefaultPodNameGenerator newDefaultPodNameGenerator(boolean useImageNamePrefix) {
        return new DefaultPodNameGenerator(new SecureRandom(), "testcontainer-", useImageNamePrefix);
    }

    private static String imageNamePrefix(Container<?> container) {
        var dockerImageName = container.getDockerImageName();
        var parts = dockerImageName.split(":");
        var prefix = parts.length > 0 ? parts[0] : "";
        return !prefix.isEmpty() ? prefix + "-" : "";
    }

    @Override
    public String generatePodName(Container<?> container) {
        var maxNameLength = 63;
        var prefix = this.prefix + imageNamePrefix(container);
        var reminds = maxNameLength - prefix.length();
        var bytes = new byte[reminds / 2];
        random.nextBytes(bytes);
        var id = encodeHexString(bytes);
        return prefix + id;
    }
}
