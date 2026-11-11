package org.artifactory.storage.db.event.dao.query;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class RepoStatusQueryHandler {
    private static final String SELECT_REPO_STATUS_FEDERATED_CURSORS =
            "select status, count(*) AS count " +
                    "from node_event_cursor " +
                    "where type = 'FEDERATED_REPLICATION' " +
                    "group by status";

    public String getQueryByDBType() {
        return SELECT_REPO_STATUS_FEDERATED_CURSORS;
    }
}
