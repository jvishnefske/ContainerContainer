package org.artifactory.storage.db.devopsagent.requestlog.dao;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class RequestLogDaoTestUtils {

    private RequestLogDaoTestUtils() {
    }

    static void assertOrderedTokensPresent(String sql, String... tokens) {
        int previousIdx = -1;
        for (String token : tokens) {
            int idx = sql.indexOf(token);
            assertTrue(idx >= 0, "Expected '" + token + "' in SQL: " + sql);
            assertTrue(idx > previousIdx,
                    "Expected token '" + token + "' after the previous token in SQL: " + sql);
            previousIdx = idx;
        }
    }
}
