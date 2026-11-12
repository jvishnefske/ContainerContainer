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

import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.addon.plugin.groovy.PluginRealmAuthentication;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.security.AuthenticationHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The production {@link WasmPlugin.RequestContext}: the headers of the servlet request bound to the current thread
 * (at most {@value #MAX_HEADERS} names; none outside a request, such as an asynchronous execution) and the plugin
 * realm of the current authentication. {@link WasmPlugin} removes credential headers before a module sees them.
 */
public final class ServletRequestContext implements WasmPlugin.RequestContext {

    /** Most header names passed on. */
    public static final int MAX_HEADERS = 64;

    @Override
    public Map<String, List<String>> headers() {
        HttpServletRequest request = RequestThreadLocal.getRequest();
        if (request == null || request.getHeaderNames() == null) {
            return Map.of();
        }
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (String name : Collections.list(request.getHeaderNames())) {
            if (headers.size() >= MAX_HEADERS) {
                break;
            }
            List<String> values = new ArrayList<>();
            if (request.getHeaders(name) != null) {
                values.addAll(Collections.list(request.getHeaders(name)));
            }
            headers.put(name, List.copyOf(values));
        }
        return headers;
    }

    @Override
    public Optional<AbiCodec.CallerRealm> callerRealm() {
        if (AuthenticationHelper.getAuthentication() instanceof PluginRealmAuthentication realm) {
            return Optional.of(new AbiCodec.CallerRealm(realm.pluginName(), realm.realmName(), realm.expiresAt()));
        }
        return Optional.empty();
    }
}
