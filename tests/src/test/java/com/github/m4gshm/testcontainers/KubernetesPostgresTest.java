package com.github.m4gshm.testcontainers;

import com.github.m4gshm.testcontainers.context.PostgreSQLPodInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import static com.github.m4gshm.testcontainers.DockerPostgresTest.TestConfig;

@SpringBootTest(classes = TestConfig.class)
@ContextConfiguration(initializers = PostgreSQLPodInitializer.class)
public class KubernetesPostgresTest extends AbstractPostgresTest {

}
