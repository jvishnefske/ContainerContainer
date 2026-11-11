package org.artifactory.request.range.stream;

import org.artifactory.common.ConstantValues;
import org.artifactory.request.range.Range;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.util.LazyStreamLoader;
import org.jfrog.storage.binstore.ifc.SkippableInputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SingleRangeSkipInputStreamTest extends ArtifactoryHomeBoundJUnitTest {

    private static final byte[] TEST_CONTENT = "0123456789ABCDEFGHIJ".getBytes();

    @Test
    void constructor_shouldSkipAndLimit_whenFeatureFlagIsDisabled() throws IOException {
        getBound().setProperty(ConstantValues.cloudBinaryStoreRangeSupportEnabled, "false");
        Range range = Range.constructSkipRange(5, 9);

        SingleRangeSkipInputStream result = new SingleRangeSkipInputStream(
                range, new ByteArrayInputStream(TEST_CONTENT));

        assertArrayEquals("56789".getBytes(), result.readAllBytes());
    }

    @Test
    void constructor_shouldSkipAndLimit_whenFeatureFlagEnabledButNotLazyStreamLoader() throws IOException {
        getBound().setProperty(ConstantValues.cloudBinaryStoreRangeSupportEnabled, "true");
        Range range = Range.constructSkipRange(5, 9);

        SingleRangeSkipInputStream result = new SingleRangeSkipInputStream(
                range, new ByteArrayInputStream(TEST_CONTENT));

        assertArrayEquals("56789".getBytes(), result.readAllBytes());
    }

    @Test
    void constructor_shouldSkipAndLimit_whenLazyStreamLoaderNotRangeAware() throws IOException {
        getBound().setProperty(ConstantValues.cloudBinaryStoreRangeSupportEnabled, "true");
        Range range = Range.constructSkipRange(5, 9);
        // Non-range-aware stream (regular ByteArrayInputStream wrapped in LazyStreamLoader)
        LazyStreamLoader lazyStream = new LazyStreamLoader(() -> new ByteArrayInputStream(TEST_CONTENT));

        SingleRangeSkipInputStream result = new SingleRangeSkipInputStream(range, lazyStream);

        assertArrayEquals("56789".getBytes(), result.readAllBytes());
    }

    @Test
    void constructor_shouldPassThroughStream_whenAllConditionsMet() throws IOException {
        getBound().setProperty(ConstantValues.cloudBinaryStoreRangeSupportEnabled, "true");
        Range range = Range.constructSkipRange(5, 9);
        // Range-aware stream that already returns the correct range
        byte[] rangeContent = "56789".getBytes();
        LazyStreamLoader lazyStream = new LazyStreamLoader(
                () -> new RangeAwareSkippableInputStream(rangeContent));

        SingleRangeSkipInputStream result = new SingleRangeSkipInputStream(range, lazyStream);

        // The stream should be passed through as-is (no additional skip/limit applied)
        assertArrayEquals(rangeContent, result.readAllBytes());
    }

    @Test
    void constructor_shouldHandleRangeFromStart_whenFeatureFlagDisabled() throws IOException {
        getBound().setProperty(ConstantValues.cloudBinaryStoreRangeSupportEnabled, "false");
        Range range = Range.constructSkipRange(0, 4);

        SingleRangeSkipInputStream result = new SingleRangeSkipInputStream(
                range, new ByteArrayInputStream(TEST_CONTENT));

        assertArrayEquals("01234".getBytes(), result.readAllBytes());
    }

    @Test
    void constructor_shouldHandleRangeToEnd_whenFeatureFlagDisabled() throws IOException {
        getBound().setProperty(ConstantValues.cloudBinaryStoreRangeSupportEnabled, "false");
        Range range = Range.constructSkipRange(15, 19);

        SingleRangeSkipInputStream result = new SingleRangeSkipInputStream(
                range, new ByteArrayInputStream(TEST_CONTENT));

        assertArrayEquals("FGHIJ".getBytes(), result.readAllBytes());
    }

    /**
     * A SkippableInputStream that reports itself as range-aware.
     * Used to simulate a cloud binary store stream that already contains the requested range.
     */
    private static class RangeAwareSkippableInputStream extends ByteArrayInputStream implements SkippableInputStream {
        RangeAwareSkippableInputStream(byte[] buf) {
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

