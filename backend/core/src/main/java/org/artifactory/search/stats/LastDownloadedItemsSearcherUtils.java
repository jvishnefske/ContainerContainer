package org.artifactory.search.stats;

import org.artifactory.api.search.stats.StatsSearchControls;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.api.internal.AqlBase;
import org.artifactory.search.VisibleAqlItemsSearchHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.artifactory.aql.api.internal.AqlBase.and;
import static org.artifactory.aql.api.internal.AqlBase.or;

/**
 * @author Alexei Vainshtein
 */
public class LastDownloadedItemsSearcherUtils {
    private static final Logger log = LoggerFactory.getLogger(LastDownloadedItemsSearcherUtils.class);

    private LastDownloadedItemsSearcherUtils() {
    }

    public static AqlBase.AndClause<AqlApiItem> buildQuery(StatsSearchControls controls) {
        AqlBase.OrClause<AqlApiItem> reposToSearchFilter =
                VisibleAqlItemsSearchHelper.getSelectedReposForSearchClause(controls);

        long since = controls.getDownloadedSince().getTimeInMillis();
        //If createdBefore is not specified will only return artifacts that were created before downloadedSince
        long createdBefore = since;
        if (controls.hasCreatedBefore()) {
            createdBefore = controls.getCreatedBefore().getTimeInMillis();
        }
        AqlBase.AndClause<AqlApiItem> query = and();
        if (!reposToSearchFilter.isEmpty()) {
            log.debug("Filtering not used since search by repos: {}", controls.getSelectedRepoForSearch());
            query.append(reposToSearchFilter);
        }
        query.append(
                or(
                        AqlApiItem.statistic().downloaded().less(since),
                        AqlApiItem.statistic().downloaded().equals(null)
                )
        );
        query.append(
                or(
                        AqlApiItem.statistic().remoteDownloaded().less(since),
                        AqlApiItem.statistic().remoteDownloaded().equals(null)
                )
        );
        query.append(AqlApiItem.created().less(createdBefore));
        return query;
    }
}
