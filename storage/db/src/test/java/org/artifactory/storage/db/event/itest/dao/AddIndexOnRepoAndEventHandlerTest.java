package org.artifactory.storage.db.event.itest.dao;

import jakarta.annotation.Nonnull;
import org.artifactory.storage.db.event.dao.eventTableRepoKeyMigration.query.AddIndexOnRepoAndEventHandler;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AddIndexOnRepoAndEventHandlerTest {
    @Test
    void gettersTest() {
        AddIndexOnRepoAndEventHandler addIndexOnRepoAndEventHandler = new AddIndexOnRepoAndEventHandler(DbType.ORACLE);
        String queryAddIndexByDBType = addIndexOnRepoAndEventHandler.getQueryAddIndexByDBType();
        Assertions.assertTrue(toLowerCase(queryAddIndexByDBType).contains(toLowerCase("CREATE INDEX")));

        String queryCheckOnlineIndexSupportedByDB =
                addIndexOnRepoAndEventHandler.getQueryCheckOnlineIndexSupportedByDBType();
        Assertions.assertTrue(toLowerCase(queryCheckOnlineIndexSupportedByDB).contains(toLowerCase("enterprise")));

        String queryOnlineIndexCmdByDBType = addIndexOnRepoAndEventHandler.getQueryOnlineIndexCmdByDBType();
        Assertions.assertTrue(toLowerCase(queryOnlineIndexCmdByDBType).contains(toLowerCase("online")));
    }

    @Nonnull
    private static String toLowerCase(String str) {
        return str.toLowerCase();
    }
}
