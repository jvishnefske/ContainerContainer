package org.artifactory.storage.db.aql.itest.service;

import org.artifactory.aql.result.AqlEagerResult;
import org.testng.annotations.Test;

@Test
public class AqlItemInfoTest extends AqlAbstractServiceTest {

    //Aql item info search*/
    @Test
    public void findItemInfos() {
        AqlEagerResult<?> queryResult = aqlService.executeQueryEager(
                "item.infos.find({\"item.repo\" :\"repo1\"})");
        // Returns 2 although there are 3 matching rows because distinct merges two identical rows.
        // Currently we don't see a valid use case where it matters.
        assertSize(queryResult, 2);
        assertItemInfos(queryResult, "yossis", 5L);
        assertItemInfos(queryResult, "yoyo", 9L);
    }
}
