package com.github.m4gshm.testcontainers;

import com.github.m4gshm.test.jpa.model.UserEntity;
import com.github.m4gshm.test.jpa.service.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import static org.junit.jupiter.api.Assertions.assertEquals;

abstract class AbstractPostgresTest {
    @Autowired
    protected UserRepository userRepository;

    @Test
    public void storeUsers() {
        userRepository.save(UserEntity.builder()
                .id(1)
                .name("First")
                .build());

        var user = userRepository.findById(1).get();
        assertEquals("First", user.getName());
    }

    @Configuration
    @EnableAutoConfiguration
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @EntityScan(basePackageClasses = UserEntity.class)
    public static class TestConfig {

    }
}
