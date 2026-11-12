/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
 * Modifications copyright (C) 2026 the fork authors.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.wasm;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The result of a performed effect, fed back to the module in the next step's {@code effects}. A failure the module
 * can react to (a refused connection, a non-issued token) is a result with {@code ok: false} and an {@code error};
 * an effect the policy refuses never gets this far, it fails the call.
 */
public sealed interface EffectResult {

    /**
     * The result of an {@code http_get}.
     *
     * @param ok      whether a response was received in time and within the size cap
     * @param status  the HTTP status, 0 when there is no response
     * @param headers response headers by lower-case name
     * @param body    the response body, empty when there is none
     * @param error   why there is no usable response; empty when {@code ok}
     */
    record HttpGet(boolean ok, int status, Map<String, List<String>> headers, byte[] body, String error)
            implements EffectResult {
        public HttpGet {
            headers = Map.copyOf(headers);
            body = body.clone();
            Objects.requireNonNull(error, "error");
        }

        /** @return a failed result with {@code error} */
        public static HttpGet failed(String error) {
            return new HttpGet(false, 0, Map.of(), new byte[0], error);
        }

        /** @return a copy of the body */
        @Override
        public byte[] body() {
            return body.clone();
        }
    }

    /**
     * The result of a {@code mint_access_token}.
     *
     * @param ok          whether a token was issued
     * @param accessToken the token, empty when not issued
     * @param expiresIn   its lifetime in seconds, 0 when not issued
     * @param error       why it was not issued; empty when {@code ok}
     */
    record Mint(boolean ok, String accessToken, long expiresIn, String error) implements EffectResult {
        public Mint {
            Objects.requireNonNull(accessToken, "accessToken");
            Objects.requireNonNull(error, "error");
        }

        /** Never prints the token. */
        @Override
        public String toString() {
            return "Mint[ok=" + ok + ", expiresIn=" + expiresIn + ", error=" + error + "]";
        }
    }
}
