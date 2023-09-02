package com.github.m4gshm.testcontainers.wait;

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
        var logs = "";
        while (true) {
            logs = waitStrategyTarget.getLogs();
            if (logs.matches("(?s)" + regEx)) {
                break;
            }
            if (timeout <= System.currentTimeMillis()) {
                throw new ContainerLaunchException("Timed out waiting for log output matching '" + regEx + "'");
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new ContainerLaunchException("waitUntilReady is interrupted", e);
            }
        }
        log.trace("log waiter is finished, regExp {}, log:\n{}", regEx, logs);
    }
}
