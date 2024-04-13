package io.github.m4gshm.testcontainers;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;

public abstract class AbstractRedisContainerInitializer extends AbstractContainerInitializer<GenericContainer<?>> {

    public static final int REDIS_PORT = 6379;

    @Override
    protected abstract GenericContainer<?> newContainer();

    @Override
    protected void initContext(
            ConfigurableApplicationContext configurableApplicationContext,
            GenericContainer<?> container
    ) {
        var port = container.getMappedPort(REDIS_PORT);
        TestPropertyValues.of(
                "spring.data.redis.port=" + port
        ).applyTo(configurableApplicationContext.getEnvironment());

        log.info("start testcontainer {}, port {} ", container.getContainerName(), port);
    }
}
