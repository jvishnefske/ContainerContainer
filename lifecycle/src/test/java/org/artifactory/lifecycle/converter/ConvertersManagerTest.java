/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.lifecycle.converter;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.ha.HaNodeProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ArtifactoryConverterAdapter;
import org.artifactory.converter.ConverterType;
import org.artifactory.lifecycle.converter.helpers.ConvertersManagerTestBase;
import org.artifactory.lifecycle.converter.helpers.MockArtifactoryHome;
import org.artifactory.lifecycle.converter.helpers.MockServiceConverter;
import org.artifactory.lifecycle.webapp.servlet.BasicConfigurationManager;
import org.artifactory.storage.db.DatabaseConverterAdapter;
import org.artifactory.storage.db.LockingDBConverterAdapter;
import org.artifactory.storage.db.converter.markers.ConverterManagerImpl;
import org.artifactory.test.TestUtils;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.fest.assertions.Assertions;
import org.jfrog.config.ifc.ConfigurationManager;
import org.jfrog.converter.DbDetails;
import org.jfrog.storage.DbType;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.artifactory.version.ArtifactoryServiceVersionProvider.v301;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v304;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v310;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v560;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v670;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v680;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Gidi Shabat
 */
@Test
public class ConvertersManagerTest extends ConvertersManagerTestBase {

    @DataProvider
    public static Object[][] accessEmigratorDataProvider() {
        return new Object[][] {
                {false},
                {true}
        };
    }

    @BeforeMethod
    public void setup() {
        System.setProperty(ConstantValues.devHa.getPropertyName(), "true");
    }

    @AfterMethod
    public void cleanAfterCreateEnv() {
        System.clearProperty(ConstantValues.devHa.getPropertyName());
        Optional.ofNullable(configurationManager).ifPresent(ConfigurationManager::destroy);
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
    }

    /**
     * Convert artifactory which home is version 3.0.1  DBProperties is 3.0.1 and cluster version is 3.0.1
     */
    public void convertAll301() throws IOException {
        File home = TestUtils.createTempDir(getClass());
        createEnvironment(home, v301.get(), v301.get());
        // Make sure that the artifactory local home properties.file has been updated
        assertTrue(isArtifactoryLocalHomePropertiesHasBeenUpdated(home));
        // Make sure that the artifactory db properties has been updated
        assertTrue(isServiceConvertWasRun(home));
        // Make sure that the artifactory.properties exist in home
        assertTrue(isArtifactoryPropertiesHasBeenUpdated(home));
    }

    /**
     * Convert artifactory which home is version 3.0.4  DBProperties is 3.0.4 and cluster version is 3.0.4
     */
    public void convertAll304() throws IOException {
        File home = TestUtils.createTempDir(getClass());
        createEnvironment(home, v304.get(), v304.get());
        // Make sure that the artifactory local home properties.file has been updated
        assertTrue(isArtifactoryLocalHomePropertiesHasBeenUpdated(home));
        // Make sure that the artifactory db properties has been updated
        assertTrue(isServiceConvertWasRun(home));
        // Make sure that the artifactory.properties exist in home
        assertTrue(isArtifactoryPropertiesHasBeenUpdated(home));
    }

    @Test(dataProvider = "accessEmigratorDataProvider")
    public void convert560WithIsInterestedOverride(boolean expected) throws IOException {
        File home = TestUtils.createTempDir(getClass());
        createEnvironment(home, v560.get(), v560.get(), new MockServiceConverter(home + "/.history/db.test", expected));
        assertEquals(isServiceConvertWasRun(home), expected);
    }

    @Test
    public void testConvertAlwaysInterested() throws IOException {
        File home = TestUtils.createTempDir(getClass());
        createEnvironment(home, v670.get(), v680.get(), new MockServiceConverter(home + "/.history/db.test", false));
        assertFalse(isServiceConvertWasRun(home));
        ArtifactoryHome artifactoryHome = new ArtifactoryHome(new File(home + "/.artifactory").getAbsolutePath());
        artifactoryHome.initHaNodeProperties();
        HaNodeProperties haNodeProperties = artifactoryHome.getHaNodeProperties();

        File artifactoryHaNodePropertiesFile = artifactoryHome.getArtifactoryHaNodePropertiesFile();
        Properties properties = new Properties();
        try (FileInputStream inputStream = new FileInputStream(artifactoryHaNodePropertiesFile)) {
            properties.load(inputStream);
        }
        assertNotNull(haNodeProperties);
        Assertions.assertThat(properties).isNotEmpty();
        assertFalse(properties.contains("primary"));
    }

    /**
     * Convert artifactory which home is version current  DBProperties is current and cluster version is current
     */
    public void convertAllCurrent() throws IOException {
        File home = TestUtils.createTempDir(getClass());
        ArtifactoryServiceVersion current = ArtifactoryServiceVersion.getCurrent();
        createEnvironment(home, current, current);
        // Make sure that the artifactory local home properties.file has been updated
        assertFalse(isArtifactoryLocalHomePropertiesHasBeenUpdated(home));
        assertFalse(isArtifactorySharedHomePropertiesHasBeenUpdated(home));
        // Make sure that the artifactory db properties has been updated
        assertFalse(isServiceConvertWasRun(home));
        // Make sure that the artifactory.properties exist in home
        assertTrue(isArtifactoryPropertiesHasBeenUpdated(home));
    }

    /**
     * Convert artifactory which home is version null  DBProperties is null and cluster version is null
     */
    public void convertNoVersions() throws IOException {
        File home = TestUtils.createTempDir(getClass());
        createEnvironment(home, null, null);
        // Make sure that the artifactory local home properties.file has been updated
        assertFalse(isArtifactoryLocalHomePropertiesHasBeenUpdated(home));
        // Make sure that the artifactory db properties has been updated
        assertFalse(isServiceConvertWasRun(home));
        // Make sure that the artifactory.properties exist in home
        assertTrue(isArtifactoryPropertiesHasBeenUpdated(home));
    }

    /**
     * Convert artifactory which home is current  DBProperties is 3.0.1 and cluster version is current
     * <p/>
     * In case that the db_properties doesn't exist then take the version from home.
     * This is dangerous: in case that the home version is current no db conversion will run
     */
    public void convertCurrentHomeDb301Cluster() throws IOException {
        File home = TestUtils.createTempDir(getClass());
        ArtifactoryServiceVersion current = ArtifactoryServiceVersion.getCurrent();
        createEnvironment(home, current, v301.get());
        // Make sure that the artifactory local home properties.file has been updated
        assertFalse(isArtifactoryLocalHomePropertiesHasBeenUpdated(home));
        assertFalse(isArtifactorySharedHomePropertiesHasBeenUpdated(home));
        // Make sure that the artifactory db properties has been updated
        assertTrue(isServiceConvertWasRun(home));
        // Make sure that the artifactory.properties exist in home
        assertTrue(isArtifactoryPropertiesHasBeenUpdated(home));
    }

    /**
     * * Convert artifactory which home is version current  DBProperties is 3.1.0 and cluster version is current
     */
    public void convertCurrentHomeOldDbCurrentCluster() throws IOException {
        File home = TestUtils.createTempDir(getClass());
        ArtifactoryServiceVersion current = ArtifactoryServiceVersion.getCurrent();
        createEnvironment(home, current, v310.get());
        // Make sure that the artifactory local home properties.file has been updated
        assertFalse(isArtifactoryLocalHomePropertiesHasBeenUpdated(home));
        // Make sure that the artifactory db properties has been updated
        assertTrue(isServiceConvertWasRun(home));
        // Make sure that the artifactory.properties exist in home
        assertTrue(isArtifactoryPropertiesHasBeenUpdated(home));
    }

    /**
     * Convert artifactory which home is version 3.0.1  DBProperties is current and cluster version is current
     */
    public void convertOldHomeCurrentDbCurrentCluster() throws IOException {
        File home = TestUtils.createTempDir(getClass());
        ArtifactoryServiceVersion current = ArtifactoryServiceVersion.getCurrent();
        createEnvironment(home, v301.get(), current);
        // Make sure that the artifactory local home properties.file has been updated
        assertTrue(isArtifactoryLocalHomePropertiesHasBeenUpdated(home));
        // Make sure that the artifactory db properties has been updated
        assertFalse(isServiceConvertWasRun(home));
        // Make sure that the artifactory.properties exist in home
        assertTrue(isArtifactoryPropertiesHasBeenUpdated(home));
    }

    /**
     * Verify that in case of internal locking DB, there is only DatabaseConverterAdapter
     */
    public void testBasicConfigurationManagerForInternalLockingDB() throws Exception {
        File home = TestUtils.createTempDir(getClass());
        ArtifactoryHome artifactoryHome = spy(new MockArtifactoryHome(home));
        ArtifactoryDbProperties dbProperties = spy(new ArtifactoryDbProperties(artifactoryHome));
        doAnswer(invocation -> dbProperties).when(artifactoryHome).initDBProperties();
        BasicConfigurationManager bcm = createBasicConfigurationManager(home,
                ArtifactoryServiceVersion.getCurrent(), dbProperties, artifactoryHome, true);
        bcm.initialize();
        List<ArtifactoryConverterAdapter> dbConverters =
                ((ConverterManagerImpl) bcm.convertersManager).getConverters().get(ConverterType.DATABASE);

        Assert.assertEquals(dbConverters.size(), 1);
        assertEquals(DatabaseConverterAdapter.class, dbConverters.get(0).getClass());

        //Get the main db details from the converters manager and verify it is initialized with the
        //jdbc helper and db type.
        verifyDbDetails((DatabaseConverterAdapter) dbConverters.get(0));

    }


    public void testBasicConfigurationManagerForExternalLockingDB() throws Exception {
        File home = TestUtils.createTempDir(getClass());
        ArtifactoryHome artifactoryHome = spy(new MockArtifactoryHome(home));
        ArtifactoryDbProperties dbProperties = spy(new ArtifactoryDbProperties(artifactoryHome) {
            public Optional<Boolean> allowNonPgFlag() {
                return Optional.of(true);
            }
        });
        doAnswer(invocation -> dbProperties).when(artifactoryHome).initDBProperties();
        doAnswer(invocation -> Optional.of(DbType.DERBY)).when(dbProperties).getLockingDbSpecificType();
        doAnswer(invocation -> "jdbc:derby:{db.home};create=true").when(dbProperties).getLockingDbConnectionUrl();
        doAnswer(invocation -> "org.apache.derby.jdbc.EmbeddedDriver").when(dbProperties).getLockingDbDriverClass();
        BasicConfigurationManager bcm = createBasicConfigurationManager(home,
                ArtifactoryServiceVersion.getCurrent(), dbProperties, artifactoryHome, false);

        bcm.initialize();

        List<ArtifactoryConverterAdapter> dbConverters =
                ((ConverterManagerImpl) bcm.convertersManager).getConverters().get(ConverterType.DATABASE);

        //Verify 2 converters
        Assert.assertEquals(dbConverters.size(), 2);
        //verify one converter is the standard one DatabaseConverterAdapter
        Optional<ArtifactoryConverterAdapter> dbConverter =
                dbConverters.stream()
                        .filter(converter -> converter.getClass().equals(DatabaseConverterAdapter.class))
                        .findFirst();
        Assert.assertTrue(dbConverter.isPresent());
        //Verify one converter is for external locking db: LockingDBConverterAdapter
        Optional<ArtifactoryConverterAdapter> lockingDbConverter =
                dbConverters.stream()
                        .filter(converter -> converter.getClass().equals(LockingDBConverterAdapter.class))
                        .findFirst();
        Assert.assertTrue(lockingDbConverter.isPresent());

        //Get the main db details from the converters manager and verify it is initialized with the
        //jdbc helper and db type.
        verifyDbDetails((DatabaseConverterAdapter) dbConverter.get());

        //Get the locking db details from the converters manager and verify it is initialized with the
        //jdbc helper and db type.
        verifyDbDetails((LockingDBConverterAdapter) lockingDbConverter.get());
    }

    /**
     * Verify DbDetails in ConverterManager are initialized
     */
    private void verifyDbDetails(DatabaseConverterAdapter converter) throws Exception {
        DbDetails dbDetails = (DbDetails) FieldUtils.readField(
                FieldUtils.getField(DatabaseConverterAdapter.class, "dbDetails", true),
                converter);

        assertNotNull(dbDetails.getJdbcHelper());
        assertNotNull(dbDetails.getDbType());
    }
}






