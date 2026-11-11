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

package org.artifactory.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.jar.JarArchiveInputStream;

import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;

/**
 * Extended JAR/WAR is a zip archive input stream, where the start of the archive could be an executable script.
 * In those cases, we would like to skip until the zip part start.
 * Zip archive will be identified by 4 magic numbers header - {50,4B}, then {03,04}, {05,06} or {07,08}
 * {@see https://en.wikipedia.org/wiki/List_of_file_signatures}
 *
 * @author nadavy
 */
@Slf4j
public class ExtendedJarInputStream extends ArchiveInputStream {

    private final JarArchiveInputStream jarArchiveInputStream;
    private static final int MAGIC_A = 'P'; // first magic number, 50 hex
    private static final int MAGIC_B = 'K'; // second magic number 4B hex
    // zip types by 3+4 bytes headers
    private static final byte[] ZIP = new byte[] {'\u0003', '\u0004'};
    private static final byte[] EMPTY = new byte[] {'\u0005', '\u0006'};
    private static final byte[] SPANNED = new byte[] {'\u0007', '\u0008'};

    ExtendedJarInputStream(InputStream inputStream) throws IOException {
        PushbackInputStream pushbackInputStream = new PushbackInputStream(inputStream, 4);
        skipToZipStart(pushbackInputStream);
        this.jarArchiveInputStream = new JarArchiveInputStream(pushbackInputStream);
    }

    @Override
    public int read(@Nonnull byte[] b) throws IOException {
        return jarArchiveInputStream.read(b);
    }

    @Override
    public int read(@Nonnull byte[] b, int off, int len) throws IOException {
        return jarArchiveInputStream.read(b, off, len);
    }

    @Override
    public ArchiveEntry getNextEntry() throws IOException {
        return jarArchiveInputStream.getNextEntry();
    }

    @Override
    public void close() throws IOException {
        jarArchiveInputStream.close();
    }

    @Override
    protected void pushedBackBytes(long pushedBack) {
        throw new UnsupportedOperationException(
                "does not support pushback method");
    }

    @Override
    public long skip(long n) throws IOException {
        return jarArchiveInputStream.skip(n);
    }

    @Override
    public int available() throws IOException {
        return jarArchiveInputStream.available();
    }

    /**
     * Read bytes until we found the [50,4b,x,y] bytes that signal the zip start
     */
    public static void skipToZipStart(PushbackInputStream inputStream) throws IOException {
        int bufRead;
        boolean aFound = false;
        while ((bufRead = inputStream.read()) >= 0) {
            if (aFound && bufRead == MAGIC_B) {
                byte[] buffer = new byte[2];
                int read = inputStream.read(buffer);
                if (read == 2 && (isZipType(buffer, ZIP) || isZipType(buffer, EMPTY) || isZipType(buffer, SPANNED))) {
                    inputStream.unread(buffer);
                    inputStream.unread(MAGIC_B);
                    inputStream.unread(MAGIC_A);
                    return;
                }
            }
            if (bufRead == MAGIC_A) {
                aFound = true;
                continue;
            }
            aFound = false;
        }
    }

    private static boolean isZipType(byte[] buffer, byte[] type) {
        return buffer.length == 2 && buffer[0] == type[0] && buffer[1] == type[1];
    }
}