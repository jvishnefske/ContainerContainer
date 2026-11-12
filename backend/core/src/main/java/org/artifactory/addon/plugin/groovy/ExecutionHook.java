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

package org.artifactory.addon.plugin.groovy;

import groovy.lang.Closure;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The code behind a plugin execution: a Groovy closure or a hosted call, such as a WASM module run by the host.
 * Either way {@link GroovyPluginEngine} applies the same authorization ({@link ExecutionAuthorizer}) and HTTP method
 * rule before running it, in the {@link PluginCallScope.Kind#EXECUTION} scope.
 */
public sealed interface ExecutionHook {

    /** An execution closure, called with {@code (params, body)} and a {@code ResponseCtx} delegate. */
    record Script(Closure<?> closure) implements ExecutionHook {
        public Script {
            Objects.requireNonNull(closure, "closure");
        }
    }

    /** An execution served by the host on behalf of a non-Groovy plugin. */
    record Hosted(Call call) implements ExecutionHook {
        public Hosted {
            Objects.requireNonNull(call, "call");
        }
    }

    /**
     * What a hosted execution is called with.
     *
     * @param name   the execution name
     * @param method the request's HTTP method
     * @param params request parameters
     * @param body   the request body handle, or {@code null} when there is none
     * @param caller who runs it; already authorized
     */
    record Request(String name, String method, Map<String, ?> params, Object body, Caller caller) {
        public Request {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(method, "method");
            params = params == null ? Map.of() : params;
            Objects.requireNonNull(caller, "caller");
        }
    }

    /**
     * A hosted execution's HTTP answer.
     *
     * @param status  100 to 599
     * @param headers response headers by name, already validated by the host
     * @param body    response body, empty for none
     */
    record Response(int status, Map<String, List<String>> headers, byte[] body) {
        public Response {
            if (status < 100 || status > 599) {
                throw new IllegalArgumentException("status " + status + " is not between 100 and 599");
            }
            headers = Map.copyOf(headers);
            body = body.clone();
        }

        /** @return a copy of the body */
        @Override
        public byte[] body() {
            return body.clone();
        }
    }

    /** A hosted execution. Implementations never throw; they answer {@link HostedOutcome.Failed} instead. */
    @FunctionalInterface
    interface Call {

        /** @return the HTTP answer, or why there is none */
        HostedOutcome<Response> call(Request request);
    }
}
