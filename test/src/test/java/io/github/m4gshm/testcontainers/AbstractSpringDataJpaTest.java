package io.github.m4gshm.testcontainers;

import io.github.m4gshm.jpa.model.UserEntity;
import io.github.m4gshm.spring.data.repository.UserRepository;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

public abstract class AbstractSpringDataJpaTest extends AbstractSpringDataTest {

    @Configuration
    @EnableAutoConfiguration
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @EntityScan(basePackageClasses = UserEntity.class)
    public static class TestConfig {

    }
}
