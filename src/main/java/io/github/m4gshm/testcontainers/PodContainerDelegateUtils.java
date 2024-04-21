package io.github.m4gshm.testcontainers;

import io.github.m4gshm.testcontainers.wait.PodLogMessageWaitStrategy;
import io.github.m4gshm.testcontainers.wait.PodPortWaitStrategy;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;

import static java.util.Arrays.stream;

@UtilityClass
public class PodContainerDelegateUtils {

    public static <T> T getFieldValue(Object object, String regEx) throws NoSuchFieldException, IllegalAccessException {
        return getFieldValue(object.getClass(), object, regEx);
    }

    private static <T> T getFieldValue(Class<?> type, Object object, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        var regExField = type.getDeclaredField(fieldName);
        regExField.setAccessible(true);
        return (T) regExField.get(object);
    }

    @SneakyThrows
    public static void setContainerFieldValue(Container<?> container, String name, Object value) {
        if (container instanceof GenericContainer<?>) {
            var field = GenericContainer.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(container, value);
        } else {
            throw new IllegalStateException("no field '" + name + "' in container " + container.getClass());
        }
    }

    public static WaitStrategy replacePodWaiters(@NotNull WaitStrategy waitStrategy) {
        try {
            return waitStrategy instanceof LogMessageWaitStrategy
                    ? new PodLogMessageWaitStrategy(getFieldValue(waitStrategy, "regEx"))
                    : waitStrategy instanceof HostPortWaitStrategy
                    ? new PodPortWaitStrategy(getFieldValue(waitStrategy, "ports"))
                    : waitStrategy;
        } catch (NoSuchFieldException e) {
            throw new UnsupportedOperationException("unsupported WaitStrategy type " +
                    waitStrategy.getClass().getSimpleName(), e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("unreadable field of WaitStrategy " +
                    waitStrategy.getClass().getSimpleName(), e);
        }
    }

    @SneakyThrows
    public static Object invokeContainerMethod(Container<?> container, String name, Object... parameters) {
        if (container instanceof GenericContainer<?>) {
            var parameterTypes = stream(parameters).map(Object::getClass).toArray(Class[]::new);
            var method = GenericContainer.class.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method.invoke(container, parameters);
        } else {
            throw new IllegalStateException("no method '" + name + "' in container " + container.getClass());
        }
    }

}
