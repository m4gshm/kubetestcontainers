package io.github.m4gshm.testcontainers.docker;

import io.github.m4gshm.testcontainers.AbstractJdbcDatabaseContainerInitializer;
import io.github.m4gshm.testcontainers.AbstractSpringDataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = AbstractSpringDataJpaTest.TestConfig.class)
@ContextConfiguration(initializers = DockerPostgresTest.PostgresqlContainerInitializer.class)
public class DockerPostgresTest extends AbstractSpringDataJpaTest {

    public static class PostgresqlContainerInitializer extends AbstractJdbcDatabaseContainerInitializer {
        @Override
        protected JdbcDatabaseContainer<?> newContainer() {
            return new PostgreSQLContainer<>();
        }

    }


}
