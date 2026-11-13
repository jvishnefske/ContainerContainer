/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The JSON {@code body} columns, encoded with Jackson. Package-private: the layout is a storage detail.
 * <ul>
 *     <li>users and groups: {@code {"customData":{...}}}</li>
 *     <li>permissions: {@code {"customData":"<opaque resources JSON or null>","userActions":{...},
 *     "groupActions":{...}}}</li>
 * </ul>
 * Unknown properties are ignored when reading, so later versions may add fields.
 */
final class EaJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private EaJson() {
    }

    /**
     * The body of a user or group.
     *
     * @param customData the custom data
     */
    record PrincipalBody(Map<String, String> customData) {
        PrincipalBody {
            customData = customData == null ? Map.of() : customData;
        }
    }

    /**
     * The body of a permission.
     *
     * @param customData   the opaque resources JSON, or {@code null}
     * @param userActions  the actions per user
     * @param groupActions the actions per group
     */
    record PermissionBody(String customData, Map<String, Set<String>> userActions,
            Map<String, Set<String>> groupActions) {
        PermissionBody {
            userActions = userActions == null ? Map.of() : userActions;
            groupActions = groupActions == null ? Map.of() : groupActions;
        }
    }

    static String principalBody(Map<String, String> customData) {
        return write(new PrincipalBody(new TreeMap<>(customData)));
    }

    static Map<String, String> readPrincipalBody(String json) {
        return read(json, PrincipalBody.class).customData();
    }

    static String permissionBody(String customData, Map<String, Set<String>> userActions,
            Map<String, Set<String>> groupActions) {
        return write(new PermissionBody(customData, userActions, groupActions));
    }

    static PermissionBody readPermissionBody(String json) {
        return read(json, PermissionBody.class);
    }

    private static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot encode embedded access body", e);
        }
    }

    private static <T> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot decode embedded access body", e);
        }
    }
}
