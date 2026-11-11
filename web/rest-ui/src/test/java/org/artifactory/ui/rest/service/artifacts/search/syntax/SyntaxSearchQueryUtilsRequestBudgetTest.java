package org.artifactory.ui.rest.service.artifacts.search.syntax;

import org.artifactory.ui.rest.model.artifacts.search.syntax.Query;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Amir Avidan
 */
class SyntaxSearchQueryUtilsRequestBudgetTest {

    @ParameterizedTest
    @CsvSource({
            "true,  9",
            "false, 30"
    })
    void getRequestBudgetSeconds_returnsAutoCompleteOrFullSearchBudget(
            boolean autoComplete, int expectedBudgetSeconds) {
        Query query = new Query("anything", null, null, null, autoComplete);

        int budget = SyntaxSearchQueryUtils.getRequestBudgetSeconds(query);

        assertEquals(expectedBudgetSeconds, budget);
    }
}
