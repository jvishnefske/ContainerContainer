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

import org.artifactory.addon.plugin.ResponseCtx;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

/**
 * The response of a hosted execution ({@link ExecutionHook.Hosted}): status, body and response headers. Unlike the
 * {@link ResponseCtx} of a Groovy execution, whose {@code headers} map has never been sent to the client (and still
 * is not, so Groovy executions behave as before), the REST layer sends {@link #responseHeaders()} with the response.
 * The headers were validated by the host that produced them (names are tokens, values carry no line breaks, and
 * framing, cookie and authentication headers are refused).
 */
public final class HostedResponseCtx extends ResponseCtx {

    private final Map<String, List<String>> responseHeaders;

    /** Copies {@code response} into a context whose body stream is the response body. */
    public HostedResponseCtx(ExecutionHook.Response response) {
        byte[] body = response.body();
        setStatus(response.status());
        setInputStream(new ByteArrayInputStream(body));
        setSize(body.length);
        this.responseHeaders = Map.copyOf(response.headers());
    }

    /** @return the response headers to send, by name; unmodifiable */
    public Map<String, List<String>> responseHeaders() {
        return responseHeaders;
    }
}
