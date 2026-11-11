package org.artifactory.storage.db.fs.entity;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author Andrey Tashlitsky
 */
@Test
public class StatTest {

    public void statLastDownloadedByLongUsername() {
        Stat stat = new Stat(1, 5, 0, "verylongnamefortestingthe128ofusernameto-dev-ats-openpaas-cc&testing", 10, 0,
                "verylongnamefortestingthe128ofusernameto-dev-ats-openpaas-dd&testing");
        assertEquals(stat.getLocalLastDownloadedBy(),
                "verylongnamefortestingthe128ofusernameto-dev-ats-openpaas-cc&...");
        assertEquals(stat.getRemoteLastDownloadedBy(),
                "verylongnamefortestingthe128ofusernameto-dev-ats-openpaas-dd&...");
    }
}
