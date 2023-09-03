package com.github.m4gshm.testcontainers;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import static com.github.m4gshm.testcontainers.DockerPostgresTest.*;

@SpringBootTest(classes = TestConfig.class)
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
public class DockerPostgresTest extends AbstractPostgresTest {

}
