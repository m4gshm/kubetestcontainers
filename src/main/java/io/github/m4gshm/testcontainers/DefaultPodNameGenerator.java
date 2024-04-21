package io.github.m4gshm.testcontainers;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

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

    private static String imageNamePrefix(@NonNull String dockerImageName) {
        var parts = dockerImageName.split(":");
        var imageNamePath = parts.length > 0 ? parts[0] : "";
        var pathParts = imageNamePath.split("/");
        var name = pathParts[pathParts.length - 1];
        return !name.isEmpty() ? name + "-" : "";
    }

    @Override
    public String generatePodName(@NonNull String dockerImageName) {
        var maxNameLength = 63;
        var prefix = this.prefix + (withImageNamePrefix ? imageNamePrefix(dockerImageName) : "");
        var reminds = maxNameLength - prefix.length();
        var bytes = new byte[reminds / 2];
        random.nextBytes(bytes);
        var id = encodeHexString(bytes);
        return prefix + id;
    }
}
