package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.SQLException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

public class V69ServerRoleSizeIncreasedTest extends UpgradeBaseTest {

    @Test(dataProvider = "provideResults")
    public void testServerRoleSizeColumn(ArtifactoryDBVersion version, int expectedSize) throws SQLException,
            IOException {

        resetToVersion(version);

        assertEquals(columnSize("artifactory_servers", "server_role"), expectedSize);

        //check field is not null
        assertFalse(isColumnNullable("artifactory_servers", "server_role"));
    }

    @DataProvider
    public static Object[][] provideResults() {
        return new Object[][] {
                {ArtifactoryDBVersion.v67, 12},
                {ArtifactoryDBVersion.v69, 255},
        };
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v68;
    }
}
