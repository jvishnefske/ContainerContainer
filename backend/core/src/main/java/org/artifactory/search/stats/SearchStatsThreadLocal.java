package org.artifactory.search.stats;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.search.SearchControlsBase;

/**
 * @author Andrey Tashlitsky
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SearchStatsThreadLocal {

    private static final ThreadLocal<SearchStats> searchStats = new ThreadLocal<>();

    public static void executeWithLogging(Runnable action) {
        initialize();
        try {
            action.run();
        } finally {
            logSearchStats();
            clear();
        }
    }

    public static void setSearchType(String searchType) {
        SearchStats stats = searchStats.get();
        if (stats != null) {
            stats.setSearchType(searchType);
        }
    }

    public static void addDbDuration(long dbDuration) {
        SearchStats stats = searchStats.get();
        if (stats != null) {
            stats.setDbDuration(stats.getDbDuration() + dbDuration);
        }
    }

    public static void addDbResults(long dbResults) {
        SearchStats stats = searchStats.get();
        if (stats != null) {
            stats.setDbResults(stats.getDbResults() + dbResults);
        }
    }

    public static void setReturnedResults(long returnedResults) {
        SearchStats stats = searchStats.get();
        if (stats != null) {
            stats.setReturnedResults(returnedResults);
        }
    }

    public static void setSearchControls(SearchControlsBase searchControls) {
        SearchStats stats = searchStats.get();
        if (stats != null) {
            stats.setSearchControls(searchControls);
        }
    }

    private static void initialize() {
        searchStats.set(new SearchStats());
    }

    private static SearchStats get() {
        return searchStats.get();
    }

    private static void logSearchStats() {
        SearchStats stats = get();
        if (stats != null) {
            log.info("search-type={},db-duration={}ms,db-results={},returned-results={},search-controls={}",
                    stats.getSearchType(),
                    stats.getDbDuration(),
                    stats.getDbResults(),
                    stats.getReturnedResults(),
                    stats.getSearchControls());
        }
    }

    private static void clear() {
        searchStats.remove();
    }
}
