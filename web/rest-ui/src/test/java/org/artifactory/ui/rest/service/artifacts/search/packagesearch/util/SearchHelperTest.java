package org.artifactory.ui.rest.service.artifacts.search.packagesearch.util;

import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.ui.rest.model.artifacts.search.packagesearch.search.AqlUISearchModel;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.strategy.AqlUISearchStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Katrin Shor
 */
class SearchHelperTest {

    @Test
    void testBuildStrategiesFromSearchModelWithNullIdThrowsIllegalArgumentException() {
        final AqlUISearchModel searchModel = new AqlUISearchModel(null,
                                                                  AqlComparatorEnum.equals,
                                                                  List.of("test-value"));
        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> SearchHelper.buildStrategiesFromSearchModel(List.of(searchModel)));
        
        assertTrue(exception.getMessage().contains("Search model ID cannot be null or empty"));
    }

    @Test
    void testBuildStrategiesFromSearchModelWithEmptyIdThrowsIllegalArgumentException() {
        final AqlUISearchModel searchModel = new AqlUISearchModel("",
                                                                  AqlComparatorEnum.equals,
                                                                  List.of("test-value"));
        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> SearchHelper.buildStrategiesFromSearchModel(List.of(searchModel)));
        
        assertTrue(exception.getMessage().contains("Search model ID cannot be null or empty"));
    }

    @Test
    void testBuildStrategiesFromSearchModelWithValidIdReturnsStrategies() {
        final AqlUISearchModel searchModel = new AqlUISearchModel("repo",
                                                                  AqlComparatorEnum.equals,
                                                                  List.of("test-repo"));
        final List<AqlUISearchStrategy> strategies = SearchHelper.buildStrategiesFromSearchModel(List.of(searchModel));
        
        assertNotNull(strategies);
        assertFalse(strategies.isEmpty());
    }

    @Test
    void testBuildStrategiesFromSearchModelWithNoArgConstructorThrowsIllegalArgumentException() {
        final AqlUISearchModel searchModel = new AqlUISearchModel();
        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> SearchHelper.buildStrategiesFromSearchModel(List.of(searchModel)));
        
        assertTrue(exception.getMessage().contains("Search model ID cannot be null or empty"));
    }
}

