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

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * An immutable JSON value, kept as its compact text. The manifest {@code config} and plugin {@code state} are held
 * this way, so no mutable Jackson tree is ever shared between calls or threads; the envelope embeds the text as is.
 *
 * @param json compact JSON text of one value (object, array, string, number, boolean or {@code null})
 */
public record JsonText(String json) {

    /** The JSON {@code null} value. */
    public static final JsonText NULL = new JsonText("null");

    public JsonText {
        Objects.requireNonNull(json, "json");
    }

    /** @return the compact text of {@code node}; {@link #NULL} for a missing node */
    public static JsonText of(JsonNode node) {
        return node == null || node.isMissingNode() ? NULL : new JsonText(node.toString());
    }

    /** @return the size of the UTF-8 encoding, the measure of the state cap */
    public int utf8Length() {
        return json.getBytes(StandardCharsets.UTF_8).length;
    }
}
