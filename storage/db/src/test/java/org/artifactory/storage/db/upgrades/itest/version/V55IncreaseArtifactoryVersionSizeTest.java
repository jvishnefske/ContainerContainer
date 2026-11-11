package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.SQLException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

/**
 * @author avishayh.
 */
@Test
public class V55IncreaseArtifactoryVersionSizeTest extends UpgradeBaseTest {

    @Test(dataProvider = "provideResults")
    public void testArtifactoryVersionSizeColumn(ArtifactoryDBVersion version, int expectedSize)
            throws SQLException, IOException {

        resetToVersion(version);

        assertEquals(columnSize("artifactory_servers", "artifactory_version"), expectedSize);
        assertEquals(columnSize("db_properties", "artifactory_version"), expectedSize);

        //check field is not null
        assertFalse(isColumnNullable("artifactory_servers", "artifactory_version"));
        assertFalse(isColumnNullable("db_properties", "artifactory_version"));

    }

    @DataProvider
    public static Object[][] provideResults() {
        return new Object[][] {
                {ArtifactoryDBVersion.v50, 30},
                {ArtifactoryDBVersion.v55, 128},
        };
    }

    @Test(dataProvider = "provideExpectedNullableResults")
    public void testAllOtherFields(String columnName, boolean expected) throws SQLException, IOException {
        resetToVersion(ArtifactoryDBVersion.v48);
        assertEquals(isColumnNullable("artifactory_servers", columnName), expected);
    }


    @DataProvider
    public static Object[][] provideExpectedNullableResults() {
        return new Object[][] {
                {"server_id", false},
                {"start_time", false},
                {"context_url", true},
                {"membership_port", true},
                {"server_state", false},
                {"server_role", false},
                {"last_heartbeat", false},
                {"artifactory_revision", true},
                {"artifactory_release", true},
                {"artifactory_running_mode", false},
                {"license_hash", false},
        };
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v54;
    }
}
