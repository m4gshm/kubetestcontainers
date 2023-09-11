package com.github.m4gshm.testcontainers.kuber;

import com.github.m4gshm.testcontainers.AbstractContainerInitializer;
import com.github.m4gshm.testcontainers.AbstractSpringDataMongoTest;
import com.github.m4gshm.testcontainers.MongoDBPod;
import lombok.SneakyThrows;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.MongoDBContainer;

import static java.net.InetAddress.getByName;

@SpringBootTest(classes = AbstractSpringDataMongoTest.TestConfig.class)
@ContextConfiguration(initializers = KubernetesMongoTest.ContainerInitializer.class)
public class KubernetesMongoTest extends AbstractSpringDataMongoTest {

    public static class ContainerInitializer extends AbstractContainerInitializer<MongoDBContainer> {
        @Override
        @SneakyThrows
        protected MongoDBContainer newContainer() {
            return new MongoDBPod().withLocalPortForwardHost(getByName("localhost"));
        }

        @Override
        protected void initContext(
                ConfigurableApplicationContext configurableApplicationContext, MongoDBContainer container
        ) {
            var connectionString = container.getConnectionString();
            TestPropertyValues.of(
                    "spring.data.mongodb.uri=" + connectionString,
                    "spring.data.mongodb.database=testcontainer"
            ).applyTo(configurableApplicationContext.getEnvironment());
        }
    }
}
