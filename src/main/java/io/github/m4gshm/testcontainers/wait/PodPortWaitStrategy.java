package io.github.m4gshm.testcontainers.wait;

import io.github.m4gshm.testcontainers.PodAware;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;

import static java.time.Duration.ofSeconds;
import static java.util.stream.Stream.ofNullable;


@Builder
@Slf4j
@AllArgsConstructor
public class PodPortWaitStrategy extends AbstractWaitStrategy {
    private int[] ports;

    public PodPortWaitStrategy() {
        this.startupTimeout = ofSeconds(10);
    }

    @Override
    protected void waitUntilReady() {
        var podEngine = waitStrategyTarget instanceof PodAware podAware ? podAware.getPod() : null;

        var timeout = startupTimeout.toMillis();
        var ports = ofNullable(this.ports).flatMapToInt(Arrays::stream).boxed().toList();
        var exposedPorts = !ports.isEmpty() ? ports : waitStrategyTarget.getExposedPorts();
        var portsCount = exposedPorts.size();
        if (portsCount > 0) {
            var errors = new HashMap<Integer, IOException>(portsCount);
            var onePortTimeout = timeout / portsCount;
            for (var exposedPort : exposedPorts) {
                var localPort = waitStrategyTarget.getMappedPort(exposedPort);
                var localHost = podEngine != null ? podEngine.getMappedPortHost(exposedPort) : "localhost";
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
                var localPort = waitStrategyTarget.getMappedPort(exposedPort);
                var localHost = podEngine != null ? podEngine.getMappedPortHost(exposedPort) : "localhost";
                throw new IllegalStateException("Socket not listening yet, pod port '" + exposedPort +
                        "', local port forwarding '" + localHost + ":" + localPort + "'", error
                );
            }
        } else {
            log.debug("no exposed ports for waiting");
        }
    }

    public PodPortWaitStrategy forPorts(int... ports) {
        this.ports = ports;
        return this;
    }
}