package com.github.m4gshm.testcontainers;

import lombok.RequiredArgsConstructor;

import java.security.SecureRandom;

import static org.apache.commons.codec.binary.Hex.encodeHexString;

@RequiredArgsConstructor
public class DefaultPodNameGenerator implements PodNameGenerator {
    protected final SecureRandom random;
    private final String prefix;

    public DefaultPodNameGenerator() {
        this(new SecureRandom(), "testcontainer-");
    }

    @Override
    public String generatePodName() {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        var id = encodeHexString(bytes);
        return this.prefix + id.substring(0, 49);
    }
}
