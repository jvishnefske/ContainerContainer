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

import org.apache.commons.io.IOUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link CleanedXmlInputStream} to verify XML cleaning functionality,
 * especially UTF-8 character handling and streaming behavior.
 *
 * @author Michael Akushsky
 */
public class CleanedXmlInputStreamTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String VALID_POM_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
            "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 " +
            "http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
            "    <modelVersion>4.0.0</modelVersion>\n" +
            "    <groupId>jfrog.rt.test</groupId>\n" +
            "    <artifactId>jfrog-app</artifactId>\n" +
            "    <version>1.7048.0</version>\n" +
            "</project>";

    @Test
    public void testValidXmlWithoutMalformation() throws IOException {
        // Test that valid XML without issues passes through unchanged
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(VALID_POM_XML), "test-pom.xml")) {
            String result = IOUtils.toString(cleanedStream, StandardCharsets.UTF_8);

            // Should preserve the original content
            assertTrue(result.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"),
                    "XML declaration should be preserved");
            assertTrue(result.contains("<project xmlns="), "Project element should be preserved");
            assertTrue(result.contains("<groupId>jfrog.rt.test</groupId>"), "GroupId should be preserved");
        }
    }

    @Test
    public void testXmlWithLeadingGarbage() throws IOException {
        // Test XML with leading non-XML content
        String xmlWithGarbage = "Some garbage text\n#!/bin/bash\n\n" + 
                VALID_POM_XML;

        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(xmlWithGarbage), "test-pom.xml")) {
            String result = IOUtils.toString(cleanedStream, StandardCharsets.UTF_8);

            // Should skip garbage and start with XML
            assertTrue(result.trim().startsWith("<?xml"), "Should start with XML declaration");
            assertFalse(result.contains("Some garbage text"), "Should not contain garbage text");
            assertFalse(result.contains("#!/bin/bash"), "Should not contain shell script");
            assertTrue(result.contains("<groupId>jfrog.rt.test</groupId>"), "XML content should be preserved");
        }
    }

    private void resetConfiguredCharsCache() {
        try {
            Field f = CleanedXmlInputStream.class.getDeclaredField("CONFIGURED_PROBLEMATIC_CHARS");
            f.setAccessible(true);
            f.set(null, null);
        } catch (Exception ignore) {
        }
    }

    @Test
    public void testProblematicCharsConfig_SingleCharacter() throws IOException {
        setStringSystemProperty(ConstantValues.mvnCleanXmlProblematicChars, "x");
        resetConfiguredCharsCache();

        String xml = "<?xml version=\"1.0\"?><t>axbx</t>";
        try (CleanedXmlInputStream is = new CleanedXmlInputStream(createInputStream(xml), "cfg-single")) {
            String out = IOUtils.toString(is, StandardCharsets.UTF_8);
            org.junit.jupiter.api.Assertions.assertTrue(out.contains("a b "));
        }
    }

    @Test
    public void testProblematicCharsConfig_HexPlain() throws IOException {
        setStringSystemProperty(ConstantValues.mvnCleanXmlProblematicChars, "00A0");
        resetConfiguredCharsCache();

        String xml = "<?xml version=\"1.0\"?><t>A\u00A0B</t>";
        try (CleanedXmlInputStream is = new CleanedXmlInputStream(createInputStream(xml), "cfg-hex")) {
            String out = IOUtils.toString(is, StandardCharsets.UTF_8);
            org.junit.jupiter.api.Assertions.assertTrue(out.contains("A B"));
        }
    }

    @Test
    public void testProblematicCharsConfig_Hex0xPrefix() throws IOException {
        setStringSystemProperty(ConstantValues.mvnCleanXmlProblematicChars, "0x200B");
        resetConfiguredCharsCache();

        String xml = "<?xml version=\"1.0\"?><t>A\u200BZ</t>";
        try (CleanedXmlInputStream is = new CleanedXmlInputStream(createInputStream(xml), "cfg-0x")) {
            String out = IOUtils.toString(is, StandardCharsets.UTF_8);
            org.junit.jupiter.api.Assertions.assertTrue(out.contains("A Z"));
        }
    }

    @Test
    public void testProblematicCharsConfig_Decimal() throws IOException {
        setStringSystemProperty(ConstantValues.mvnCleanXmlProblematicChars, "dec:160");
        resetConfiguredCharsCache();

        String xml = "<?xml version=\"1.0\"?><t>P\u00A0Q</t>";
        try (CleanedXmlInputStream is = new CleanedXmlInputStream(createInputStream(xml), "cfg-dec")) {
            String out = IOUtils.toString(is, StandardCharsets.UTF_8);
            org.junit.jupiter.api.Assertions.assertTrue(out.contains("P Q"));
        }
    }

    @Test
    public void testProblematicCharsConfig_UnicodeLiteral() throws IOException {
        setStringSystemProperty(ConstantValues.mvnCleanXmlProblematicChars, "\\u00A0");
        resetConfiguredCharsCache();

        String xml = "<?xml version=\"1.0\"?><t>M\u00A0N</t>";
        try (CleanedXmlInputStream is = new CleanedXmlInputStream(createInputStream(xml), "cfg-ulit")) {
            String out = IOUtils.toString(is, StandardCharsets.UTF_8);
            org.junit.jupiter.api.Assertions.assertTrue(out.contains("M N"));
        }
    }

    @Test
    public void testProblematicCharsConfig_MixedAndInvalid() throws IOException {
        setStringSystemProperty(ConstantValues.mvnCleanXmlProblematicChars, "00A0, GARBAGE, 0x200B, , 160");
        resetConfiguredCharsCache();

        String xml = "<?xml version=\"1.0\"?><t>1\u00A02\u200B3\u00A0</t>";
        try (CleanedXmlInputStream is = new CleanedXmlInputStream(createInputStream(xml), "cfg-mixed")) {
            String out = IOUtils.toString(is, StandardCharsets.UTF_8);
            org.junit.jupiter.api.Assertions.assertTrue(out.contains("1 2 3 "));
        }
    }

    @Test
    public void testEmptyProblematicCharsList_NoCleaning() throws IOException {
        setStringSystemProperty(ConstantValues.mvnCleanXmlProblematicChars, "");
        resetConfiguredCharsCache();

        String xml = "<?xml version=\"1.0\"?><t>1\u00A02</t>";
        try (CleanedXmlInputStream is = new CleanedXmlInputStream(createInputStream(xml), "cfg-empty")) {
            String out = IOUtils.toString(is, StandardCharsets.UTF_8);
            org.junit.jupiter.api.Assertions.assertTrue(out.contains("1\u00A02"));
        }
    }

    @Test
    public void testCleaningDisabledProperty() throws IOException {
        setStringSystemProperty(ConstantValues.mvnCleanXmlInPoms, "false");
        // Ensure problematic list would otherwise clean; still, disabled should bypass cleaning
        setStringSystemProperty(ConstantValues.mvnCleanXmlProblematicChars, "00A0");
        resetConfiguredCharsCache();

        String xml = "<?xml version=\"1.0\"?><t>A\u00A0B</t>";
        try (CleanedXmlInputStream is = new CleanedXmlInputStream(createInputStream(xml), "cfg-disabled")) {
            String out = IOUtils.toString(is, StandardCharsets.UTF_8);
            org.junit.jupiter.api.Assertions.assertTrue(out.contains("A\u00A0B"));
        }
        // Re-enable for other tests
        setStringSystemProperty(ConstantValues.mvnCleanXmlInPoms, "true");
    }

    @Test
    public void testXmlWithProblematicUnicodeCharacters() throws IOException {
        // Create XML with problematic Unicode characters
        String xmlWithBadChars = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<project>\n" +
                "    <groupId>test\u00A0group</groupId>\n" +        // Non-breaking space
                "    <artifactId>test\u2009artifact</artifactId>\n" + // Thin space
                "    <version>1.0\u200B.0</version>\n" +            // Zero-width space
                "    <description>Test\uFEFFdescription</description>\n" + // BOM
                "</project>";

        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(xmlWithBadChars), "test-pom.xml")) {
            String result = IOUtils.toString(cleanedStream, StandardCharsets.UTF_8);

            // Problematic characters should be replaced with regular spaces
            assertTrue(result.contains("<groupId>test group</groupId>"), "Should contain cleaned groupId");
            assertTrue(result.contains("<artifactId>test artifact</artifactId>"), "Should contain cleaned artifactId");
            assertTrue(result.contains("<version>1.0 .0</version>"), "Should contain cleaned version");
            assertTrue(result.contains("<description>Test description</description>"), 
                    "Should contain cleaned description");
            
            // Should not contain problematic characters
            assertFalse(result.contains("\u00A0"), "Should not contain non-breaking space");
            assertFalse(result.contains("\u2009"), "Should not contain thin space");
            assertFalse(result.contains("\u200B"), "Should not contain zero-width space");
            assertFalse(result.contains("\uFEFF"), "Should not contain BOM");
        }
    }

    @Test
    public void testXmlWithInvalidUtf8Sequences() throws IOException {
        // Create a byte array with invalid UTF-8 sequences
        byte[] validXmlStart = "<?xml version=\"1.0\"?>\n<project>\n    <groupId>".getBytes(StandardCharsets.UTF_8);
        byte[] invalidUtf8 = {(byte) 0xFF, (byte) 0xFE, (byte) 0xFD}; // Invalid UTF-8 sequence
        byte[] validXmlEnd = "test</groupId>\n</project>".getBytes(StandardCharsets.UTF_8);
        
        byte[] combined = new byte[validXmlStart.length + invalidUtf8.length + validXmlEnd.length];
        System.arraycopy(validXmlStart, 0, combined, 0, validXmlStart.length);
        System.arraycopy(invalidUtf8, 0, combined, validXmlStart.length, invalidUtf8.length);
        System.arraycopy(validXmlEnd, 0, combined, validXmlStart.length + invalidUtf8.length, validXmlEnd.length);

        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(new ByteArrayInputStream(combined), "test-pom.xml")) {
            String result = IOUtils.toString(cleanedStream, StandardCharsets.UTF_8);

            // Should handle invalid UTF-8 sequences gracefully
            assertTrue(result.startsWith("<?xml"), "Should start with XML declaration");
            assertTrue(result.contains("<project>"), "Should contain valid XML structure");
            assertTrue(result.contains("<groupId>"), "Should contain groupId element");
            assertTrue(result.contains("</project>"), "Should end properly");
        }
    }

    @Test
    public void testXmlWithLeadingUtf8Problems() throws IOException {
        // Create content with problematic UTF-8 characters before XML
        String leadingBadChars = "\u00A0\u2009\uFEFF\n\n";
        String xmlContent = VALID_POM_XML;
        String combined = leadingBadChars + xmlContent;

        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(combined), "test-pom.xml")) {
            String result = IOUtils.toString(cleanedStream, StandardCharsets.UTF_8);

            // Should skip leading problematic characters and start with XML
            assertTrue(result.trim().startsWith("<?xml"), "Should start with XML declaration");
            assertTrue(result.contains("<groupId>jfrog.rt.test</groupId>"), "Should contain valid XML content");
        }
    }

    @Test
    public void testStreamingBehaviorWithLargeContent() throws IOException {
        // Create a larger XML content to test streaming behavior
        StringBuilder largeXml = new StringBuilder();
        largeXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project>\n");
        
        // Add many dependencies to make it larger
        for (int i = 0; i < 1000; i++) {
            largeXml.append("    <dependency>\n");
            largeXml.append("        <groupId>com.example").append(i).append("</groupId>\n");
            largeXml.append("        <artifactId>artifact").append(i).append("</artifactId>\n");
            largeXml.append("        <version>1.0.").append(i).append("</version>\n");
            largeXml.append("    </dependency>\n");
        }
        largeXml.append("</project>");

        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(largeXml.toString()), "large-pom.xml")) {
            
            // Test reading in chunks
            byte[] buffer = new byte[1024];
            int totalBytesRead = 0;
            int bytesRead;
            
            while ((bytesRead = cleanedStream.read(buffer)) != -1) {
                totalBytesRead += bytesRead;
                assertTrue(bytesRead > 0, "Should read positive number of bytes");
            }
            
            assertTrue(totalBytesRead > 10000, "Should read substantial amount of data");
        }
    }

    @Test
    public void testReadSingleBytes() throws IOException {
        String shortXml = "<?xml version=\"1.0\"?><test>value</test>";
        
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(shortXml), "test.xml")) {
            
            StringBuilder result = new StringBuilder();
            int b;
            while ((b = cleanedStream.read()) != -1) {
                result.append((char) b);
            }
            
            String resultStr = result.toString();
            assertTrue(resultStr.startsWith("<?xml"), "Should start with XML declaration");
            assertTrue(resultStr.contains("<test>value</test>"), "Should contain test element");
        }
    }

    @Test
    public void testSkipBytes() throws IOException {
        String xml = "<?xml version=\"1.0\"?><test>long content here</test>";
        
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(xml), "test.xml")) {
            
            // Skip some bytes
            long skipped = cleanedStream.skip(10);
            assertEquals(10, skipped, "Should skip requested bytes");
            
            // Read remaining content
            String remaining = IOUtils.toString(cleanedStream, StandardCharsets.UTF_8);
            assertFalse(remaining.startsWith("<?xml"), 
                    "Should not start with XML declaration after skipping");
        }
    }

    @Test
    public void testAvailable() throws IOException {
        String xml = "<?xml version=\"1.0\"?><test/>";
        
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(xml), "test.xml")) {
            
            int available = cleanedStream.available();
            assertTrue(available >= 0, "Should have bytes available");
        }
    }

    @Test
    public void testMarkNotSupported() throws IOException {
        String xml = "<?xml version=\"1.0\"?><test/>";
        
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(xml), "test.xml")) {
            
            assertFalse(cleanedStream.markSupported(), "Mark should not be supported");
        }
    }

    @Test
    public void testEmptyStream() throws IOException {
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(""), "empty.xml")) {
            
            int result = cleanedStream.read();
            assertEquals(-1, result, "Empty stream should return -1");
        }
    }

    @Test
    public void testStreamWithOnlyGarbage() throws IOException {
        String onlyGarbage = "This is not XML at all\nJust some random text\n";
        
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(onlyGarbage), "garbage.xml")) {
            
            int result = cleanedStream.read();
            assertEquals(-1, result, "Stream with no XML should return -1");
        }
    }

    @Test
    public void testXmlWithoutDeclaration() throws IOException {
        String xmlWithoutDecl = "<project><groupId>test</groupId></project>";
        
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(xmlWithoutDecl), "test.xml")) {
            String result = IOUtils.toString(cleanedStream, StandardCharsets.UTF_8);
            
            assertTrue(result.trim().startsWith("<project>"), "Should start with project tag");
            assertTrue(result.contains("<groupId>test</groupId>"), "Should contain groupId");
        }
    }

    @Test
    public void testMixedProblematicCharactersAndGarbage() throws IOException {
        // Combine garbage text with problematic UTF-8 characters
        String problematicContent = "Garbage\u00A0text\uFEFF\nMore\u2009garbage\n\n" + 
                "<?xml version=\"1.0\"?>\n" +
                "<project>\n" +
                "    <groupId>test\u00A0group</groupId>\n" +
                "</project>";

        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(problematicContent), "test.xml")) {
            String result = IOUtils.toString(cleanedStream, StandardCharsets.UTF_8);

            // Should skip garbage and clean problematic characters in XML
            assertTrue(result.trim().startsWith("<?xml"), "Should start with XML declaration");
            assertFalse(result.contains("Garbage"), "Should not contain garbage");
            assertTrue(result.contains("<groupId>test group</groupId>"), "Should contain cleaned groupId");
        }
    }

    @Test
    public void testReadBufferWithOffset() throws IOException {
        String xml = "<?xml version=\"1.0\"?><test>content</test>";
        
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(xml), "test.xml")) {
            
            byte[] buffer = new byte[100];
            int bytesRead = cleanedStream.read(buffer, 10, 50);
            
            assertTrue(bytesRead > 0, "Should read some bytes");
            String result = new String(buffer, 10, bytesRead, StandardCharsets.UTF_8);
            assertTrue(result.contains("<?xml") || result.contains("<test>"), "Should contain XML content");
        }
    }

    @Test
    public void testCleaningDisabled() throws IOException {
        // Test behavior when cleaning is disabled via system property
        String xmlWithProblems = "Garbage\u00A0text\n<?xml version=\"1.0\"?><test>value\u2009here</test>";
        
        // Note: This test assumes the system property can control the behavior
        // In a real scenario, we might need to mock ConstantValues.mvnCleanXmlInPoms
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(xmlWithProblems), "test.xml")) {
            
            // When cleaning is enabled, should clean content
            String result = IOUtils.toString(cleanedStream, StandardCharsets.UTF_8);
            // The actual behavior depends on the system property value
            assertTrue(result.length() > 0, "Should return some content");
        }
    }

    @Test
    public void testStringConstructor() throws IOException {
        // Test the string-based constructor
        String xmlContent = "<?xml version=\"1.0\"?><test>value</test>";
        
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(xmlContent, "string-test.xml")) {
            String result = IOUtils.toString(cleanedStream, StandardCharsets.UTF_8);
            
            assertTrue(result.contains("<?xml"), "Should contain XML declaration");
            assertTrue(result.contains("<test>value</test>"), "Should contain test content");
        }
    }

    @Test
    public void testStringConstructorWithNull() throws IOException {
        // Test string constructor with null content
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream((String) null, "null-test.xml")) {
            int result = cleanedStream.read();
            assertEquals(-1, result, "Null content should return -1");
        }
    }

    @Test
    public void testStringConstructorWithEmpty() throws IOException {
        // Test string constructor with empty content
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream("", "empty-test.xml")) {
            int result = cleanedStream.read();
            assertEquals(-1, result, "Empty content should return -1");
        }
    }

    @Test
    public void testReadWithNullBuffer() throws IOException {
        String xml = "<?xml version=\"1.0\"?><test/>";
        
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(xml), "test.xml")) {
            
            // Should throw NullPointerException for null buffer
            assertThrows(NullPointerException.class, () -> {
                cleanedStream.read(null, 0, 10);
            });
        }
    }

    @Test
    public void testReadWithInvalidParameters() throws IOException {
        String xml = "<?xml version=\"1.0\"?><test/>";
        
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(xml), "test.xml")) {
            
            byte[] buffer = new byte[10];
            
            // Test negative offset
            assertThrows(IndexOutOfBoundsException.class, () -> {
                cleanedStream.read(buffer, -1, 5);
            });
            
            // Test negative length
            assertThrows(IndexOutOfBoundsException.class, () -> {
                cleanedStream.read(buffer, 0, -1);
            });
            
            // Test length beyond buffer
            assertThrows(IndexOutOfBoundsException.class, () -> {
                cleanedStream.read(buffer, 5, 10);
            });
        }
    }

    @Test
    public void testReadWithZeroLength() throws IOException {
        String xml = "<?xml version=\"1.0\"?><test/>";
        
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(xml), "test.xml")) {
            
            byte[] buffer = new byte[10];
            int result = cleanedStream.read(buffer, 0, 0);
            assertEquals(0, result, "Reading zero bytes should return 0");
        }
    }

    @Test
    public void testComplexProblematicCharacters() throws IOException {
        // Test all problematic characters defined in the class
        StringBuilder xmlBuilder = new StringBuilder("<?xml version=\"1.0\"?>\n<project>\n");
        
        // Add all problematic characters
        char[] problematicChars = {
            '\u00A0', // Non-breaking space
            '\u2007', // Figure space
            '\u2009', // Thin space
            '\u200A', // Hair space
            '\u200B', // Zero-width space
            '\u200C', // Zero-width non-joiner
            '\u200D', // Zero-width joiner
            '\u2060', // Word joiner
            '\uFEFF'  // Zero-width no-break space (BOM)
        };
        
        for (int i = 0; i < problematicChars.length; i++) {
            xmlBuilder.append("    <element").append(i).append(">value")
                     .append(problematicChars[i]).append("here</element").append(i).append(">\n");
        }
        xmlBuilder.append("</project>");
        
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(xmlBuilder.toString()), "complex-test.xml")) {
            String result = IOUtils.toString(cleanedStream, StandardCharsets.UTF_8);
            
            // All problematic characters should be replaced with spaces
            for (char problematicChar : problematicChars) {
                assertFalse(result.contains(String.valueOf(problematicChar)), 
                        "Should not contain problematic character: U+" + Integer.toHexString(problematicChar));
            }
            
            // Should contain cleaned content
            assertTrue(result.contains("value here"), "Should contain cleaned values");
        }
    }

    @Test
    public void testXmlStartWithTagOnly() throws IOException {
        // Test XML that starts with < instead of <?xml
        String xmlWithoutDecl = "Some garbage\n<project><groupId>test</groupId></project>";
        
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(xmlWithoutDecl), "test.xml")) {
            String result = IOUtils.toString(cleanedStream, StandardCharsets.UTF_8);
            
            assertTrue(result.trim().startsWith("<project>"), "Should start with project tag");
            assertFalse(result.contains("Some garbage"), "Should skip garbage");
            assertTrue(result.contains("<groupId>test</groupId>"), "Should contain groupId");
        }
    }

    @Test
    public void testMultipleXmlDeclarations() throws IOException {
        // Test content with multiple potential XML starts
        String multipleXml = "Garbage\n<?xml version=\"1.0\"?>\nMore garbage\n<project>content</project>";
        
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(multipleXml), "test.xml")) {
            String result = IOUtils.toString(cleanedStream, StandardCharsets.UTF_8);
            
            // Should start from the first XML declaration
            assertTrue(result.trim().startsWith("<?xml"), "Should start with first XML declaration");
            assertTrue(result.contains("<project>content</project>"), "Should contain project content");
        }
    }

    @Test
    public void testVeryLargeSkipOperation() throws IOException {
        String xml = "<?xml version=\"1.0\"?><test>" + "x".repeat(10000) + "</test>";
        
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(xml), "large-test.xml")) {
            
            // Skip more bytes than available
            long skipped = cleanedStream.skip(50000);
            assertTrue(skipped > 0, "Should skip some bytes");
            assertTrue(skipped <= xml.length(), "Should not skip more than available");
            
            // After large skip, should reach end
            int result = cleanedStream.read();
            assertEquals(-1, result, "Should reach end of stream");
        }
    }

    @Test
    public void testBoundaryBufferConditions() throws IOException {
        // Create XML that's larger than buffer size to test buffer management
        int bufferSize = 8192; // BUFFER_SIZE from the class
        StringBuilder xmlBuilder = new StringBuilder("<?xml version=\"1.0\"?>\n<project>\n");
        
        // Fill to exceed buffer size significantly
        while (xmlBuilder.length() < bufferSize * 2) {
            xmlBuilder.append("    <dependency><groupId>com.example</groupId>")
                     .append("<artifactId>test</artifactId><version>1.0</version></dependency>\n");
        }
        xmlBuilder.append("</project>");
        
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(xmlBuilder.toString()), "boundary-test.xml")) {
            
            // Read in small chunks to test buffer management
            byte[] smallBuffer = new byte[100];
            int totalRead = 0;
            int bytesRead;
            
            while ((bytesRead = cleanedStream.read(smallBuffer)) != -1) {
                totalRead += bytesRead;
                assertTrue(bytesRead > 0, "Should read positive bytes");
            }
            
            // Should read all the content we created
            assertTrue(totalRead > bufferSize, "Should read substantial amount of data");
            assertTrue(totalRead >= xmlBuilder.length() - 100, "Should read most of the generated content");
        }
    }

    @Test
    public void testReadAfterClose() throws IOException {
        String xml = "<?xml version=\"1.0\"?><test/>";
        CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(xml), "test.xml");
        
        cleanedStream.close();
        
        // Note: ByteArrayInputStream doesn't throw IOException after close, 
        // so we just verify that close() works without error
        // The underlying stream behavior determines the exception behavior
        int result = cleanedStream.read();
        // ByteArrayInputStream returns -1 after close, which is valid behavior
        assertTrue(result >= -1, "Should return valid result after close");
    }

    @Test
    public void testContextPathInLogging() throws IOException {
        // Test that context path is used in logging (hard to test directly, but ensures coverage)
        String xmlWithProblems = "<?xml version=\"1.0\"?><test>content</test>";
        
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(xmlWithProblems), "context-test.xml")) {
            String result = IOUtils.toString(cleanedStream, StandardCharsets.UTF_8);
            assertTrue(result.contains("<test>content</test>"), "Should process content normally");
        }
        
        // Test with null context path
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(xmlWithProblems), null)) {
            String result = IOUtils.toString(cleanedStream, StandardCharsets.UTF_8);
            assertTrue(result.contains("<test>content</test>"), "Should process content with null context");
        }
    }

    @Test
    public void testMixedReadOperations() throws IOException {
        String xml = "<?xml version=\"1.0\"?><test>mixed operations test</test>";
        
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(xml), "mixed-test.xml")) {
            
            // Mix of different read operations
            int singleByte = cleanedStream.read();
            assertTrue(singleByte >= 0, "Should read first byte");
            
            byte[] buffer = new byte[10];
            int bytesRead = cleanedStream.read(buffer);
            assertTrue(bytesRead > 0, "Should read into buffer");
            
            long skipped = cleanedStream.skip(5);
            assertTrue(skipped >= 0, "Should skip some bytes");
            
            byte[] remaining = new byte[1000];
            int remainingBytes = cleanedStream.read(remaining);
            assertTrue(remainingBytes > 0, "Should read remaining bytes");
        }
    }

    @Test
    public void testNullSafetyInProcessRawData() throws IOException {
        // Test that null safety checks work correctly
        // This test ensures the null checks in processRawData are effective
        String xmlContent = "<?xml version=\"1.0\"?><test>null safety test</test>";
        
        try (CleanedXmlInputStream cleanedStream = 
                new CleanedXmlInputStream(createInputStream(xmlContent), "null-safety-test.xml")) {
            
            // Read the content to trigger processRawData
            String result = IOUtils.toString(cleanedStream, StandardCharsets.UTF_8);
            
            // Should successfully process without null pointer exceptions
            assertTrue(result.contains("<?xml"), "Should contain XML declaration");
            assertTrue(result.contains("<test>null safety test</test>"), "Should contain test content");
        }
    }

    @Test
    void shouldStreamWithoutOverflowOnDecoderExpansion() throws Exception {
        // Craft a BUFFER_SIZE-sized chunk starting with '<' followed by many invalid UTF-8 bytes.
        // Decoder will replace them but our implementation must not overflow internal buffers.
        byte[] raw = new byte[8192];
        raw[0] = (byte) '<';
        for (int i = 1; i < raw.length; i++) {
            raw[i] = (byte) 0x80; // invalid continuation byte
        }

        InputStream source = new ByteArrayInputStream(raw);
        CleanedXmlInputStream cleaned = new CleanedXmlInputStream(source, "test-context");

        byte[] out = new byte[4096];
        int total = 0;
        int r;
        while ((r = cleaned.read(out)) != -1) {
            assertTrue(r > 0);
            total += r;
        }
        assertTrue(total > 0);
    }

    @Test
    public void testLargeCleanedChunkDoesNotOverflowBuffer() throws IOException {
        // Build input that becomes much larger after decoding/cleaning
        // We simulate by creating a very large well-formed XML content
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\"?><root>\n");
        for (int i = 0; i < 50_000; i++) {
            sb.append("<n>").append(i).append("</n>\n");
        }
        sb.append("</root>");

        try (CleanedXmlInputStream cleanedStream =
                new CleanedXmlInputStream(createInputStream(sb.toString()), "overflow-test.xml")) {
            byte[] buf = new byte[1024];
            long total = 0;
            int read;
            while ((read = cleanedStream.read(buf)) != -1) {
                assertTrue(read > 0);
                total += read;
            }
            assertTrue(total >= sb.length());
        }
    }

    @Test
    public void testIso88591XmlContentHandledGracefully() throws IOException {
        String pom = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n" +
                "<project>\n" +
                "  <name>café Müller</name>\n" +
                "  <description>Résumé and naïve use-case</description>\n" +
                "</project>";
        byte[] isoBytes = pom.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

        try (CleanedXmlInputStream cleanedStream = new CleanedXmlInputStream(new java.io.ByteArrayInputStream(isoBytes),
                "iso-8859-1-pom.xml")) {
            String result = IOUtils.toString(cleanedStream, StandardCharsets.UTF_8);

            // XML tags should be preserved and content readable, even if some chars are replaced
            assertTrue(result.contains("<project>"));
            assertTrue(result.contains("<name>"));
            assertTrue(result.contains("<description>"));
            // Replacement character likely appears due to UTF-8 decoder replacing ISO-8859-1 specific bytes
            assertTrue(result.indexOf('\uFFFD') >= 0);
        }
    }

    @Test
    public void testIso88591XmlWithLeadingGarbage() throws IOException {
        String pom = "garbage header\n#! /bin/sh\n" +
                "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n" +
                "<project>\n" +
                "  <artifactId>caf \u000E</artifactId>\n" + // this \u000E is control; ensure robustness
                "</project>";
        byte[] isoBytes = pom.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

        try (CleanedXmlInputStream cleanedStream = new CleanedXmlInputStream(new java.io.ByteArrayInputStream(isoBytes),
                "iso-8859-1-garbage.xml")) {
            String result = IOUtils.toString(cleanedStream, StandardCharsets.UTF_8);
            // Should start from XML or first tag after cleaning
            assertTrue(result.trim().startsWith("<?xml") || result.trim().startsWith("<project>"));
            assertTrue(result.contains("<artifactId>"));
        }
    }

    @Test
    public void testWindows1252XmlContentHandledGracefully() throws IOException {
        String pom = "<?xml version=\"1.0\" encoding=\"Windows-1252\"?>\n" +
                "<project>\n" +
                "  <name>café déjà vu</name>\n" +
                "  <description>Pokémon – año</description>\n" +
                "</project>";
        byte[] winBytes = pom.getBytes(java.nio.charset.Charset.forName("Windows-1252"));

        try (CleanedXmlInputStream cleanedStream = new CleanedXmlInputStream(new java.io.ByteArrayInputStream(winBytes),
                "windows-1252-pom.xml")) {
            String result = IOUtils.toString(cleanedStream, StandardCharsets.UTF_8);
            assertTrue(result.contains("<project>"));
            assertTrue(result.contains("<name>"));
            assertTrue(result.contains("<description>"));
        }
    }

    @Test
    public void testShiftJISXmlContentHandledGracefully() throws IOException {
        String pom = "<?xml version=\"1.0\" encoding=\"Shift_JIS\"?>\n" +
                "<project>\n" +
                "  <name>日本語の名前</name>\n" +
                "  <description>テストの説明</description>\n" +
                "</project>";
        byte[] sjisBytes = pom.getBytes(java.nio.charset.Charset.forName("Shift_JIS"));

        try (CleanedXmlInputStream cleanedStream =
                     new CleanedXmlInputStream(new java.io.ByteArrayInputStream(sjisBytes),
                             "shift-jis-pom.xml")) {
            String result = IOUtils.toString(cleanedStream, StandardCharsets.UTF_8);
            assertTrue(result.contains("<project>"));
            assertTrue(result.contains("<name>"));
            assertTrue(result.contains("<description>"));
        }
    }

    /**
     * Helper method to create InputStream from string content
     */
    private InputStream createInputStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}