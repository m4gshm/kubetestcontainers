package com.github.m4gshm.testcontainers.wait;

import com.github.m4gshm.testcontainers.PodEngine;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;

import static java.time.Duration.ofSeconds;


@Builder
@Slf4j
@AllArgsConstructor
public class PodPortWaitStrategy extends AbstractWaitStrategy {

    private PodEngine<?> podEngine;
    private Container<?> container;

    public PodPortWaitStrategy() {
        this.startupTimeout = ofSeconds(10);
    }

    @Override
    protected void waitUntilReady() {
        var timeout = startupTimeout.toMillis();
        var exposedPorts = container.getExposedPorts();
        var portsCount = exposedPorts.size();
        if (portsCount > 0) {
            var errors = new HashMap<Integer, IOException>(portsCount);
            var onePortTimeout = timeout / portsCount;
            for (var exposedPort : exposedPorts) {
                var localPort = container.getMappedPort(exposedPort);
                var localHost = podEngine.getMappedPortHost(exposedPort);
                try (var socket = new Socket()) {
                    var inetSocketAddress = new InetSocketAddress(localHost, localPort);
                    socket.connect(inetSocketAddress, (int) onePortTimeout);
                    errors.clear();
                    log.trace("local port forwarded check is success, pod port {}, local port {}, host {}",
                            exposedPort, localPort, localHost);
                } catch (IOException e) {
                    errors.put(exposedPort, e);
                }
            }

            if (!errors.isEmpty()) {
                var first = errors.entrySet().iterator().next();
                var exposedPort = first.getKey();
                var error = first.getValue();
                var localPort = container.getMappedPort(exposedPort);
                var localHost = podEngine.getMappedPortHost(exposedPort);
                throw new IllegalStateException("Socket not listening yet, pod port '" + exposedPort +
                        "', local port forwarding '" + localHost + ":" + localPort + "'", error
                );
            }
        } else {
            log.debug("no exposed ports for waiting");
        }
    }

//    @Override
//    public void setPod(PodEngine<?> pod) {
//        this.podEngine = pod;
//    }
}