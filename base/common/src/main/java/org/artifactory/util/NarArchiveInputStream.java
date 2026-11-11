package org.artifactory.util;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * An ArchiveInputStream implementation for reading NAR (Nix Archive) format files.
 * <p>
 * The NAR format is used by the Nix package manager to store directory trees.
 * It uses a simple binary format with:
 * <ul>
 *   <li>Magic header: "nix-archive-1"</li>
 *   <li>Parenthesized expressions for structure</li>
 *   <li>Length-prefixed strings (8-byte little-endian length, padded to 8-byte boundary)</li>
 * </ul>
 *
 * @author Natalie Berni
 */
@Slf4j
public class NarArchiveInputStream extends ArchiveInputStream<NarArchiveEntry> {

    private static final String NAR_MAGIC = "nix-archive-1";
    private static final String TOKEN_OPEN_PAREN = "(";
    private static final String TOKEN_CLOSE_PAREN = ")";
    private static final String TOKEN_TYPE = "type";
    private static final String TOKEN_NAME = "name";
    private static final String TOKEN_CONTENTS = "contents";
    private static final String TOKEN_EXECUTABLE = "executable";
    private static final String TOKEN_ENTRY = "entry";
    private static final String TOKEN_NODE = "node";
    private static final String TOKEN_TARGET = "target";
    private static final String TYPE_REGULAR = "regular";
    private static final String TYPE_DIRECTORY = "directory";
    private static final String TYPE_SYMLINK = "symlink";

    private final InputStream inputStream;
    private final Deque<String> pathStack = new ArrayDeque<>();
    private NarArchiveEntry currentEntry;
    private long remainingBytesInCurrentEntry = 0;
    private long lastEntrySize = 0; // Track original size for padding calculation
    private boolean headerParsed = false;
    private boolean finished = false;
    private int depth = 0; // Track nesting depth for proper termination

    // State for parsing directory entries
    private boolean inDirectory = false;
    private String pendingEntryName = null;

    public NarArchiveInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    @Override
    public NarArchiveEntry getNextEntry() throws IOException {
        if (finished) {
            return null;
        }

        // Skip any remaining bytes from the previous entry
        if (remainingBytesInCurrentEntry > 0) {
            skipBytes(remainingBytesInCurrentEntry);
            remainingBytesInCurrentEntry = 0;
        }

        // Always skip padding based on original entry size (even if content was fully read)
        if (lastEntrySize > 0) {
            skipPadding(lastEntrySize);
            lastEntrySize = 0;
        }

        if (!headerParsed) {
            parseHeader();
            headerParsed = true;
        }

        currentEntry = parseNextEntry();
        return currentEntry;
    }

    private void parseHeader() throws IOException {
        // NAR format: "nix-archive-1" "(" "type" ...
        // First expect the magic header
        String token = readString();
        if (!NAR_MAGIC.equals(token)) {
            throw new IOException("NAR: invalid magic header, expected '" + NAR_MAGIC + "', got: " + token);
        }

        // Expect opening paren for root node
        token = readString();
        if (!TOKEN_OPEN_PAREN.equals(token)) {
            throw new IOException("NAR: expected '(' after magic header, got: " + token);
        }
        depth = 1; // We're now inside the root node
    }

    private NarArchiveEntry parseNextEntry() throws IOException {
        while (true) {
            String token = readString();
            NarArchiveEntry entry = processToken(token);
            if (entry != null || finished) {
                return entry;
            }
        }
    }

    private NarArchiveEntry processToken(String token) throws IOException {
        if (token == null) {
            finished = true;
            return null;
        }

        switch (token) {
            case TOKEN_OPEN_PAREN:
                depth++;
                return null;

            case TOKEN_CLOSE_PAREN:
                return handleCloseParen();

            case TOKEN_TYPE:
                return parseTypedEntry(readString());

            case TOKEN_ENTRY:
                handleEntryToken();
                return null;

            case TOKEN_NAME:
                pendingEntryName = readString();
                return null;

            case TOKEN_NODE:
                expectToken(TOKEN_OPEN_PAREN);
                depth++;
                return null;

            default:
                log.debug("NAR: skipping unknown token: {}", token);
                return null;
        }
    }

    private NarArchiveEntry handleCloseParen() {
        depth--;
        if (inDirectory && !pathStack.isEmpty()) {
            pathStack.pop();
        }
        if (depth == 0) {
            finished = true;
        }
        return null;
    }

    private void handleEntryToken() throws IOException {
        expectToken(TOKEN_OPEN_PAREN);
        depth++;
        expectToken(TOKEN_NAME);
        pendingEntryName = readString();
        expectToken(TOKEN_NODE);
        expectToken(TOKEN_OPEN_PAREN);
        depth++;
    }

    private NarArchiveEntry parseTypedEntry(String type) throws IOException {
        String path = buildCurrentPath();

        switch (type) {
            case TYPE_DIRECTORY:
                inDirectory = true;
                if (pendingEntryName != null) {
                    pathStack.push(pendingEntryName);
                    pendingEntryName = null;
                }
                // Skip the root directory entry (empty path) - it's implicit
                if (path.isEmpty()) {
                    return null;
                }
                // Return directory with trailing slash
                return new NarArchiveEntry(path + "/", 0, true, false, null);

            case TYPE_REGULAR:
                return parseRegularFile(path);

            case TYPE_SYMLINK:
                expectToken(TOKEN_TARGET);
                String target = readString();
                // Read closing paren for symlink
                expectToken(TOKEN_CLOSE_PAREN);
                return new NarArchiveEntry(path, 0, false, false, target);

            default:
                throw new IOException("NAR: unknown type: " + type);
        }
    }

    private NarArchiveEntry parseRegularFile(String path) throws IOException {
        boolean executable = false;

        while (true) {
            String token = readString();
            NarArchiveEntry entry = processRegularFileToken(token, path, executable);
            if (entry != null) {
                return entry;
            }
            // Check if we found the executable flag
            if (TOKEN_EXECUTABLE.equals(token)) {
                executable = true;
            }
        }
    }

    private NarArchiveEntry processRegularFileToken(String token, String path, boolean executable) throws IOException {
        if (token == null) {
            throw new IOException("NAR: unexpected end of stream in regular file");
        }

        switch (token) {
            case TOKEN_EXECUTABLE:
                // Executable is followed by an empty string
                readString();
                return null;

            case TOKEN_CONTENTS:
                long size = readLong();
                remainingBytesInCurrentEntry = size;
                lastEntrySize = size;
                pendingEntryName = null;
                return new NarArchiveEntry(path, size, false, executable, null);

            case TOKEN_CLOSE_PAREN:
                pendingEntryName = null;
                return new NarArchiveEntry(path, 0, false, executable, null);

            default:
                log.debug("NAR: skipping unknown token in regular file: {}", token);
                return null;
        }
    }

    private String buildCurrentPath() {
        if (pathStack.isEmpty() && pendingEntryName == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        // Build path from stack (bottom to top)
        String[] stackArray = pathStack.toArray(new String[0]);
        for (int i = stackArray.length - 1; i >= 0; i--) {
            if (!sb.isEmpty()) {
                sb.append("/");
            }
            sb.append(stackArray[i]);
        }

        if (pendingEntryName != null) {
            if (!sb.isEmpty()) {
                sb.append("/");
            }
            sb.append(pendingEntryName);
        }

        return sb.toString();
    }

    private void expectToken(String expected) throws IOException {
        String token = readString();
        if (!expected.equals(token)) {
            throw new IOException("NAR: expected '" + expected + "', got: " + token);
        }
    }

    private String readString() throws IOException {
        long length = readLong();
        if (length < 0) {
            return null;
        }
        if (length > Integer.MAX_VALUE) {
            throw new IOException("NAR: string too long: " + length);
        }

        byte[] bytes = new byte[(int) length];
        int read = 0;
        while (read < length) {
            int r = inputStream.read(bytes, read, (int) length - read);
            if (r < 0) {
                throw new IOException("NAR: unexpected end of stream reading string");
            }
            read += r;
        }

        // Skip padding to 8-byte boundary
        skipPadding(length);

        return new String(bytes, StandardCharsets.UTF_8);
    }

    private long readLong() throws IOException {
        byte[] bytes = new byte[8];
        int read = 0;
        while (read < 8) {
            int r = inputStream.read(bytes, read, 8 - read);
            if (r < 0) {
                if (read == 0) {
                    return -1; // End of stream
                }
                throw new IOException("NAR: unexpected end of stream reading long");
            }
            read += r;
        }
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    private void skipPadding(long length) throws IOException {
        int padding = (int) ((8 - (length % 8)) % 8);
        if (padding > 0) {
            skipBytes(padding);
        }
    }

    private void skipBytes(long count) throws IOException {
        long remaining = count;
        while (remaining > 0) {
            long skipped = inputStream.skip(remaining);
            if (skipped <= 0) {
                // Read and discard if skip doesn't work
                int toRead = (int) Math.min(remaining, 8192);
                byte[] buf = new byte[toRead];
                int read = inputStream.read(buf, 0, toRead);
                if (read < 0) {
                    throw new IOException("NAR: unexpected end of stream while skipping");
                }
                remaining -= read;
            } else {
                remaining -= skipped;
            }
        }
    }

    @Override
    public int read(@Nonnull byte[] b, int off, int len) throws IOException {
        if (currentEntry == null || remainingBytesInCurrentEntry <= 0) {
            return -1;
        }

        int toRead = (int) Math.min(len, remainingBytesInCurrentEntry);
        int read = inputStream.read(b, off, toRead);
        if (read > 0) {
            remainingBytesInCurrentEntry -= read;
        }
        return read;
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }
}
