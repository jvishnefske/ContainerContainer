package com.jfrog.build.gateway;

import lombok.NonNull;
import org.artifactory.common.ConstantValues;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.artifactory.exception.factory.ExceptionFactory.internalServerException;

public class BuildConfigGateway {

    private final Map<String, ConstantValues> configs = new HashMap<>();

    public BuildConfigGateway() {
        Arrays.stream(ConstantValues.values()).forEach(config -> configs.put(config.getPropertyName(), config));
    }

    @NonNull
    @SuppressWarnings("unchecked")
    public <T> T getArtifactoryConfig(
            @NonNull String key,
            @NonNull Class<T> clazz) {
        if (!configs.containsKey(key)) {
            throw internalServerException("The %s config does not exist", key);
        }
        ConstantValues config = configs.get(key);
        if (clazz == Boolean.class) {
            return (T) (Boolean) config.getBoolean();
        }
        if (clazz == Integer.class) {
            return (T) (Integer) config.getInt();
        }
        if (clazz == Long.class) {
            return (T) (Integer) config.getInt();
        }
        if (clazz == String.class) {
            return (T) (Integer) config.getInt();
        }
        throw internalServerException("The %s for config does not exist", clazz);
    }
}
