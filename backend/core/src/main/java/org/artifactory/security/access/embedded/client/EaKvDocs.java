/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import org.artifactory.security.access.embedded.core.EaOutcome;
import org.artifactory.security.access.embedded.store.EaKvEntry;
import org.jfrog.security.crypto.EncryptionWrapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;
import java.util.function.Function;

/**
 * JSON documents in the store's {@code ea_kv} table, written with compare-and-swap ({@code updateKv}).
 * <p>
 * The documents are Access and Artifactory model objects: Lombok value classes with final fields, no default
 * constructor and a generated {@code builder()}. They are written field by field and read back through their
 * builder: the introspector below picks the nested builder of any class that has a static {@code builder()} method
 * and no no-argument constructor, with no prefix on the builder's setters (public builder methods are used, so
 * Lombok {@code @Builder.Default} values survive), so no per-class mapping is needed.
 * Unknown properties are ignored when reading, so a document written by an older model still loads.
 * <p>
 * With an {@link EncryptionWrapper}, values are encrypted before they are stored and decrypted when read
 * (documents stored in clear text still read).
 */
final class EaKvDocs {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
            .setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.PUBLIC_ONLY)
            .setSerializationInclusion(JsonInclude.Include.ALWAYS)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setAnnotationIntrospector(new BuilderIntrospector());

    private final EaRuntime rt;
    private final EncryptionWrapper wrapper;

    EaKvDocs(EaRuntime rt, EncryptionWrapper wrapper) {
        this.rt = rt;
        this.wrapper = wrapper;
    }

    /**
     * Returns a copy that encrypts with a wrapper.
     *
     * @param encryption the wrapper, or {@code null} for clear text
     * @return the copy
     */
    EaKvDocs withEncryption(EncryptionWrapper encryption) {
        return new EaKvDocs(rt, encryption);
    }

    /**
     * Returns the shared mapper's type factory view of a class.
     *
     * @param type the class
     * @return the Jackson type
     */
    static JavaType type(Class<?> type) {
        return MAPPER.getTypeFactory().constructType(type);
    }

    /**
     * Returns the Jackson type of a list of a class.
     *
     * @param element the element class
     * @return the Jackson type
     */
    static JavaType listOf(Class<?> element) {
        return MAPPER.getTypeFactory().constructCollectionType(java.util.List.class, element);
    }

    /**
     * Returns the Jackson type for a type reference.
     *
     * @param reference the reference
     * @return the Jackson type
     */
    static JavaType type(com.fasterxml.jackson.core.type.TypeReference<?> reference) {
        return MAPPER.getTypeFactory().constructType(reference);
    }

    /**
     * Reads a document's raw JSON.
     *
     * @param key the key
     * @return the JSON, or empty
     */
    Optional<String> readRaw(String key) {
        return EaErrors.orThrow(rt.store().getKv(key)).map(EaKvEntry::value).map(this::decrypt);
    }

    /**
     * Reads and binds a document.
     *
     * @param key  the key
     * @param type the type
     * @param <T>  the type
     * @return the value, or empty if absent or JSON {@code null}
     */
    <T> Optional<T> read(String key, JavaType type) {
        return readRaw(key).map(json -> this.<T>bind(json, type));
    }

    /**
     * Writes a document unconditionally (retrying on concurrent writes).
     *
     * @param key   the key
     * @param value the value
     */
    void write(String key, Object value) {
        String json = toJson(value);
        EaErrors.orThrow(rt.store().updateKv(key, current -> EaOutcome.ok(encrypt(json))));
    }

    /**
     * Reads, transforms and writes a document with compare-and-swap. The function may run more than once.
     *
     * @param key    the key
     * @param type   the stored type
     * @param update maps the current value ({@code null} if absent) to the new one
     * @param <T>    the type
     */
    <T> void update(String key, JavaType type, Function<T, Object> update) {
        EaErrors.orThrow(rt.store().updateKv(key, current -> {
            try {
                T old = current.map(this::decrypt).map(json -> this.<T>bind(json, type)).orElse(null);
                return EaOutcome.ok(encrypt(toJson(update.apply(old))));
            } catch (RuntimeException e) {
                return EaOutcome.refused("cannot update " + key + ": " + e.getMessage());
            }
        }));
    }

    /**
     * Deletes a document.
     *
     * @param key the key
     */
    void delete(String key) {
        EaErrors.orThrow(rt.store().deleteKv(key));
    }

    /**
     * Serialises a value.
     *
     * @param value the value
     * @return the JSON
     */
    static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw EaErrors.failure("cannot serialise " + value.getClass().getName(), e);
        }
    }

    /**
     * Binds JSON to a type.
     *
     * @param json the JSON
     * @param type the type
     * @param <T>  the type
     * @return the value, or {@code null} for JSON {@code null}
     */
    @SuppressWarnings("unchecked")
    <T> T bind(String json, JavaType type) {
        try {
            return (T) MAPPER.readValue(json, type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw EaErrors.failure("cannot read stored document as " + type, e);
        }
    }

    private String encrypt(String json) {
        return wrapper == null ? json : wrapper.encryptIfNeeded(json);
    }

    private String decrypt(String stored) {
        return wrapper == null ? stored : wrapper.decryptIfNeeded(stored).getDecryptedData();
    }

    /** Uses the Lombok builder of classes that cannot be built otherwise. */
    private static final class BuilderIntrospector extends JacksonAnnotationIntrospector {

        private static final JsonPOJOBuilder.Value NO_PREFIX = new JsonPOJOBuilder.Value("build", "");

        @Override
        public Class<?> findPOJOBuilder(AnnotatedClass ac) {
            Class<?> declared = super.findPOJOBuilder(ac);
            if (declared != null) {
                return declared;
            }
            return builderOf(ac.getRawType());
        }

        @Override
        public JsonPOJOBuilder.Value findPOJOBuilderConfig(AnnotatedClass ac) {
            JsonPOJOBuilder.Value declared = super.findPOJOBuilderConfig(ac);
            return declared != null ? declared : NO_PREFIX;
        }

        private static Class<?> builderOf(Class<?> type) {
            if (type.isInterface() || type.isArray() || type.isPrimitive() || type.isEnum()
                    || type.getName().startsWith("java.") || hasNoArgConstructor(type)) {
                return null;
            }
            try {
                Method builder = type.getMethod("builder");
                if (Modifier.isStatic(builder.getModifiers())
                        && builder.getReturnType().getEnclosingClass() == type) {
                    return builder.getReturnType();
                }
            } catch (NoSuchMethodException e) {
                return null;
            }
            return null;
        }

        private static boolean hasNoArgConstructor(Class<?> type) {
            for (Constructor<?> c : type.getDeclaredConstructors()) {
                if (c.getParameterCount() == 0) {
                    return true;
                }
            }
            return false;
        }
    }
}
