/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.groovy;

import org.artifactory.addon.plugin.pkgtype.PluginApiResponse;
import org.artifactory.resource.ResourceStreamHandle;

import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Converts what an {@code api} closure returns into a {@link PluginApiResponse}. Pure apart from closing a stream
 * of a value it rejects.
 *
 * <p>Accepted values:
 * <ul>
 *     <li>a {@link PluginApiResponse}, used as is;</li>
 *     <li>a map with keys among {@code status} (a number, default 200), {@code headers} (a map from name to a value or
 *         a list of values; values are converted with {@code toString()}) and {@code body}. The body may be
 *         {@code null} (none), a {@code byte[]}, a {@link CharSequence} (sent as UTF-8), a
 *         {@link PluginApiResponse.Body}, a {@link ResourceStreamHandle} (as returned by
 *         {@code repositories.getContent}, streamed with its size and closed afterwards) or a map
 *         {@code [stream: InputStream, size: long]} ({@code size} optional, default {@code -1}).</li>
 * </ul>
 * Anything else, an unknown map key, or a status outside 100 to 599 is {@link Result.Invalid}; a stream carried by
 * the rejected value is closed, so a rejected value never leaks one.
 */
public final class ApiResponseConversion {

    private static final Set<String> KEYS = Set.of("status", "headers", "body");
    private static final Set<String> STREAM_KEYS = Set.of("stream", "size");

    private ApiResponseConversion() {
    }

    /** The conversion result. */
    public sealed interface Result {
        record Valid(PluginApiResponse response) implements Result {
            public Valid {
                Objects.requireNonNull(response, "response");
            }
        }

        record Invalid(String reason) implements Result {
            public Invalid {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    /** Converts a closure's return value. */
    public static Result convert(Object value) {
        if (value instanceof PluginApiResponse response) {
            return new Result.Valid(response);
        }
        if (!(value instanceof Map<?, ?> map)) {
            return new Result.Invalid("api handler returned " + describe(value) +
                    ", expected PluginApiResponse or a [status:, headers:, body:] map");
        }
        Object rawBody = map.get("body");
        try {
            for (Object key : map.keySet()) {
                if (!KEYS.contains(String.valueOf(key))) {
                    return invalid(rawBody, "unknown response key '" + key + "'; expected status, headers, body");
                }
            }
            Object rawStatus = map.containsKey("status") ? map.get("status") : 200;
            if (!(rawStatus instanceof Number number) || number.intValue() != number.doubleValue()) {
                return invalid(rawBody, "status must be an integer, got " + describe(rawStatus));
            }
            int status = number.intValue();
            if (status < 100 || status > 599) {
                return invalid(rawBody, "status must be 100 to 599, got " + status);
            }
            Map<String, List<String>> headers = new LinkedHashMap<>();
            Object rawHeaders = map.get("headers");
            if (rawHeaders != null) {
                if (!(rawHeaders instanceof Map<?, ?> headerMap)) {
                    return invalid(rawBody, "headers must be a map, got " + describe(rawHeaders));
                }
                for (Map.Entry<?, ?> entry : headerMap.entrySet()) {
                    headers.put(String.valueOf(entry.getKey()), values(entry.getValue()));
                }
            }
            Object body = body(rawBody);
            if (body instanceof String reason) {
                return invalid(rawBody, reason);
            }
            return new Result.Valid(new PluginApiResponse(status, headers, (PluginApiResponse.Body) body));
        } catch (RuntimeException e) {
            return invalid(rawBody, "invalid response: " + e.getMessage());
        }
    }

    /** @return the body, or a {@code String} reason why the value is not one */
    private static Object body(Object raw) {
        if (raw == null) {
            return new PluginApiResponse.Body.None();
        }
        if (raw instanceof PluginApiResponse.Body body) {
            return body;
        }
        if (raw instanceof byte[] bytes) {
            return new PluginApiResponse.Body.Bytes(bytes);
        }
        if (raw instanceof CharSequence text) {
            return new PluginApiResponse.Body.Bytes(text.toString().getBytes(StandardCharsets.UTF_8));
        }
        if (raw instanceof ResourceStreamHandle handle) {
            return new PluginApiResponse.Body.Stream(new HandleClosingStream(handle), handle.getSize());
        }
        if (raw instanceof Map<?, ?> map) {
            for (Object key : map.keySet()) {
                if (!STREAM_KEYS.contains(String.valueOf(key))) {
                    return "unknown body key '" + key + "'; expected stream, size";
                }
            }
            if (!(map.get("stream") instanceof InputStream input)) {
                return "body stream must be an InputStream, got " + describe(map.get("stream"));
            }
            Object size = map.containsKey("size") ? map.get("size") : -1L;
            if (!(size instanceof Number number)) {
                return "body size must be a number, got " + describe(size);
            }
            return new PluginApiResponse.Body.Stream(input, number.longValue());
        }
        return "body must be null, byte[], String, ResourceStreamHandle or [stream:, size:], got " + describe(raw);
    }

    private static List<String> values(Object raw) {
        List<String> values = new ArrayList<>();
        if (raw instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                values.add(String.valueOf(Objects.requireNonNull(value, "header value")));
            }
        } else {
            values.add(String.valueOf(Objects.requireNonNull(raw, "header value")));
        }
        return values;
    }

    private static Result invalid(Object rawBody, String reason) {
        closeQuietly(rawBody);
        return new Result.Invalid(reason);
    }

    private static void closeQuietly(Object rawBody) {
        Object closeable = rawBody instanceof Map<?, ?> map ? map.get("stream") : rawBody;
        if (rawBody instanceof PluginApiResponse.Body.Stream stream) {
            closeable = stream.input();
        }
        if (closeable instanceof Closeable c) {
            try {
                c.close();
            } catch (IOException | RuntimeException ignored) {
                // nothing more to do for a value already rejected
            }
        }
    }

    private static String describe(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    /** Streams a handle's content and closes the handle with the stream. */
    static final class HandleClosingStream extends FilterInputStream {
        private final ResourceStreamHandle handle;

        HandleClosingStream(ResourceStreamHandle handle) {
            super(handle.getInputStream());
            this.handle = handle;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                handle.close();
            }
        }
    }
}
