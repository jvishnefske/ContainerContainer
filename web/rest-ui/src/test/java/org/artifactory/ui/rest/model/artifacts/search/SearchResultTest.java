package org.artifactory.ui.rest.model.artifacts.search;

import org.artifactory.common.ConstantValues;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.ui.rest.model.artifacts.search.quicksearch.QuickSearchResult;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;

import static org.testng.Assert.assertEquals;

/**
 * @author Oleksandr Legenkiy
 */
@Test
public class SearchResultTest extends ArtifactoryHomeBoundTest {

    private static final String NO_RESULTS_MSG =
            "No artifacts found. You can broaden your search by using the * and ? wildcards.";

    private String noResultsProjectsLimitedMessage;
    private String projectsLimitedMessage;
    private String maxResLimitedMessage;
    private int maxSearchResults;

    @BeforeMethod
    public void setup() {
        this.maxSearchResults = ConstantValues.searchMaxResults.getInt();
        int maxReposForProject = ConstantValues.searchInProjectMaxReposToProcess.getInt();
        this.noResultsProjectsLimitedMessage = String.format(
                "No artifacts found. Search was narrowed to first %d repositories for current project. Please " +
                        "consider refining your search.",
                maxReposForProject);
        this.projectsLimitedMessage =
                String.format("Search was narrowed to first %d repositories for current project. Please consider " +
                        "refining your search.", maxReposForProject);
        this.maxResLimitedMessage =
                String.format("Search results are limited to first %d results. Please consider refining your search.",
                        maxSearchResults);
    }

    @Test
    public void testAddNotifications() {
        ArtifactoryRestResponse<SearchResult<QuickSearchResult>> response = new ArtifactoryRestResponse<>();
        // No results, no limits.
        SearchResult<QuickSearchResult> result = createResult(0, false, false);
        result.addNotifications(response);
        assertEquals(response.getFeedbackMsg().getWarn(), NO_RESULTS_MSG);
        // No results, results limited by projects limitations.
        result = createResult(0, false, true);
        result.addNotifications(response);
        assertEquals(response.getFeedbackMsg().getWarn(), noResultsProjectsLimitedMessage);
        // Results present but limited by projects.
        result = createResult(2, false, true);
        result.addNotifications(response);
        assertEquals(response.getFeedbackMsg().getWarn(), projectsLimitedMessage);
        // Results present, no warnings.
        response.warn("empty"); // blank messages are not set.
        result = createResult(2, false, false);
        result.addNotifications(response);
        assertEquals(response.getFeedbackMsg().getWarn(), "empty");
        // Results present, limited by max search results limitations.
        result = createResult(maxSearchResults + 1, true, false);
        result.addNotifications(response);
        assertEquals(response.getFeedbackMsg().getWarn(), maxResLimitedMessage);
        // Results limited both by max search results and by projects.
        result = createResult(maxSearchResults + 1, true, true);
        result.addNotifications(response);
        assertEquals(response.getFeedbackMsg().getWarn(), maxResLimitedMessage);
    }

    private SearchResult<QuickSearchResult> createResult(int resCount, boolean isLimitSearchResults,
            boolean resultsLimitedByProjects) {
        return new SearchResult<>(Collections.emptyList(), "searchExpression",
                resCount, isLimitSearchResults, resultsLimitedByProjects);
    }

}
