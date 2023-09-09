package com.github.m4gshm.testcontainers.kuber;

import com.github.m4gshm.testcontainers.AbstractRedisContainerInitializer;
import com.github.m4gshm.testcontainers.AbstractSpringDataRedisTest;
import com.github.m4gshm.testcontainers.GenericPod;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.GenericContainer;

@SpringBootTest(classes = AbstractSpringDataRedisTest.TestConfig.class, properties = {
        "spring.data.jpa.repositories.enabled=false",
})
@ContextConfiguration(initializers = KubernetesRedisTest.RedisContainerInitializer.class)
public class KubernetesRedisTest extends AbstractSpringDataRedisTest {

    public static class RedisContainerInitializer extends AbstractRedisContainerInitializer {

        @Override
        protected GenericContainer<?> newContainer() {
            return new GenericPod<>("redis:5.0.3-alpine").withExposedPorts(REDIS_PORT);
        }
    }
}
