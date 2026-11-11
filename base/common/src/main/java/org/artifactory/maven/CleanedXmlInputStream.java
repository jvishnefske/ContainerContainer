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

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A streaming InputStream implementation that cleans malformed UTF-8 characters from XML content.
 * This implementation is designed to handle potentially huge files by processing data in chunks
 * without loading the entire content into memory.
 *
 * <p>The stream performs the following operations:
 * <ul>
 * <li>Removes or replaces invalid UTF-8 byte sequences</li>
 * <li>Skips leading non-XML content until XML declaration or first XML tag is found</li>
 * <li>Preserves all content from the first XML element onwards</li>
 * <li>Handles problematic Unicode characters that cause XML parsing issues</li>
 * </ul>
 *
 * <p>This implementation uses a streaming approach with internal buffering to handle
 * files of any size efficiently.
 *
 * @author Michael Akushsky
 */
@Slf4j
public class CleanedXmlInputStream extends InputStream {

    // Buffer size for reading chunks from the source stream
    private static final int BUFFER_SIZE = 8192;

    // XML declaration and tag start patterns (as bytes)
    private static final byte[] XML_DECL_START = "<?xml".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TAG_START = "<".getBytes(StandardCharsets.UTF_8);

    // Cached configured problematic characters list (loaded lazily from ConstantValues)
    private static volatile char[] CONFIGURED_PROBLEMATIC_CHARS;

    private final InputStream sourceStream;
    private final String contextPath;
    private final CharsetDecoder utf8Decoder;

    // Internal state
    private final byte[] readBuffer;
    private int bufferPosition;
    private int bufferLimit;
    private boolean xmlStartFound;
    private boolean endOfStream;
    // Holds leftover cleaned bytes that didn't fit into readBuffer on last fill
    private byte[] pendingBuffer;
    private int pendingPosition;
    private int pendingLimit;

    /**
     * Creates a new CleanedXmlInputStream that processes the input stream.
     *
     * @param inputStream the source input stream containing potentially malformed XML
     * @param contextPath optional path for logging context (can be null)
     */
    public CleanedXmlInputStream(InputStream inputStream, String contextPath) {
        this.sourceStream = inputStream;
        this.contextPath = contextPath;
        this.readBuffer = new byte[BUFFER_SIZE];
        this.bufferPosition = 0;
        this.bufferLimit = 0;
        this.xmlStartFound = false;
        this.endOfStream = false;
        this.pendingBuffer = null;
        this.pendingPosition = 0;
        this.pendingLimit = 0;
        // Ensure we respect latest configuration per stream instance
        CONFIGURED_PROBLEMATIC_CHARS = null;

        // Configure UTF-8 decoder to replace malformed sequences
        this.utf8Decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
    }

    /**
     * Creates a new CleanedXmlInputStream from string content.
     *
     * @param xmlContent  the XML content as string
     * @param contextPath optional path for logging context (can be null)
     */
    public CleanedXmlInputStream(String xmlContent, String contextPath) {
        this(new ByteArrayInputStream(
                        xmlContent != null ? xmlContent.getBytes(StandardCharsets.UTF_8) : new byte[0]),
                contextPath);
    }

    @Override
    public int read() throws IOException {
        if (isCleaningDisabled()) {
            // If cleaning is disabled, delegate directly to source stream
            return sourceStream.read();
        }

        byte[] singleByte = new byte[1];
        int result = read(singleByte, 0, 1);
        return result == -1 ? -1 : (singleByte[0] & 0xFF);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (isCleaningDisabled()) {
            // If cleaning is disabled, delegate directly to source stream
            return sourceStream.read(b, off, len);
        }

        if (b == null) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return 0;
        }

        int totalBytesRead = 0;

        while (totalBytesRead < len && !endOfStream) {
            // Ensure we have data in our buffer
            if (bufferPosition >= bufferLimit && !fillBuffer()) {
                break; // End of stream reached
            }

            // Copy available bytes to output buffer
            int availableBytes = bufferLimit - bufferPosition;
            int bytesToCopy = Math.min(len - totalBytesRead, availableBytes);

            System.arraycopy(readBuffer, bufferPosition, b, off + totalBytesRead, bytesToCopy);
            bufferPosition += bytesToCopy;
            totalBytesRead += bytesToCopy;
        }

        return totalBytesRead == 0 && endOfStream ? -1 : totalBytesRead;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public long skip(long n) throws IOException {
        if (isCleaningDisabled()) {
            return sourceStream.skip(n);
        }

        long totalSkipped = 0;
        byte[] skipBuffer = new byte[Math.min((int) n, BUFFER_SIZE)];

        while (totalSkipped < n) {
            int bytesToSkip = (int) Math.min(n - totalSkipped, skipBuffer.length);
            int bytesRead = read(skipBuffer, 0, bytesToSkip);

            if (bytesRead == -1) {
                break; // End of stream
            }

            totalSkipped += bytesRead;
        }

        return totalSkipped;
    }

    @Override
    public int available() throws IOException {
        if (isCleaningDisabled()) {
            return sourceStream.available();
        }

        // Return available bytes in our buffer plus estimate from source
        int bufferAvailable = bufferLimit - bufferPosition;
        int sourceAvailable = sourceStream.available();
        return bufferAvailable + sourceAvailable;
    }

    @Override
    public void close() throws IOException {
        sourceStream.close();
    }

    @Override
    public boolean markSupported() {
        return false; // Mark/reset not supported in streaming mode
    }

    /**
     * Checks if XML cleaning is disabled.
     */
    private boolean isCleaningDisabled() {
        return !ConstantValues.mvnCleanXmlInPoms.getBoolean();
    }

    /**
     * Fills the internal buffer with cleaned data from the source stream.
     * This method handles the core logic of UTF-8 cleaning and XML detection.
     *
     * @return true if data was read, false if end of stream reached
     * @throws IOException if an I/O error occurs
     */
    private boolean fillBuffer() throws IOException {
        if (endOfStream) {
            return false;
        }

        // Reset buffer for new data
        bufferPosition = 0;
        bufferLimit = 0;

        // If we have pending cleaned bytes, serve them first
        if (pendingBuffer != null && pendingPosition < pendingLimit) {
            int bytesToCopy = Math.min(BUFFER_SIZE, pendingLimit - pendingPosition);
            System.arraycopy(pendingBuffer, pendingPosition, readBuffer, 0, bytesToCopy);
            bufferLimit = bytesToCopy;
            pendingPosition += bytesToCopy;
            if (pendingPosition >= pendingLimit) {
                pendingBuffer = null;
                pendingPosition = 0;
                pendingLimit = 0;
            }
            return true;
        }

        // Read raw data from source
        byte[] rawBuffer = new byte[BUFFER_SIZE];
        int rawBytesRead = sourceStream.read(rawBuffer);

        if (rawBytesRead == -1) {
            endOfStream = true;
            return false;
        }

        // Process the raw data
        try {
            processRawData(rawBuffer, rawBytesRead);
        } catch (Exception e) {
            if (contextPath != null) {
                log.warn("Error processing XML data for '{}': {}", contextPath, e.getMessage());
            }
            // On error, try to pass through original data
            System.arraycopy(rawBuffer, 0, readBuffer, 0, rawBytesRead);
            bufferLimit = rawBytesRead;
        }

        // If we have data in buffer, return true
        if (bufferLimit > 0) {
            return true;
        }

        // If buffer is empty and we haven't found XML yet, try to read more
        // unless we've reached the end of stream
        if (!xmlStartFound && !endOfStream) {
            // Recursively try to fill buffer until we find XML or reach end of stream
            return fillBuffer();
        }

        // If we reach here, either XML was found but buffer is empty (shouldn't happen)
        // or we've reached end of stream without finding XML
        if (log.isDebugEnabled()) {
            log.debug("fillBuffer returning false; xmlStartFound={}, endOfStream={}, bufferLimit={}, context={}",
                    xmlStartFound, endOfStream, bufferLimit, contextPath);
        }
        return false;
    }

    /**
     * Processes raw byte data by cleaning UTF-8 sequences and handling XML detection.
     */
    private void processRawData(byte[] rawData, int length) throws IOException {
        // Convert raw bytes to string, replacing invalid UTF-8 sequences
        ByteBuffer byteBuffer = ByteBuffer.wrap(rawData, 0, length);
        String decoded = utf8Decoder.decode(byteBuffer).toString();

        // Clean problematic Unicode characters
        String cleaned = cleanProblematicChars(decoded);

        // Convert back to bytes
        byte[] cleanedBytes = (cleaned != null) ? cleaned.getBytes(StandardCharsets.UTF_8) : new byte[0];

        // Safety check to ensure cleanedBytes is never null
        if (cleanedBytes == null) {
            cleanedBytes = new byte[0];
        }

        if (!xmlStartFound) {
            // Look for XML start in the cleaned data
            int xmlStartPos = findXmlStart(cleanedBytes);

            if (xmlStartPos >= 0) {
                xmlStartFound = true;
                // Copy from XML start position onwards
                int totalAvailable = cleanedBytes.length - xmlStartPos;
                int bytesToCopy = Math.min(totalAvailable, readBuffer.length);
                System.arraycopy(cleanedBytes, xmlStartPos, readBuffer, 0, bytesToCopy);
                bufferLimit = bytesToCopy;
                // Stash leftover if needed
                int remaining = totalAvailable - bytesToCopy;
                if (remaining > 0) {
                    pendingBuffer = new byte[remaining];
                    System.arraycopy(cleanedBytes, xmlStartPos + bytesToCopy, pendingBuffer, 0, remaining);
                    pendingPosition = 0;
                    pendingLimit = remaining;
                }

                if (contextPath != null) {
                    log.debug("Found XML start at position {} in '{}'", xmlStartPos, contextPath);
                }
            } else {
                // No XML found yet, buffer is empty - fillBuffer will handle retrying
                bufferLimit = 0;
            }
        } else {
            // XML already started, include all cleaned data
            int bytesToCopy = Math.min(cleanedBytes.length, readBuffer.length);
            System.arraycopy(cleanedBytes, 0, readBuffer, 0, bytesToCopy);
            bufferLimit = bytesToCopy;
            int remaining = cleanedBytes.length - bytesToCopy;
            if (remaining > 0) {
                pendingBuffer = new byte[remaining];
                System.arraycopy(cleanedBytes, bytesToCopy, pendingBuffer, 0, remaining);
                pendingPosition = 0;
                pendingLimit = remaining;
            }
        }
    }

    /**
     * Cleans problematic Unicode characters that can cause XML parsing issues.
     */
    private String cleanProblematicChars(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder result = new StringBuilder(input.length());
        char[] problematicChars = getConfiguredProblematicChars();

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

            // Check if character is in our problematic list
            boolean isProblematic = false;
            for (char problematicChar : problematicChars) {
                if (ch == problematicChar) {
                    isProblematic = true;
                    break;
                }
            }

            if (isProblematic) {
                // Replace problematic characters with regular space, but only if we're not in a string value
                result.append(' ');
            } else {
                result.append(ch);
            }
        }

        return result.toString();
    }

    /**
     * Returns the configured list of problematic characters to be cleaned.
     * The list is loaded lazily from {@link ConstantValues#mvnCleanXmlProblematicChars} and cached.
     * Supported token formats: single character, hex (e.g. 00A0 or 0x00A0), decimal code point, or U+NNNN.
     */
    private static char[] getConfiguredProblematicChars() {
        char[] local = CONFIGURED_PROBLEMATIC_CHARS;
        if (local != null) {
            return local;
        }
        synchronized (CleanedXmlInputStream.class) {
            if (CONFIGURED_PROBLEMATIC_CHARS == null) {
                List<String> tokens = ConstantValues.mvnCleanXmlProblematicChars.getList();
                CONFIGURED_PROBLEMATIC_CHARS = parseCharTokens(tokens);
            }
            return CONFIGURED_PROBLEMATIC_CHARS;
        }
    }

    /**
     * Parses a list of tokens into a char array. Each token can be:
     * - a single character
     * - a hexadecimal code point (e.g., 00A0 or 0x00A0)
     * - a decimal code point (e.g., 160)
     * - a unicode literal (e.g., U+00A0)
     */
    private static char[] parseCharTokens(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return new char[0];
        }
        List<Character> chars = new ArrayList<>();
        for (String raw : tokens) {
            if (raw == null) {
                continue;
            }
            String token = raw.trim();
            if (token.isEmpty()) {
                continue;
            }
            Character parsed = parseSingleCharToken(token);
            if (parsed != null) {
                chars.add(parsed);
            }
        }
        char[] result = new char[chars.size()];
        for (int i = 0; i < chars.size(); i++) {
            result[i] = chars.get(i);
        }
        return result;
    }

    private static Character parseSingleCharToken(String token) {
        try {
            // Unicode literal prefix backslash-u
            if (token.startsWith("\\" + "u") && token.length() >= 6) {
                int codePoint = Integer.parseInt(token.substring(2), 16);
                return (char) codePoint;
            }
            // Hex with 0x prefix
            if ((token.startsWith("0x") || token.startsWith("0X")) && token.length() > 2) {
                int codePoint = Integer.parseInt(token.substring(2), 16);
                return (char) codePoint;
            }
            // Explicit decimal with dec: prefix
            if (token.matches("(?i)dec:[0-9]{1,7}")) {
                int codePoint = Integer.parseInt(token.substring(4));
                return (char) codePoint;
            }
            // Plain hex (2-6 hex digits)
            if (token.matches("[0-9A-Fa-f]{2,6}")) {
                int codePoint = Integer.parseInt(token, 16);
                return (char) codePoint;
            }
            // Single character
            if (token.length() == 1) {
                return token.charAt(0);
            }
        } catch (Exception ignore) {
            // Ignore invalid token
        }
        return null;
    }

    /**
     * Finds the start of XML content in the given byte array.
     * Looks for either "<?xml" declaration or the first "<" character.
     */
    private int findXmlStart(byte[] data) {
        // First try to find XML declaration
        int xmlDeclPos = findPattern(data, XML_DECL_START);
        if (xmlDeclPos >= 0) {
            return xmlDeclPos;
        }

        // If no XML declaration, look for first tag
        return findPattern(data, TAG_START);
    }

    /**
     * Finds the first occurrence of a pattern in the data array.
     */
    private int findPattern(byte[] data, byte[] pattern) {
        if (data == null || pattern == null || data.length < pattern.length) {
            return -1;
        }

        for (int i = 0; i <= data.length - pattern.length; i++) {
            boolean found = true;
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return i;
            }
        }

        return -1;
    }
}