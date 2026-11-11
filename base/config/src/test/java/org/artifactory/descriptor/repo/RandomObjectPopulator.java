package org.artifactory.descriptor.repo;

import org.apache.commons.lang3.reflect.FieldUtils;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static java.lang.reflect.Modifier.isFinal;

/**
 * @author Tamar Rosen
 */
public class RandomObjectPopulator {
    private static final Random random = new Random();

    public static <T> T createAndFill(Class<T> clazz) throws Exception {
        T instance;
        try {
            instance = clazz.getDeclaredConstructor().newInstance();
        } catch (InstantiationException e) {
            System.out.println("type: " + clazz);
            throw e;
        }
        for (Field field : FieldUtils.getAllFields(clazz)) {
            if (isFinal(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            Object value = getRandomValueForField(field, instance);
            field.set(instance, value);
        }
        return instance;
    }

    private static <T> Object getRandomValueForField(Field field, T instance) throws Exception {
        Class<?> type = field.getType();
        if (type.isEnum()) {
            Object[] enumValues = type.getEnumConstants();
            return enumValues[random.nextInt(enumValues.length)];
        } else if (type.equals(Integer.TYPE) || type.equals(Integer.class)) {
            return random.nextInt();
        } else if (type.equals(Long.TYPE) || type.equals(Long.class)) {
            return random.nextLong();
        } else if (type.equals(Double.TYPE) || type.equals(Double.class)) {
            return random.nextDouble();
        } else if (type.equals(Float.TYPE) || type.equals(Float.class)) {
            return random.nextFloat();
        } else if (type.equals(String.class)) {
            return UUID.randomUUID().toString();
        } else if (type.equals(BigInteger.class)) {
            return BigInteger.valueOf(random.nextInt());
        } else if (type.equals(Boolean.TYPE) || type.equals(Boolean.class)) {
            return !(Boolean) field.get(instance);
        } else if (type.equals(List.class)) {
            return List.of();
        } else if (type.equals(Map.class)) {
            return Map.of();
        } else if (type.equals(RemoteRepoDescriptor.class)) {
            return null;
        }
        return createAndFill(type);
    }
}
