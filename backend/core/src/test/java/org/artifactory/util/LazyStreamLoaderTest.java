package org.artifactory.util;


import org.apache.commons.lang3.RandomStringUtils;
import org.jfrog.storage.binstore.ifc.SkippableInputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Brut force test for the lazy stream loader
 */
class LazyStreamLoaderTest {

    @Test
    void lazyInitialization() throws IOException {
        // Ensure stream is not initialized until call to getStream()
        LazyStreamLoader.StreamProvider test = () -> new StringInputStream("Test");
        LazyStreamLoader lazyStreamLoader = new LazyStreamLoader(test);
        // Ensure stream is initialized after first usage
        int read = lazyStreamLoader.read();
        assertEquals('T', read);
    }


    @Test
    void singleBiteRead() throws IOException {
        for (int i = 0; i < 10; i++) {
            for (int j = 1; j < i; j++) {
                byte[] bytes = RandomStringUtils.randomAscii(i).getBytes();
                LazyStreamLoader lazyStreamLoader = new LazyStreamLoader(() -> new ByteArrayInputStream(bytes));
                int read = 0;
                while (read < i) {
                    int data = lazyStreamLoader.read();
                    assertEquals(bytes[read], data);
                    read += 1;
                }
            }
        }
    }

    @Test
    void arrayRead() throws IOException {
        for (int i = 0; i < 10; i++) {
            for (int j = 1; j < i; j++) {
                byte[] bytes = RandomStringUtils.randomAscii(i).getBytes();
                LazyStreamLoader lazyStreamLoader = new LazyStreamLoader(() -> new ByteArrayInputStream(bytes));
                byte[] buffer = new byte[j];
                int read = 0;
                while (read < i) {
                    int amount = lazyStreamLoader.read(buffer);
                    assertEquals(0, Arrays.compare(bytes, read, read + amount, buffer, 0, amount));
                    read += amount;
                }
            }
        }
    }

    @Test
    void arrayReadWithOffset() throws IOException {
        for (int i = 0; i < 10; i++) {
            for (int j = 1; j < i; j++) {
                byte[] bytes = RandomStringUtils.randomAscii(i).getBytes();
                LazyStreamLoader lazyStreamLoader = new LazyStreamLoader(() -> new ByteArrayInputStream(bytes));
                byte[] buffer = new byte[j + 2];
                int read = 0;
                while (read < i) {
                    int amount = lazyStreamLoader.read(buffer, 2, buffer.length - 2);
                    assertEquals(0, Arrays.compare(bytes, read, read + amount, buffer, 2, 2 + amount));
                    read += amount;
                }
            }
        }
    }

    @Test
    void arrayReadAll() throws IOException {
        for (int i = 0; i < 10; i++) {
            byte[] bytes = RandomStringUtils.randomAscii(i).getBytes();
            LazyStreamLoader lazyStreamLoader = new LazyStreamLoader(() -> new ByteArrayInputStream(bytes));
            byte[] result = lazyStreamLoader.readAllBytes();
            assertEquals(0, Arrays.compare(bytes, result));
        }
    }

    @Test
    void arrayReadNBites() throws IOException {
        for (int i = 0; i < 10; i++) {
            byte[] bytes = RandomStringUtils.randomAscii(i).getBytes();
            for (int j = 0; j < i; j++) {
                LazyStreamLoader lazyStreamLoader = new LazyStreamLoader(() -> new ByteArrayInputStream(bytes));
                byte[] result = lazyStreamLoader.readNBytes(j);
                assertEquals(0, Arrays.compare(bytes, 0, j, result, 0, j));
            }
        }
    }

    @Test
    void arrayReadNBites2() throws IOException {
        for (int i = 0; i < 10; i++) {
            byte[] bytes = RandomStringUtils.randomAscii(i).getBytes();
            for (int j = 0; j < i; j++) {
                LazyStreamLoader lazyStreamLoader = new LazyStreamLoader(() -> new ByteArrayInputStream(bytes));
                byte[] result = new byte[j];
                lazyStreamLoader.readNBytes(result, 0, j);
                assertEquals(0, Arrays.compare(bytes, 0, j, result, 0, j));
            }
        }
    }

    @Test
    void skip() throws IOException {
        for (int i = 0; i < 10; i++) {
            byte[] bytes = RandomStringUtils.randomAscii(i).getBytes();
            for (int j = 0; j < i; j++) {
                LazyStreamLoader lazyStreamLoader = new LazyStreamLoader(() -> new ByteArrayInputStream(bytes));
                long skipped = lazyStreamLoader.skip(j);
                byte byteValue = (byte) lazyStreamLoader.read();
                assertEquals(bytes[(int) skipped], byteValue);
            }
        }
    }

    @Test
    void skipNBytes() throws IOException {
        for (int i = 0; i < 10; i++) {
            byte[] bytes = RandomStringUtils.randomAscii(i).getBytes();
            for (int j = 0; j < i; j++) {
                LazyStreamLoader lazyStreamLoader = new LazyStreamLoader(() -> new ByteArrayInputStream(bytes));
                lazyStreamLoader.skipNBytes(j);
                byte byteValue = (byte) lazyStreamLoader.read();
                assertEquals(bytes[j], byteValue);
            }
        }
    }

    @Test
    void available() throws IOException {
        for (int i = 0; i < 10; i++) {
            byte[] bytes = RandomStringUtils.randomAscii(i).getBytes();
            for (int j = 0; j < i; j++) {
                LazyStreamLoader lazyStreamLoader = new LazyStreamLoader(() -> new ByteArrayInputStream(bytes));
                int available = lazyStreamLoader.available();
                assertEquals(available, i);
            }
        }
    }

    @Test
    void isRangeAware_shouldReturnFalse_whenStreamIsNotSkippableInputStream() throws IOException {
        LazyStreamLoader lazyStreamLoader = new LazyStreamLoader(() -> new ByteArrayInputStream("test".getBytes()));
        boolean result = lazyStreamLoader.isRangeAware();

        assertFalse(result);
    }

    @Test
    void isRangeAware_shouldReturnFalse_whenStreamIsSkippableButNotRanged() throws IOException {
        LazyStreamLoader lazyStreamLoader = new LazyStreamLoader(
                () -> new NonRangedSkippableInputStream("test".getBytes()));
        boolean result = lazyStreamLoader.isRangeAware();

        assertFalse(result);
    }

    @Test
    void isRangeAware_shouldReturnTrue_whenStreamIsSkippableAndRanged() throws IOException {
        LazyStreamLoader lazyStreamLoader = new LazyStreamLoader(
                () -> new RangedSkippableInputStream("test".getBytes()));
        boolean result = lazyStreamLoader.isRangeAware();

        assertTrue(result);
    }

    /**
     * A SkippableInputStream that is NOT range-aware (isRangedStream returns false)
     */
    private static class NonRangedSkippableInputStream extends ByteArrayInputStream implements SkippableInputStream {
        NonRangedSkippableInputStream(byte[] buf) {
            super(buf);
        }

        @Override
        public boolean isSkippable() {
            return true;
        }

        @Override
        public boolean isRangedStream() {
            return false;
        }
    }

    /**
     * A SkippableInputStream that IS range-aware (isRangedStream returns true)
     */
    private static class RangedSkippableInputStream extends ByteArrayInputStream implements SkippableInputStream {
        RangedSkippableInputStream(byte[] buf) {
            super(buf);
        }

        @Override
        public boolean isSkippable() {
            return true;
        }

        @Override
        public boolean isRangedStream() {
            return true;
        }
    }
}