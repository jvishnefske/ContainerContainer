package org.artifactory.ui.rest.model.artifacts.search.packagesearch.criteria;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Katrin Shor
 */
class FieldSearchCriteriaTest {

    @Test
    void testGetStrategyByFieldIdWithNullIdThrowsIllegalArgumentException() {
        final IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> FieldSearchCriteria.getStrategyByFieldId(null));
        
        assertTrue(exception.getMessage().contains("Field ID cannot be null or empty"));
    }

    @Test
    void testGetStrategyByFieldIdWithEmptyIdThrowsIllegalArgumentException() {
        final IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> FieldSearchCriteria.getStrategyByFieldId(""));
        
        assertTrue(exception.getMessage().contains("Field ID cannot be null or empty"));
    }

    @Test
    void testGetStrategyByFieldIdWithInvalidIdThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> FieldSearchCriteria.getStrategyByFieldId("invalidFieldId"));
    }

    @Test
    void testGetStrategyByFieldIdWithValidIdReturnsStrategy() {
        assertNotNull(FieldSearchCriteria.getStrategyByFieldId("repo"));
    }
}

