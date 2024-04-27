package io.github.m4gshm.testcontainers.docker;

import io.github.m4gshm.testcontainers.AbstractContainerInitializer;
import io.github.m4gshm.testcontainers.AbstractSpringDataMongoTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.MongoDBContainer;

@SpringBootTest(classes = AbstractSpringDataMongoTest.TestConfig.class)
@ContextConfiguration(initializers = DockerMongoTest.ContainerInitializer.class)
public class DockerMongoTest extends AbstractSpringDataMongoTest {

    public static class ContainerInitializer extends AbstractContainerInitializer<MongoDBContainer> {
        @Override
        protected MongoDBContainer newContainer() {
            return new MongoDBContainer();
        }

        @Override
        protected void initContext(
                ConfigurableApplicationContext configurableApplicationContext, MongoDBContainer container
        ) {
            TestPropertyValues.of(
                    "spring.data.mongodb.uri=" + container.getConnectionString(),
                    "spring.data.mongodb.database=testcontainer"
            ).applyTo(configurableApplicationContext.getEnvironment());
        }
    }
}
