/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.pkgtype;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The response a {@link PluginApiHandler} returns. Immutable: the {@code with...} methods return new values.
 *
 * <p>How the shell sends it:
 * <ul>
 *     <li>status and headers as given; header names keep their case, and a name given twice in different case is
 *         one header. Names and values must not contain line breaks;</li>
 *     <li>{@code Content-Length} is the body's size when it is known ({@link Body.Bytes}, or {@link Body.Stream}
 *         with a size), replacing a handler-set value; for {@link Body.None} or an unsized stream, a
 *         handler-set {@code Content-Length} is sent as is, which is how a HEAD response advertises the size of
 *         content it does not send; {@link Body.None} without one is sent with {@code Content-Length: 0} except
 *         for HEAD; {@code Transfer-Encoding} set by the handler is dropped (the container decides it);</li>
 *     <li>a body without a {@code Content-Type} header is sent as {@code application/octet-stream};</li>
 *     <li>for a HEAD request only the status and headers are sent and a stream body is closed unread;</li>
 *     <li>a {@link Body.Stream} is copied to the client without buffering and always closed.</li>
 * </ul>
 *
 * @param status  HTTP status, 100 to 599
 * @param headers response headers, values in order
 * @param body    the body
 */
public record PluginApiResponse(int status, Map<String, List<String>> headers, Body body) {

    /** The body of a response: none, a byte array or a stream. */
    public sealed interface Body {

        /** The size in bytes when known, {@code -1} otherwise. */
        long size();

        /** No body. */
        record None() implements Body {
            @Override
            public long size() {
                return 0;
            }
        }

        /** A body held in memory, for small documents. The array is copied in and out. */
        record Bytes(byte[] content) implements Body {
            public Bytes {
                content = Objects.requireNonNull(content, "content").clone();
            }

            @Override
            public byte[] content() {
                return content.clone();
            }

            @Override
            public long size() {
                return content.length;
            }

            @Override
            public boolean equals(Object other) {
                return other instanceof Bytes bytes && Arrays.equals(content, bytes.content);
            }

            @Override
            public int hashCode() {
                return Arrays.hashCode(content);
            }

            @Override
            public String toString() {
                return "Bytes[" + content.length + "]";
            }
        }

        /**
         * A streamed body. The shell copies it to the client and closes it.
         *
         * @param input the content
         * @param size  its length in bytes, {@code -1} when unknown (sent without {@code Content-Length})
         */
        record Stream(InputStream input, long size) implements Body {
            public Stream {
                Objects.requireNonNull(input, "input");
                if (size < -1) {
                    throw new IllegalArgumentException("size must be -1 (unknown) or more, got " + size);
                }
            }
        }
    }

    public PluginApiResponse {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("status must be 100 to 599, got " + status);
        }
        headers = copyHeaders(Objects.requireNonNull(headers, "headers"));
        Objects.requireNonNull(body, "body");
    }

    /** A response with {@code status}, no headers and no body. */
    public static PluginApiResponse status(int status) {
        return new PluginApiResponse(status, Map.of(), new Body.None());
    }

    /** {@code status} with {@code content} as the body and the given content type. */
    public static PluginApiResponse bytes(int status, String contentType, byte[] content) {
        return status(status).withHeader("Content-Type", contentType).withBody(new Body.Bytes(content));
    }

    /** {@code status} with UTF-8 {@code text} as the body and the given content type. */
    public static PluginApiResponse text(int status, String contentType, String text) {
        return bytes(status, contentType, text.getBytes(StandardCharsets.UTF_8));
    }

    /** {@code status} with a JSON document as the body ({@code application/json}). */
    public static PluginApiResponse json(int status, String json) {
        return text(status, "application/json", json);
    }

    /** {@code status} with a streamed body of {@code size} bytes ({@code -1} when unknown). */
    public static PluginApiResponse stream(int status, InputStream input, long size) {
        return status(status).withBody(new Body.Stream(input, size));
    }

    /** @return this response with {@code value} added to header {@code name} */
    public PluginApiResponse withHeader(String name, String value) {
        Map<String, List<String>> next = new LinkedHashMap<>(headers);
        String key = existingName(next, Objects.requireNonNull(name, "name")).orElse(name);
        List<String> values = new ArrayList<>(next.getOrDefault(key, List.of()));
        values.add(Objects.requireNonNull(value, "value of " + name));
        next.put(key, values);
        return new PluginApiResponse(status, next, body);
    }

    /** @return this response with {@code value} as the body */
    public PluginApiResponse withBody(Body value) {
        return new PluginApiResponse(status, headers, value);
    }

    /** @return this response with status {@code value} */
    public PluginApiResponse withStatus(int value) {
        return new PluginApiResponse(value, headers, body);
    }

    /** @return the first value of header {@code name}, matched ignoring case */
    public Optional<String> header(String name) {
        return existingName(headers, name).map(key -> headers.get(key).get(0));
    }

    private static Optional<String> existingName(Map<String, List<String>> headers, String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return headers.keySet().stream().filter(key -> key.toLowerCase(Locale.ROOT).equals(lower)).findFirst();
    }

    private static boolean hasLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    private static Map<String, List<String>> copyHeaders(Map<String, List<String>> source) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        source.forEach((name, values) -> {
            Objects.requireNonNull(name, "header name");
            if (name.isBlank() || hasLineBreak(name)) {
                throw new IllegalArgumentException("header name must not be blank or contain line breaks: " + name);
            }
            String key = existingName(result, name).orElse(name);
            List<String> merged = new ArrayList<>(result.getOrDefault(key, List.of()));
            for (String value : Objects.requireNonNull(values, "values of " + name)) {
                if (hasLineBreak(Objects.requireNonNull(value, "value of " + name))) {
                    throw new IllegalArgumentException("value of header " + name + " must not contain line breaks");
                }
                merged.add(value);
            }
            result.put(key, List.copyOf(merged));
        });
        return Collections.unmodifiableMap(result);
    }
}
