package com.github.m4gshm.testcontainers;

import com.github.m4gshm.spring.data.repository.UserRepository;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class AbstractSpringDataRedisTest extends AbstractSpringDataTest {

    @Configuration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @EnableRedisRepositories(basePackageClasses = UserRepository.class)
    public static class TestConfig {

    }
}
