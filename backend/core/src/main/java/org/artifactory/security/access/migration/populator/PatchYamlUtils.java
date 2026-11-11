package org.artifactory.security.access.migration.populator;

import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author morm
 */
@UtilityClass
public class PatchYamlUtils {

    public static <T> boolean populateValueFromMap(String key, Consumer<T> consumer, Function<Object, T> castFunc,
            Map<Object, Object> map) {
        boolean populated = false;
        if (map.containsKey(key)) {
            consumer.accept(castFunc.apply(map.get(key)));
            map.remove(key);
            populated = true;
        }
        return populated;
    }

    public static Function<Object, Boolean> getParseBooleanFunc() {
        return (str) -> Boolean.parseBoolean(String.valueOf(str));
    }
}
