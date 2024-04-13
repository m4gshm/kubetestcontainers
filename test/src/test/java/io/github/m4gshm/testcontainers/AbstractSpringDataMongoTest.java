package io.github.m4gshm.testcontainers;

import io.github.m4gshm.spring.data.repository.UserRepository;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

public abstract class AbstractSpringDataMongoTest extends AbstractSpringDataTest {

    @Configuration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @EnableMongoRepositories(basePackageClasses = UserRepository.class)
    public static class TestConfig {
    }
}
