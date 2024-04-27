package io.github.m4gshm.testcontainers.kuber;

import io.github.m4gshm.testcontainers.AbstractJdbcDatabaseContainerInitializer;
import io.github.m4gshm.testcontainers.AbstractSpringDataJpaTest;
import io.github.m4gshm.testcontainers.PostgresqlPod;
import lombok.SneakyThrows;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.JdbcDatabaseContainer;

import static java.net.InetAddress.getByName;
import static java.time.Duration.ofSeconds;

@SpringBootTest(classes = AbstractSpringDataJpaTest.TestConfig.class)
@ContextConfiguration(initializers = KubernetesPostgresqlTest.PostgresqlPodInitializer.class)
public class KubernetesPostgresqlTest extends AbstractSpringDataJpaTest {

    public static class PostgresqlPodInitializer extends AbstractJdbcDatabaseContainerInitializer {

        @Override
        @SneakyThrows
        protected JdbcDatabaseContainer<?> newContainer() {
            return new PostgresqlPod()
                    .withDeletePodOnStop(false)
                    .withLocalPortForwardHost(getByName("localhost"))
                    .withStartupTimeout(ofSeconds(10))
                    .withConnectTimeoutSeconds(10)
                    .withStartupTimeoutSeconds(10);
        }
    }
}
