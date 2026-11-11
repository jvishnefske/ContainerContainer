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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reads plugin files from the plugins directory. This is the file-system edge of the engine.
 *
 * <p>Only regular files directly inside the directory whose names end in {@code .groovy} are read
 * (sub-directories such as {@code lib/} or {@code examples/} are not scanned). Sources are returned sorted
 * by file name, which fixes the load and hook firing order. A file that cannot be read becomes a
 * {@link PluginLoadResult.Failed} instead of aborting the scan; a missing directory yields no plugins.
 */
public final class PluginSources {

    private PluginSources() {
    }

    /**
     * The result of scanning the plugins directory.
     *
     * @param sources  readable plugin files, sorted by file name
     * @param failures files that matched but could not be read, or a failure to list the directory
     */
    public record Scan(List<PluginSource> sources, List<PluginLoadResult.Failed> failures) {
        public Scan {
            sources = List.copyOf(sources);
            failures = List.copyOf(failures);
        }
    }

    /** Scans {@code dir}; never throws. */
    public static Scan scan(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return new Scan(List.of(), List.of());
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*" + PluginSource.EXTENSION)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    files.add(file);
                }
            }
        } catch (IOException | RuntimeException e) {
            return new Scan(List.of(), List.of(new PluginLoadResult.Failed("*", dir.toString(),
                    "Cannot list plugins directory: " + e.getMessage())));
        }
        files.sort(Comparator.comparing(file -> file.getFileName().toString()));
        List<PluginSource> sources = new ArrayList<>();
        List<PluginLoadResult.Failed> failures = new ArrayList<>();
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            try {
                sources.add(PluginSource.of(fileName, Files.readString(file, StandardCharsets.UTF_8)));
            } catch (IOException | RuntimeException e) {
                failures.add(new PluginLoadResult.Failed(PluginSource.nameOf(fileName), fileName,
                        "Cannot read plugin file: " + e.getMessage()));
            }
        }
        return new Scan(sources, failures);
    }
}
