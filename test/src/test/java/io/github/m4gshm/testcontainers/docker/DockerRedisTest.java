package io.github.m4gshm.testcontainers.docker;

import io.github.m4gshm.testcontainers.AbstractRedisContainerInitializer;
import io.github.m4gshm.testcontainers.AbstractSpringDataRedisTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.GenericContainer;

@SpringBootTest(classes = AbstractSpringDataRedisTest.TestConfig.class, properties = {
        "spring.data.jpa.repositories.enabled=false",
})
@ContextConfiguration(initializers = DockerRedisTest.RedisContainerInitializer.class)
public class DockerRedisTest extends AbstractSpringDataRedisTest {

    public static class RedisContainerInitializer extends AbstractRedisContainerInitializer {

        @Override
        protected GenericContainer<?> newContainer() {
            return new GenericContainer("redis:5.0.3-alpine").withExposedPorts(REDIS_PORT);
        }
    }
}
