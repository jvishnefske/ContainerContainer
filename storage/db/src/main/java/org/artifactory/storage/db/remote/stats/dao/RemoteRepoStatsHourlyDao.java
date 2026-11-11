package org.artifactory.storage.db.remote.stats.dao;

import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.springframework.stereotype.Repository;

/**
 * DAO for the {@code remote_repo_stats_hourly} table.
 * Inherits all CRUD operations from {@link AbstractRemoteRepoStatsDao}.
 *
 * @author husseina
 */
@Repository
public class RemoteRepoStatsHourlyDao extends AbstractRemoteRepoStatsDao {

    private static final String TABLE_NAME = "remote_repo_stats_hourly";

    public RemoteRepoStatsHourlyDao(ArtifactoryJdbcHelper jdbcHelper,
                                    ArtifactoryDbProperties dbProperties) {
        super(jdbcHelper, dbProperties, TABLE_NAME);
    }
}
