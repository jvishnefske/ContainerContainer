/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import com.fasterxml.jackson.core.type.TypeReference;
import org.jfrog.access.client.platformconfig.ConfigurationModel;
import org.jfrog.security.crypto.EncryptionWrapper;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * The {@code cachedGrpcPlatformConfigs()} surface: platform configuration documents, one {@code ea_kv} row per key
 * ({@value #KEY_PREFIX}{@code <key>}), JSON, written with compare-and-swap.
 * <p>
 * A document written with {@code secret == true} is encrypted with the client's encryption wrapper when one was
 * configured; reads decrypt transparently. Feature flags are not stored: every flag answers its default.
 * Pattern queries and update hooks are not implemented and fail closed.
 */
final class EaPlatformConfigClient {

    /** The {@code ea_kv} key prefix of platform configuration documents. */
    static final String KEY_PREFIX = "platform.";

    private final EaKvDocs plain;
    private final EaKvDocs secret;

    EaPlatformConfigClient(EaKvDocs docs, EncryptionWrapper secretWrapper) {
        this.plain = docs;
        this.secret = secretWrapper == null ? docs : docs.withEncryption(secretWrapper);
    }

    /**
     * Reads a document.
     *
     * @param key  the key
     * @param type the model class
     * @param <T>  the model type
     * @return the value, or empty
     */
    public <T extends ConfigurationModel> Optional<T> getConfig(String key, Class<T> type) {
        return secret.read(KEY_PREFIX + key, EaKvDocs.type(type));
    }

    /**
     * Reads a list document.
     *
     * @param key  the key
     * @param type the list type
     * @param <T>  the element type
     * @return the list, empty if absent
     */
    public <T extends ConfigurationModel> List<T> getConfig(String key, TypeReference<List<T>> type) {
        Optional<List<T>> value = secret.read(KEY_PREFIX + key, EaKvDocs.type(type));
        return value.orElseGet(List::of);
    }

    /**
     * Reads a document's JSON and converts it with a caller function.
     *
     * @param key    the key
     * @param parser converts the stored JSON
     * @param <T>    the model type
     * @return the value, or empty
     */
    public <T extends ConfigurationModel> Optional<T> getConfig(String key, Function<String, T> parser) {
        return secret.readRaw(KEY_PREFIX + key).map(parser);
    }

    /**
     * Deletes a document.
     *
     * @param key the key
     */
    public void deleteConfig(String key) {
        plain.delete(KEY_PREFIX + key);
    }

    /**
     * Updates a document with compare-and-swap; the operator receives {@code null} when it is absent and may run
     * more than once.
     *
     * @param key      the key
     * @param operator computes the new value
     * @param type     the stored class
     * @param isSecret whether to encrypt
     * @param <T>      the type
     */
    public <T> void updateConfig(String key, UnaryOperator<T> operator, Class<T> type, boolean isSecret) {
        docs(isSecret).<T>update(KEY_PREFIX + key, EaKvDocs.type(type), operator::apply);
    }

    /**
     * Writes a document.
     *
     * @param key      the key
     * @param value    the value
     * @param isSecret whether to encrypt
     */
    public void setConfig(String key, Object value, boolean isSecret) {
        docs(isSecret).write(KEY_PREFIX + key, value);
    }

    /**
     * Writes a document; the extra flag (cluster-local in Access) has no meaning on a single node.
     *
     * @param key      the key
     * @param value    the value
     * @param isSecret whether to encrypt
     * @param local    ignored
     */
    public void setConfig(String key, Object value, boolean isSecret, boolean local) {
        setConfig(key, value, isSecret);
    }

    /**
     * Answers a feature flag's default.
     *
     * @param key          the flag
     * @param defaultValue the default
     * @return {@code defaultValue}
     */
    public boolean getBooleanFeatureFlag(String key, boolean defaultValue) {
        return defaultValue;
    }

    /**
     * Answers a feature flag's default.
     *
     * @param key          the flag
     * @param defaultValue the default
     * @return {@code defaultValue}
     */
    public String getStringFeatureFlag(String key, String defaultValue) {
        return defaultValue;
    }

    /**
     * Answers a feature flag's default.
     *
     * @param key          the flag
     * @param defaultValue the default
     * @return {@code defaultValue}
     */
    public int getIntFeatureFlag(String key, int defaultValue) {
        return defaultValue;
    }

    private EaKvDocs docs(boolean isSecret) {
        return isSecret ? secret : plain;
    }
}
