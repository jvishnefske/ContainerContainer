package org.artifactory.util;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.jfrog.common.ResourceUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExtendedJarInputStreamTest {

    private static final List<String> expectedFileNames = List.of("1", "2");

    @ParameterizedTest
    @ValueSource(strings = {"/regular.war", "/executable.war"})
    void testArchives(String resource) throws IOException {
        InputStream inputStream = ResourceUtils.getResource(resource);
        List<String> actualFileNames = new ArrayList<>();
        try (ExtendedJarInputStream zipInputStream = new ExtendedJarInputStream(inputStream)) {
            ArchiveEntry archiveEntry;
            while ((archiveEntry = zipInputStream.getNextEntry()) != null) {
                actualFileNames.add(archiveEntry.getName());
            }
        }
        assertEquals(expectedFileNames, actualFileNames);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/empty.jar", "/exec_empty.jar"})
    void testEmptyArchives(String resource) throws IOException {
        InputStream inputStream = ResourceUtils.getResource(resource);
        List<String> actualFileNames = new ArrayList<>();
        try (ExtendedJarInputStream zipInputStream = new ExtendedJarInputStream(inputStream)) {
            ArchiveEntry archiveEntry;
            while ((archiveEntry = zipInputStream.getNextEntry()) != null) {
                actualFileNames.add(archiveEntry.getName());
            }
        }
        assertEquals(List.of(), actualFileNames);
    }
}