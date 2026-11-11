package org.artifactory.storage.db.aql.itest.service;

import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.result.AqlEagerResult;
import org.artifactory.aql.result.AqlRestResult;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.artifactory.aql.model.AqlItemTypeEnum.file;

/**
 * @author Eli Skoran 08/11/2022
 */
public class AqlInTransitTest extends AqlAbstractServiceTest {

    @Test
    public void filterOutInTransitArtifacts_externalAql() throws IOException {
        /*
            When searching items using an external aql query, we expect all _intransit items to be filtered out
         */
        try ( AqlRestResult queryResult = aqlService.executeQueryLazy(
                "items.find({\"repo\":{\"$match\":\"*\"}})")) {

            byte[] read = queryResult.read();
            StringBuilder builder = new StringBuilder();
            while (read != null) {
                builder.append(new String(read));
                read = queryResult.read();
            }
            String string = builder.toString();
            Assert.assertFalse(string.contains("_intransit"));
        }
    }

    @Test
    public void filterOutInTransitArtifacts_internalAql() {
        /*
            When searching items using an internal aql query, we expect _intransit items to not be filtered out.
         */
        AqlEagerResult<?> queryResult = aqlService.executeQueryEager(
                AqlApiItem.create().filter(AqlApiItem.repo().matches("*")));

        assertItem(queryResult, "_intransit", "rb1/v1/repo1", "file.bin", file);
    }

    @Test
    public void filterOutInTransitDirectRepoRequest() {
        /*
            Make sure that even when asking specifically for the _intransit repo the result will be empty
         */
        AqlEagerResult<?> queryResult = aqlService.executeQueryEager(
                "items.find({\"repo\":\"_intransit\"})");
        assertSize(queryResult, 0);
    }
}
