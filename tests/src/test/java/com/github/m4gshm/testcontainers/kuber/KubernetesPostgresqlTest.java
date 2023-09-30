package com.github.m4gshm.testcontainers.kuber;

import com.github.m4gshm.testcontainers.AbstractJdbcDatabaseContainerInitializer;
import com.github.m4gshm.testcontainers.AbstractSpringDataJpaTest;
import com.github.m4gshm.testcontainers.PostgresqlPod;
import lombok.SneakyThrows;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.JdbcDatabaseContainer;

import static com.github.m4gshm.testcontainers.AbstractSpringDataJpaTest.TestConfig;
import static java.net.InetAddress.getByName;
import static java.time.Duration.ofSeconds;

@SpringBootTest(classes = TestConfig.class)
@ContextConfiguration(initializers = KubernetesPostgresqlTest.PostgresqlPodInitializer.class)
public class KubernetesPostgresqlTest extends AbstractSpringDataJpaTest {

    public static class PostgresqlPodInitializer extends AbstractJdbcDatabaseContainerInitializer {

        @Override
        @SneakyThrows
        protected JdbcDatabaseContainer<?> newContainer() {
            return new PostgresqlPod()
                    .withLocalPortForwardHost(getByName("localhost"))
                    .withStartupTimeout(ofSeconds(10))
                    .withConnectTimeoutSeconds(10)
                    .withStartupTimeoutSeconds(10);
        }

    }
}
