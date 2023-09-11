package com.github.m4gshm.testcontainers.docker;

import com.github.m4gshm.testcontainers.AbstractJdbcDatabaseContainerInitializer;
import com.github.m4gshm.testcontainers.AbstractSpringDataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import static com.github.m4gshm.testcontainers.AbstractSpringDataJpaTest.TestConfig;

@SpringBootTest(classes = TestConfig.class)
@ContextConfiguration(initializers = DockerPostgresTest.PostgresqlContainerInitializer.class)
public class DockerPostgresTest extends AbstractSpringDataJpaTest {

    public static class PostgresqlContainerInitializer extends AbstractJdbcDatabaseContainerInitializer {
        @Override
        protected JdbcDatabaseContainer<?> newContainer() {
            return new PostgreSQLContainer<>();
        }

    }


}
