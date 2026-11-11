package org.artifactory.storage.db.postgresql.entity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * @author Andrey Tashlitsky
 */
class IndexSizeEntityTest {

    @ParameterizedTest
    @MethodSource("equalityTestCases")
    void equals_shouldFollowCustomContract(Object obj1, Object obj2, boolean shouldBeEqual) {
        // Act & Assert
        if (shouldBeEqual) {
            assertEquals(obj1, obj2);
        } else {
            assertNotEquals(obj1, obj2);
        }
    }

    @ParameterizedTest
    @MethodSource("hashCodeTestCases")
    void hashCode_shouldBeConsistentWithEquals(IndexSizeEntity entity1, IndexSizeEntity entity2) {
        // Act & Assert
        assertEquals(entity1, entity2);
        assertEquals(entity1.hashCode(), entity2.hashCode());
    }

    private static Stream<Arguments> equalityTestCases() {
        IndexSizeEntity entity1 = new IndexSizeEntity("idx_nodes_sha256", 1000L, "nodes", 50000L, 100L);
        IndexSizeEntity entity2 = new IndexSizeEntity("idx_nodes_sha256", 2000L, "different", 99999L, 999L);
        IndexSizeEntity entity3 = new IndexSizeEntity("idx_nodes_node_id", 1000L, "nodes", 50000L, 100L);

        return Stream.of(
                // Same index name with different other fields -> equal (custom contract)
                Arguments.of(entity1, entity2, true),
                // Different index names -> not equal
                Arguments.of(entity1, entity3, false),
                // Null comparison -> not equal
                Arguments.of(entity1, null, false),
                // Different class comparison -> not equal
                Arguments.of(entity1, "not an entity", false)
        );
    }

    private static Stream<Arguments> hashCodeTestCases() {
        return Stream.of(
                // Same index name, different sizes -> equal hash code required
                Arguments.of(
                        new IndexSizeEntity("idx_nodes_sha256", 1000L, "nodes", 50000L, 100L),
                        new IndexSizeEntity("idx_nodes_sha256", 2000L, "different", 99999L, 999L)
                ),
                // Same index name, completely different data -> equal hash code required
                Arguments.of(
                        new IndexSizeEntity("idx_builds_id", 500L, "builds", 1000L, 10L),
                        new IndexSizeEntity("idx_builds_id", 999999L, "other", 999999L, 999999L)
                )
        );
    }
}
