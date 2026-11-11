package org.artifactory.addon.retention;

import org.testng.annotations.Test;

import java.util.Collections;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class EmptyRetentionSearchResultTest {

    @Test
    public void testEmptyRetentionSearchResult() {
        EmptyRetentionSearchResult emptyRetentionSearchResult = new EmptyRetentionSearchResult();
        emptyRetentionSearchResult.setResults(Collections.emptyList());
        emptyRetentionSearchResult.setRange(new EmptyRetentionSearchResult.EmptyRange());

        assertTrue(emptyRetentionSearchResult.getResults().isEmpty(), "Results should be empty");
        assertNotNull(emptyRetentionSearchResult.getRange(), "Range should not be null");
    }
}
