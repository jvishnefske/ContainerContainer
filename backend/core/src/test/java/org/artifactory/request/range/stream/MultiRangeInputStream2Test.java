package org.artifactory.request.range.stream;

import org.artifactory.request.range.Range;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Dvir Twina
 */
class MultiRangeInputStream2Test {

    private static final String TEST_CONTENT = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String CONTENT_TYPE = "text/plain";

    @Test
    void testConstructorWithLessThanTwoRanges() {
        // Test with empty list
        List<Range> emptyRanges = new ArrayList<>();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(TEST_CONTENT.getBytes());

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                new MultiRangeInputStream(emptyRanges, inputStream));
        assertTrue(exception.getMessage().contains("unsupported number of ranges"));

        // Test with single range
        List<Range> singleRange = createRanges(new long[][] {
                {0, 9}
        });
        ByteArrayInputStream inputStream2 = new ByteArrayInputStream(TEST_CONTENT.getBytes());

        exception = assertThrows(RuntimeException.class, () ->
                new MultiRangeInputStream(singleRange, inputStream2));
        assertTrue(exception.getMessage().contains("unsupported number of ranges"));
    }

    @Test
    void testReadWithMultipleRanges() throws IOException {
        List<Range> ranges = createRanges(new long[][] {
                {0, 4},    // "01234"
                {10, 14}   // "ABCDE"
        });

        ByteArrayInputStream inputStream = new ByteArrayInputStream(TEST_CONTENT.getBytes());
        MultiRangeInputStream multiRangeInputStream = new MultiRangeInputStream(ranges, inputStream);

        List<Byte> allBytes = readAllBytes(multiRangeInputStream);
        assertFalse(allBytes.isEmpty());
        String result = convertBytesToString(allBytes);

        // Verify each line of result
        String[] lines = result.split("\r\n");
        assertEquals("--BCD64322345343217845286A", lines[0]);
        assertEquals("Content-Type: text/plain", lines[1]);
        assertEquals("Content-Range: bytes 0-4/36", lines[2]);
        assertEquals("", lines[3]);
        assertEquals("01234", lines[4]);
        assertEquals("--BCD64322345343217845286A", lines[5]);
        assertEquals("Content-Type: text/plain", lines[6]);
        assertEquals("Content-Range: bytes 10-14/36", lines[7]);
        assertEquals("", lines[8]);
        assertEquals("ABCDE", lines[9]);
        assertEquals("--BCD64322345343217845286A--", lines[10]);

        multiRangeInputStream.close();
    }

    @Test
    void testClose() {
        List<Range> ranges = createRanges(new long[][] {
                {0, 4},
                {10, 14}
        });

        ByteArrayInputStream inputStream = new ByteArrayInputStream(TEST_CONTENT.getBytes());
        MultiRangeInputStream multiRangeInputStream = new MultiRangeInputStream(ranges, inputStream);

        // Close should not throw exception
        assertDoesNotThrow(multiRangeInputStream::close);
        // Close multiple times should be safe
        assertDoesNotThrow(multiRangeInputStream::close);
    }

    @Test
    void testRangesAtEndOfFile() throws IOException {
        List<Range> ranges = createRanges(new long[][] {
                {30, 32},  // "UVW"
                {33, 35}   // "XYZ"
        });

        ByteArrayInputStream inputStream = new ByteArrayInputStream(TEST_CONTENT.getBytes());
        MultiRangeInputStream multiRangeInputStream = new MultiRangeInputStream(ranges, inputStream);

        List<Byte> allBytes = readAllBytes(multiRangeInputStream);
        String result = convertBytesToString(allBytes);

        assertTrue(result.contains("UVW"));
        assertTrue(result.contains("XYZ"));

        multiRangeInputStream.close();
    }

    @Test
    void testSingleByteRanges() throws IOException {
        List<Range> ranges = createRanges(new long[][] {
                {0, 0},    // "0"
                {5, 5}     // "5"
        });

        ByteArrayInputStream inputStream = new ByteArrayInputStream(TEST_CONTENT.getBytes());
        MultiRangeInputStream multiRangeInputStream = new MultiRangeInputStream(ranges, inputStream);

        List<Byte> allBytes = readAllBytes(multiRangeInputStream);
        String result = convertBytesToString(allBytes);

        // Should contain single byte ranges
        assertTrue(result.contains("0"));
        assertTrue(result.contains("5"));

        multiRangeInputStream.close();
    }

    private List<Range> createRanges(long[][] rangeSpecs) {
        List<Range> ranges = new ArrayList<>();
        try {
            Constructor<Range> constructor = Range.class.getDeclaredConstructor(
                    String.class, long.class, long.class, long.class);
            constructor.setAccessible(true);

            for (long[] spec : rangeSpecs) {
                ranges.add(constructor.newInstance(CONTENT_TYPE, spec[0], spec[1], TEST_CONTENT.length()));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Range objects for testing", e);
        }
        return ranges;
    }

    private List<Byte> readAllBytes(MultiRangeInputStream multiRangeInputStream) throws IOException {
        List<Byte> allBytes = new ArrayList<>();
        int b;
        while ((b = multiRangeInputStream.read()) != -1) {
            allBytes.add((byte) b);
        }
        return allBytes;
    }

    private String convertBytesToString(List<Byte> byteList) {
        byte[] resultBytes = new byte[byteList.size()];
        for (int i = 0; i < byteList.size(); i++) {
            resultBytes[i] = byteList.get(i);
        }
        return new String(resultBytes);
    }
}