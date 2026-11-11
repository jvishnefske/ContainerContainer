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

import java.util.Objects;

/**
 * The text of one plugin file, read by the imperative shell and handed to the compiler.
 *
 * @param name     plugin name, the file name without {@code .groovy}
 * @param fileName file name inside the plugins directory
 * @param text     the Groovy source
 */
public record PluginSource(String name, String fileName, String text) {

    /** File extension of plugin scripts. */
    public static final String EXTENSION = ".groovy";

    public PluginSource {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(text, "text");
    }

    /** Creates a source whose plugin name is derived from {@code fileName}. */
    public static PluginSource of(String fileName, String text) {
        return new PluginSource(nameOf(fileName), fileName, text);
    }

    /** @return {@code fileName} without a trailing {@code .groovy} */
    public static String nameOf(String fileName) {
        return fileName.endsWith(EXTENSION)
                ? fileName.substring(0, fileName.length() - EXTENSION.length())
                : fileName;
    }
}
