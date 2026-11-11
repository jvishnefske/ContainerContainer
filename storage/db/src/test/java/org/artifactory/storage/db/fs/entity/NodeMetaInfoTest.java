package org.artifactory.storage.db.fs.entity;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author Andrey Tashlitsky
 */
@Test
public class NodeMetaInfoTest {

    public void nodeMetaInfoModifiedByLongUsername() {
        NodeMetaInfo nmi =
                new NodeMetaInfo(1, 0, "verylongnamefortestingthe128ofusernameto-dev-ats-openpaas-cc&testing", null);
        assertEquals(nmi.getPropsModifiedBy(), "verylongnamefortestingthe128ofusernameto-dev-ats-openpaas-cc&...");
    }
}
