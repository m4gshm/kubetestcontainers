package io.github.m4gshm.testcontainers;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.dockerjava.api.command.CreateContainerCmd;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY;
import static com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS;
import static java.lang.reflect.Proxy.newProxyInstance;

@UtilityClass
public class PodContainerUtils {
    public static JsonMapper config(JsonMapper jsonMapper) {
        return jsonMapper.rebuild()
                .enable(SORT_PROPERTIES_ALPHABETICALLY)
                .enable(ORDER_MAP_ENTRIES_BY_KEYS)
                .build();
    }
    public static @NotNull CreateContainerCmd newCreateContainerCmd(ClassLoader classLoader, PodBuilderFactory podBuilderFactory) {
        return (CreateContainerCmd) newProxyInstance(classLoader, new Class[]{CreateContainerCmd.class}, (
                proxy, method, args
        ) -> {
            var methodName = method.getName();
            return switch (methodName) {
                case "withEntrypoint" -> {
                    var firstArg = args[0];
                    if (firstArg instanceof String[] strings) {
                        podBuilderFactory.setEntryPoint(List.of(strings));
                    } else if (firstArg instanceof List<?> list) {
                        podBuilderFactory.setEntryPoint(list.stream().map(String::valueOf).toList());
                    }
                    yield null;
                }
                default -> throw new UnsupportedOperationException(methodName);
            };
        });
    }
}
