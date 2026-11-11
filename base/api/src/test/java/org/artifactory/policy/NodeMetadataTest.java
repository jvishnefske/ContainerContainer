package org.artifactory.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class NodeMetadataTest {

    @Test
    void builderCreatesInstanceWithAllFields() {
        NodeMetadata metadata = NodeMetadata.builder()
                .created(1000L)
                .modified(2000L)
                .lastDownloaded(3000L)
                .build();

        assertNotNull(metadata);
        assertEquals(1000L, metadata.created());
        assertEquals(2000L, metadata.modified());
        assertEquals(3000L, metadata.lastDownloaded());
    }

    @Test
    void builderCreatesInstanceWithNullLastDownloaded() {
        NodeMetadata metadata = NodeMetadata.builder()
                .created(1000L)
                .modified(2000L)
                .lastDownloaded(null)
                .build();

        assertNotNull(metadata);
        assertEquals(1000L, metadata.created());
        assertEquals(2000L, metadata.modified());
        assertNull(metadata.lastDownloaded());
    }

    @Test
    void recordEqualityWithSameValues() {
        NodeMetadata metadata1 = NodeMetadata.builder()
                .created(1000L)
                .modified(2000L)
                .lastDownloaded(3000L)
                .build();

        NodeMetadata metadata2 = NodeMetadata.builder()
                .created(1000L)
                .modified(2000L)
                .lastDownloaded(3000L)
                .build();

        assertEquals(metadata1, metadata2);
        assertEquals(metadata1.hashCode(), metadata2.hashCode());
    }

    @Test
    void recordAccessors() {
        NodeMetadata metadata = new NodeMetadata(1000L, 2000L, 3000L);

        assertEquals(1000L, metadata.created());
        assertEquals(2000L, metadata.modified());
        assertEquals(3000L, metadata.lastDownloaded());
    }

    @Test
    void toBuilderCreatesModifiableCopy() {
        NodeMetadata original = NodeMetadata.builder()
                .created(1000L)
                .modified(2000L)
                .lastDownloaded(3000L)
                .build();

        NodeMetadata modified = original.toBuilder()
                .lastDownloaded(4000L)
                .build();

        assertEquals(1000L, modified.created());
        assertEquals(2000L, modified.modified());
        assertEquals(4000L, modified.lastDownloaded());
    }

    @Test
    void recordWithAllNulls() {
        NodeMetadata metadata = new NodeMetadata(null, null, null);

        assertNull(metadata.created());
        assertNull(metadata.modified());
        assertNull(metadata.lastDownloaded());
    }

    @Test
    void toStringContainsAllFields() {
        NodeMetadata metadata = new NodeMetadata(1000L, 2000L, 3000L);
        String toString = metadata.toString();

        assertNotNull(toString);
    }
}

