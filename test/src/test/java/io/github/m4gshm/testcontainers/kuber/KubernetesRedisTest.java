package io.github.m4gshm.testcontainers.kuber;

import io.github.m4gshm.testcontainers.AbstractRedisContainerInitializer;
import io.github.m4gshm.testcontainers.AbstractSpringDataRedisTest;
import io.github.m4gshm.testcontainers.GenericPod;
import lombok.SneakyThrows;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.GenericContainer;

import static java.net.InetAddress.getByName;

@SpringBootTest(classes = AbstractSpringDataRedisTest.TestConfig.class, properties = {
        "spring.data.jpa.repositories.enabled=false",
})
@ContextConfiguration(initializers = KubernetesRedisTest.RedisContainerInitializer.class)
public class KubernetesRedisTest extends AbstractSpringDataRedisTest {

    public static class RedisContainerInitializer extends AbstractRedisContainerInitializer {

        @Override
        @SneakyThrows
        protected GenericContainer<?> newContainer() {
            return new GenericPod<>("redis:5.0.3-alpine")
                    .withDeletePodOnStop(true)
                    .withDeletePodOnError(true)
                    .withLocalPortForwardHost(getByName("localhost"))
                    .withExposedPorts(REDIS_PORT);
        }
    }
}
