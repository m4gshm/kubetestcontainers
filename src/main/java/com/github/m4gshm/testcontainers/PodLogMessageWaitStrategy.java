package com.github.m4gshm.testcontainers;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;

import java.util.concurrent.TimeUnit;

@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class PodLogMessageWaitStrategy extends AbstractWaitStrategy {

    @With
    private String regEx;

    @Override
    protected void waitUntilReady() {
        var startTime = System.currentTimeMillis();
        var timeout = startTime + TimeUnit.SECONDS.toMillis(startupTimeout.getSeconds());
        var matches = false;
        var logs = "";
        while (!matches) {
            logs = waitStrategyTarget.getLogs();
            matches = logs.matches("(?s)" + regEx);
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new ContainerLaunchException("waitUntilReady is interrupted", e);
            }

            if (timeout <= System.currentTimeMillis()) {
                throw new ContainerLaunchException("Timed out waiting for log output matching '" + regEx + "'");
            }
        }
        log.trace("log waiter is finished, regExp {}, log:\n{}", regEx, logs);
    }
}
