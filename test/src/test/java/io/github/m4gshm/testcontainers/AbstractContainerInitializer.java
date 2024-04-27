package io.github.m4gshm.testcontainers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.testcontainers.containers.GenericContainer;

public abstract class AbstractContainerInitializer<C extends GenericContainer<?>>
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected abstract C newContainer();

    protected void initContext(ConfigurableApplicationContext configurableApplicationContext, C container) {

    }

    @Override
    public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
        var container = newContainer();
        configurableApplicationContext.addApplicationListener(event -> {
            if (event instanceof ContextClosedEvent) {
                log.info("stop testcontainer {}", container.getContainerName());
                container.stop();
            }
        });
        container.start();

        initContext(configurableApplicationContext, container);
    }


}
