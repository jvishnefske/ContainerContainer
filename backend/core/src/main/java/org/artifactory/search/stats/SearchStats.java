package org.artifactory.search.stats;

import lombok.Data;
import org.artifactory.api.search.SearchControlsBase;

/**
 * @author Andrey Tashlitsky
 */
@Data
public class SearchStats {
    private String searchType;
    private long dbDuration;
    private long dbResults;
    private long returnedResults;
    private SearchControlsBase searchControls;
}
