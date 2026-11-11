package org.artifactory.storage.db.traffic.stats.dao;

import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.springframework.stereotype.Repository;

/**
 * DAO for the {@code repo_traffic_hourly} table.
 * Inherits all operations from {@link AbstractRepoTrafficStatsDao}.
 *
 * @author georgyr
 */
@Repository
public class RepoTrafficHourlyDao extends AbstractRepoTrafficStatsDao {

    private static final String TABLE_NAME = "repo_traffic_hourly";

    public RepoTrafficHourlyDao(
            ArtifactoryJdbcHelper jdbcHelper,
            ArtifactoryDbProperties dbProperties) {
        super(jdbcHelper, dbProperties, TABLE_NAME);
    }
}
