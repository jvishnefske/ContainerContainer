/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.maven;

import org.artifactory.api.module.ModuleInfo;
import org.artifactory.api.module.ModuleInfoUtils;
import org.artifactory.api.repo.exception.maven.BadPomException;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.util.RepoLayoutUtils;
import org.jfrog.common.ResourceUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Noam Y. Tenne
 */

class PomTargetPathValidatorTest extends ArtifactoryHomeBoundJUnitTest {

    @Test
    void validatePomTargetValidPath() throws IOException {
        InputStream inputStream = ResourceUtils.getResource("/org/artifactory/maven/yourpit-1.0.0-alpha2.pom");

        String path = "org/yourpit/yourpit/1.0.0-alpha2";

        ModuleInfo moduleInfo = ModuleInfoUtils.moduleInfoFromDescriptorPath(path, RepoLayoutUtils.MAVEN_2_DEFAULT);


        PomTargetPathValidator validator = new PomTargetPathValidator(path, moduleInfo);
        validator.validate(inputStream, false);
        assertFalse(validator.isMavenPlugin(), "Not expected detection of maven plugin");
    }

    @Test
    void validatePomTargetValidPathISO8859() throws IOException {
        InputStream inputStream = ResourceUtils.getResource("/org/artifactory/maven/all-1.6.2.pom");
        String path = "com/sun/mail/all/1.6.2";

        ModuleInfo moduleInfo = ModuleInfoUtils.moduleInfoFromDescriptorPath(path, RepoLayoutUtils.MAVEN_2_DEFAULT);


        PomTargetPathValidator validator = new PomTargetPathValidator(path, moduleInfo);
        validator.validate(inputStream, false);
        assertFalse(validator.isMavenPlugin(), "Not expected detection of maven plugin");
    }

    @Test
    void validatePomTargetValidPathUTF8() throws IOException {
        InputStream inputStream = ResourceUtils.getResource("/org/artifactory/maven/utf8-1.0.0.pom");
        String path =
                "\uD80C\uDC80/\uD80C\uDE50/\uD80C\uDE96/\uD80C\uDE83\uD80C\uDC00\uD80C\uDDF3/1.0" +
                        ".0-\uD80C\uDFE4\uD80C\uDD91\uD80C\uDCA7";

        ModuleInfo moduleInfo = ModuleInfoUtils.moduleInfoFromDescriptorPath(path, RepoLayoutUtils.MAVEN_2_DEFAULT);


        PomTargetPathValidator validator = new PomTargetPathValidator(path, moduleInfo);
        validator.validate(inputStream, false);
        assertFalse(validator.isMavenPlugin(), "Not expected detection of maven plugin");
    }

    @Test
    void validate_whenXmlIsMalformedAndNotSuppressing_shouldThrowIOException() {
        String path = "com/sun/mail/all/1.6.2";
        ModuleInfo moduleInfo = ModuleInfoUtils.moduleInfoFromDescriptorPath(path, RepoLayoutUtils.MAVEN_2_DEFAULT);

        PomTargetPathValidator validator = new PomTargetPathValidator(path, moduleInfo);

        String xml = "<artifact />";
        InputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        boolean suppressPomConsistencyChecks = false;

        // We expect an IOException because handleXmlPullException (called by the catch block)
        // should throw one when suppressPomConsistencyChecks is false.
        IOException thrownException = assertThrows(IOException.class, () -> {
            validator.validate(inputStream, suppressPomConsistencyChecks);
        }, "IOException should be thrown for empty XML when not suppressing checks.");

        // 1. Verify that the cause is indeed a BadPomException
        assertInstanceOf(BadPomException.class, thrownException);

        // 2. Verify the message of the BadPomException
        String causeMessage = thrownException.getMessage();
        assertTrue(causeMessage.contains("Expected root element 'project'"), // More specific part of the message

                "The XmlPullParserException message should indicate a missing root element. Actual: " + causeMessage);

    }

    @Test
    void validatePomWithDots() {
        InputStream inputStream = ResourceUtils.getResource("/org/artifactory/maven/yourpit-1.0.0-alpha2.pom");

        String path = "org.jfrog/yourpit/1.0.0-alpha2";

        ModuleInfo moduleInfo = ModuleInfoUtils.moduleInfoFromDescriptorPath(path, RepoLayoutUtils.MAVEN_2_DEFAULT);


        PomTargetPathValidator validator = new PomTargetPathValidator(path, moduleInfo);
        assertThrows(BadPomException.class, () -> validator.validate(inputStream, false));
        assertFalse(validator.isMavenPlugin(), "Not expected detection of maven plugin");
    }

    @Test
    void validatePomTargetInValidPath() {
        InputStream inputStream = ResourceUtils.getResource("/org/artifactory/maven/yourpit-1.0.0-alpha2.pom");

        String path = "blab/bla/1.0.0-alpha2";

        ModuleInfo moduleInfo = ModuleInfoUtils.moduleInfoFromDescriptorPath(path, RepoLayoutUtils.MAVEN_2_DEFAULT);


        assertThrows(BadPomException.class,
                () -> new PomTargetPathValidator(path, moduleInfo).validate(inputStream, false));
    }

    @Test
    void validatePluginPom() throws IOException {
        InputStream inputStream = ResourceUtils.getResource("/org/artifactory/maven/maven-null-plugin.pom");
        String path = "maven/maven-null-plugin/1.4";

        ModuleInfo moduleInfo = ModuleInfoUtils.moduleInfoFromDescriptorPath(path, RepoLayoutUtils.MAVEN_2_DEFAULT);


        PomTargetPathValidator validator = new PomTargetPathValidator(path, moduleInfo);
        validator.validate(inputStream, false);

        assertTrue(validator.isMavenPlugin(), "Expected detection of maven plugin");
    }

    @Test
    void validateNoVersion() {
        String path = "org/yourpit/yourpit/1.0.0-alpha2";
        String data = """
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd" child.project.url.inherit.append.path="false">
                    <groupId>org.yourpit</groupId>
                    <artifactId>yourpit</artifactId>
                </project>
                """;
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        assertThrows(BadPomException.class,
                () -> new PomTargetPathValidator(path, new ModuleInfo()).validate(inputStream, false));

    }

    @Test
    void validateNoVersionSuprressed() throws IOException {
        String path = "org/yourpit/yourpit/1.0.0-alpha2";
        String data = """
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd" child.project.url.inherit.append.path="false">
                    <groupId>org.yourpit</groupId>
                    <artifactId>yourpit</artifactId>
                </project>
                """;
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        new PomTargetPathValidator(path, new ModuleInfo()).validate(inputStream, true);
    }

    @Test
    void validateCorruptedXml() {
        String path = "org/yourpit/yourpit/1.0.0-alpha2";
        ByteArrayInputStream inputStream = new ByteArrayInputStream("<html></html>".getBytes(StandardCharsets.UTF_8));

        assertThrows(BadPomException.class,
                () -> new PomTargetPathValidator(path, new ModuleInfo()).validate(inputStream, false));
    }

    @Test
    void validateCorruptedXmlSuppressed() throws IOException {
        String path = "org/yourpit/yourpit/1.0.0-alpha2";
        ByteArrayInputStream inputStream = new ByteArrayInputStream("<html></html>".getBytes(StandardCharsets.UTF_8));

        new PomTargetPathValidator(path, new ModuleInfo())
                .validate(inputStream, true);
    }

    @Test
    void validateGroupFromParent() throws IOException {
        String path = "org/yourpit/yourpit/1.0.0-alpha2";
        String data = """
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd" child.project.url.inherit.append.path="false">
                    <artifactId>yourpit</artifactId>
                    <version>1.0.0-alpha2</version>
                    <parent>
                        <groupId>org.yourpit</groupId>
                    </parent>
                </project>
                """;
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        new PomTargetPathValidator(path, new ModuleInfo())
                .validate(inputStream, false);
    }

    @ParameterizedTest
    @MethodSource("provideStringsForReadString")
    void testReadString(String input) throws IOException {
        String path = "org/yourpit/yourpit/1.0.0-alpha2";
        ModuleInfo moduleInfo = ModuleInfoUtils.moduleInfoFromDescriptorPath(path, RepoLayoutUtils.MAVEN_2_DEFAULT);

        PomTargetPathValidator validator = new PomTargetPathValidator(path, moduleInfo);
        Reader reader = new StringReader(input);
        String result = validator.readString(reader);
        assertEquals(input, result);
    }

    static Stream<Arguments> provideStringsForReadString() {
        return Stream.of(
                Arguments.of("Hello, world!"),
                Arguments.of(""),
                Arguments.of("Hello,\nworld!\t")
        );
    }

    // New tests for CleanedXmlInputStream functionality
    @Test
    void testValidateWithLeadingEmptyLines() throws Exception {
        // Create a POM with leading empty lines (similar to the problematic POM mentioned)
        String pomWithLeadingEmptyLines = "\n\n" +
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "    <modelVersion>4.0.0</modelVersion>\n" +
                "    <groupId>jfrog.rt.test</groupId>\n" +
                "    <artifactId>jfrog-app</artifactId>\n" +
                "    <version>1.7048.0</version>\n" +
                "</project>";

        InputStream inputStream = new ByteArrayInputStream(pomWithLeadingEmptyLines.getBytes(StandardCharsets.UTF_8));

        String relPath = "jfrog/rt/test/jfrog-app/1.7048.0";
        ModuleInfo moduleInfo = ModuleInfoUtils.moduleInfoFromDescriptorPath(relPath, RepoLayoutUtils.MAVEN_2_DEFAULT);

        PomTargetPathValidator validator = new PomTargetPathValidator(relPath, moduleInfo);

        // This should not throw an exception now that we're cleaning the XML
        validator.validate(inputStream, false);

        // If we get here without an exception, the cleaning worked
        assertFalse(validator.isMavenPlugin(), "Not expected detection of maven plugin");
    }

    @Test
    void testValidateWithLeadingWhitespace() throws Exception {
        // Create a POM with leading whitespace
        String pomWithLeadingWhitespace = "   \t\n" +
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "    <modelVersion>4.0.0</modelVersion>\n" +
                "    <groupId>jfrog.rt.test</groupId>\n" +
                "    <artifactId>jfrog-app</artifactId>\n" +
                "    <version>1.7048.0</version>\n" +
                "</project>";

        InputStream inputStream = new ByteArrayInputStream(pomWithLeadingWhitespace.getBytes(StandardCharsets.UTF_8));

        String relPath = "jfrog/rt/test/jfrog-app/1.7048.0";
        ModuleInfo moduleInfo = ModuleInfoUtils.moduleInfoFromDescriptorPath(relPath, RepoLayoutUtils.MAVEN_2_DEFAULT);

        PomTargetPathValidator validator = new PomTargetPathValidator(relPath, moduleInfo);

        // This should not throw an exception now that we're cleaning the XML
        validator.validate(inputStream, false);

        // If we get here without an exception, the cleaning worked
        assertFalse(validator.isMavenPlugin(), "Not expected detection of maven plugin");
    }

    @Test
    void testValidateWithSuppressedChecks() throws Exception {
        // Test that when suppressPomConsistencyChecks is true, we don't use CleanedXmlInputStream
        String pomWithLeadingWhitespace = "   \t\n" +
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "    <modelVersion>4.0.0</modelVersion>\n" +
                "    <groupId>jfrog.rt.test</groupId>\n" +
                "    <artifactId>jfrog-app</artifactId>\n" +
                "    <version>1.7048.0</version>\n" +
                "</project>";

        InputStream inputStream = new ByteArrayInputStream(pomWithLeadingWhitespace.getBytes(StandardCharsets.UTF_8));

        String relPath = "jfrog/rt/test/jfrog-app/1.7048.0";
        ModuleInfo moduleInfo = ModuleInfoUtils.moduleInfoFromDescriptorPath(relPath, RepoLayoutUtils.MAVEN_2_DEFAULT);

        PomTargetPathValidator validator = new PomTargetPathValidator(relPath, moduleInfo);

        // This should not throw an exception even with suppressed checks
        validator.validate(inputStream, true); // suppressPomConsistencyChecks = true

        // If we get here without an exception, the validation worked
        assertFalse(validator.isMavenPlugin(), "Not expected detection of maven plugin");
    }

    @Test
    void testValidateWithEmptyGroupId() throws Exception {
        // Test POM without groupId (should skip validation)
        String pomWithoutGroupId = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "    <modelVersion>4.0.0</modelVersion>\n" +
                "    <artifactId>jfrog-app</artifactId>\n" +
                "    <version>1.7048.0</version>\n" +
                "</project>";

        InputStream inputStream = new ByteArrayInputStream(pomWithoutGroupId.getBytes(StandardCharsets.UTF_8));

        String relPath = "jfrog/rt/test/jfrog-app/1.7048.0";
        ModuleInfo moduleInfo = ModuleInfoUtils.moduleInfoFromDescriptorPath(relPath, RepoLayoutUtils.MAVEN_2_DEFAULT);

        PomTargetPathValidator validator = new PomTargetPathValidator(relPath, moduleInfo);

        // This should not throw an exception since validation is skipped for empty groupId
        validator.validate(inputStream, false);

        assertFalse(validator.isMavenPlugin(), "Not expected detection of maven plugin");
    }

    @Test
    void testValidateSnapshotWithVersionSnapshot() throws Exception {
        // Test snapshot with version snapshot (should include version in path)
        String pomWithSnapshot = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "    <modelVersion>4.0.0</modelVersion>\n" +
                "    <groupId>org.test</groupId>\n" +
                "    <artifactId>test-app</artifactId>\n" +
                "    <version>1.0-SNAPSHOT</version>\n" +
                "</project>";

        InputStream inputStream = new ByteArrayInputStream(pomWithSnapshot.getBytes(StandardCharsets.UTF_8));

        String relPath = "org/test/test-app/1.0-SNAPSHOT";
        ModuleInfo moduleInfo = ModuleInfoUtils.moduleInfoFromDescriptorPath(relPath, RepoLayoutUtils.MAVEN_2_DEFAULT);

        PomTargetPathValidator validator = new PomTargetPathValidator(relPath, moduleInfo);

        // This should not throw an exception
        validator.validate(inputStream, false);

        assertFalse(validator.isMavenPlugin(), "Not expected detection of maven plugin");
    }

    @Test
    void testValidateWithPropertyReferencesInPath() throws Exception {
        // Test POM with property references (should skip path validation)
        String pomWithProperties = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "    <modelVersion>4.0.0</modelVersion>\n" +
                "    <groupId>org.test</groupId>\n" +
                "    <artifactId>test-app</artifactId>\n" +
                "    <version>${project.version}</version>\n" +
                "</project>";

        InputStream inputStream = new ByteArrayInputStream(pomWithProperties.getBytes(StandardCharsets.UTF_8));

        String relPath = "org/test/test-app/1.0.0";
        ModuleInfo moduleInfo = ModuleInfoUtils.moduleInfoFromDescriptorPath(relPath, RepoLayoutUtils.MAVEN_2_DEFAULT);

        PomTargetPathValidator validator = new PomTargetPathValidator(relPath, moduleInfo);

        // This should not throw an exception since paths with property references are skipped
        validator.validate(inputStream, false);

        assertFalse(validator.isMavenPlugin(), "Not expected detection of maven plugin");
    }

    @Test
    void testValidateSnapshotWithoutVersionSnapshot() throws Exception {
        // Test integration snapshot without version snapshot (unique snapshot builds path without version)
        String pomWithSnapshot = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "    <modelVersion>4.0.0</modelVersion>\n" +
                "    <groupId>org.test</groupId>\n" +
                "    <artifactId>test-app</artifactId>\n" +
                "    <version>1.0-20240101.120000-1</version>\n" +
                "</project>";

        InputStream inputStream = new ByteArrayInputStream(pomWithSnapshot.getBytes(StandardCharsets.UTF_8));

        // For unique snapshots, buildPathPrefix should return "org/test/test-app/"
        // but our relPath doesn't match exactly, so let's use matching path
        String relPath = "org/test/test-app/1.0-20240101.120000-1";
        ModuleInfo moduleInfo = ModuleInfoUtils.moduleInfoFromDescriptorPath(relPath, RepoLayoutUtils.MAVEN_2_DEFAULT);

        PomTargetPathValidator validator = new PomTargetPathValidator(relPath, moduleInfo);

        // This tests the buildPathPrefix method with unique snapshot logic
        validator.validate(inputStream, false);

        assertFalse(validator.isMavenPlugin(), "Not expected detection of maven plugin");
    }

    @Test
    void testValidateWithBlankModelVersion() throws Exception {
        // Test POM with blank model version but not suppressed (should throw exception)
        String pomWithoutVersion = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "    <modelVersion>4.0.0</modelVersion>\n" +
                "    <groupId>org.test</groupId>\n" +
                "    <artifactId>test-app</artifactId>\n" +
                "</project>";

        InputStream inputStream = new ByteArrayInputStream(pomWithoutVersion.getBytes(StandardCharsets.UTF_8));

        String relPath = "org/test/test-app/1.0.0";
        ModuleInfo moduleInfo = ModuleInfoUtils.moduleInfoFromDescriptorPath(relPath, RepoLayoutUtils.MAVEN_2_DEFAULT);

        PomTargetPathValidator validator = new PomTargetPathValidator(relPath, moduleInfo);

        // This should throw BadPomException since model version is blank and checks are not suppressed
        assertThrows(BadPomException.class, () -> validator.validate(inputStream, false));
    }

    @Test
    void testValidateWithNullPathPrefix() throws Exception {
        // Test scenario where buildPathPrefix returns null (with suppressed checks to avoid version validation error)
        String pomWithEmptyVersion = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "    <modelVersion>4.0.0</modelVersion>\n" +
                "    <groupId>org.test</groupId>\n" +
                "    <artifactId>test-app</artifactId>\n" +
                "    <version></version>\n" + // Empty version
                "</project>";

        InputStream inputStream = new ByteArrayInputStream(pomWithEmptyVersion.getBytes(StandardCharsets.UTF_8));

        String relPath = "org/test/test-app/1.0.0";
        ModuleInfo moduleInfo = ModuleInfoUtils.moduleInfoFromDescriptorPath(relPath, RepoLayoutUtils.MAVEN_2_DEFAULT);

        PomTargetPathValidator validator = new PomTargetPathValidator(relPath, moduleInfo);

        // Use suppressed checks to avoid the version validation error
        validator.validate(inputStream, true);

        assertFalse(validator.isMavenPlugin(), "Not expected detection of maven plugin");
    }
}
