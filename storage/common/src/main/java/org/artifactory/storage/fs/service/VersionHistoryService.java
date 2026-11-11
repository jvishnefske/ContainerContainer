package org.artifactory.storage.fs.service;

import java.io.IOException;
import java.sql.SQLException;

public interface VersionHistoryService {

    /**
     * Save config history data.
     *
     */
    boolean saveConfig() throws SQLException, IOException;

    /**
     * Save version history row with backward-compatibility pending ({@code backward_compatible = 1}).
     * Call {@link #saveBackwardCompatible(String, String)} after DB converters complete.
     */
    void saveVersionHistory(String from, String to);

    /**
     * Updates {@code backward_compatible} on the active {@code version_history} row for {@code from}.
     */
    void saveBackwardCompatible(String from, String to);

    boolean isRollbackAllowed(String version);

    /**
     * Restores {@code logback.xml} from {@code config_history.log_config_data} for the given rollback target version.
     */
    void restoreLogbackFromConfigHistory(String version);
}
