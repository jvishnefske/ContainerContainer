package org.artifactory.common.config.db;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.SystemUtils;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.util.Files;
import org.jfrog.common.FileUtils;
import org.jfrog.common.YamlUtils;
import org.jfrog.storage.DbType;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.netty.util.internal.StringUtil.EMPTY_STRING;
import static org.jfrog.config.home.JFrogHome.ETC_FOLDER;
import static org.jfrog.config.home.JFrogHome.SYSTEM_YAML_FILENAME;
import static org.jfrog.config.home.JFrogHome.VAR_FOLDER;
import static org.jfrog.storage.DbProperties.Key;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.AssertJUnit.assertFalse;

/**
 * Generates a system.yaml file on-the-fly according to dynamic properties,
 * saves it to disk, and then start a new {@link ArtifactoryDbProperties} instance.
 *
 * @author Shay Bagants
 */
@Test
public class ArtifactoryDbPropertiesLockingParamsTest {

    private File homeDir;
    // system.yaml maps
    private Map<String, Map<String, Map<String, Object>>> dbPropsModel;
    private Map<String, Object> keys;
    private Map<String, String> lockingdbPropsMap;

    @BeforeMethod
    private void setup() {
        homeDir = Files.createRandomDir(SystemUtils.getJavaIoTmpDir(), this.getClass().getName());
        initDbPropsModel();
    }

    @Test(dataProvider = "partialDifferentLockingParams", expectedExceptions = IllegalStateException.class,
            expectedExceptionsMessageRegExp = ".*Mandatory database parameter 'lockingdb.* doesn't exist")
    public void testDifferentDbForLockingNoMinParams(List<String> params) throws IOException {
        setDbParams();

        // set properties of different DB for locking
        addLockingPropWithoutPrefix(params, "xyz");
        keys.put("lockingdb", lockingdbPropsMap);

        ArtifactoryHome internalHome = getArtifactoryHomeWithYaml();
        new ArtifactoryDbProperties(internalHome);
    }

    public void testDifferentDbForLocking() throws IOException {
        setDbParams();

        // set properties of different DB for locking
        addLockingPropWithoutPrefix(ImmutableList.of(Key.LOCKING_DB_TYPE.key()), DbType.ORACLE.name());
        addLockingPropWithoutPrefix(ImmutableList.of(Key.LOCKING_DB_URL.key()), "ext-url");
        addLockingPropWithoutPrefix(ImmutableList.of(Key.LOCKING_DB_USERNAME.key()), "ext-user");
        addLockingPropWithoutPrefix(ImmutableList.of(Key.LOCKING_DB_PASSWORD.key()), "ext-pass");
        addLockingPropWithoutPrefix(ImmutableList.of(Key.LOCKING_DB_DRIVER.key()), "ext-driver");
        keys.put("lockingdb", lockingdbPropsMap);

        ArtifactoryHome internalHome = getArtifactoryHomeWithYaml();
        ArtifactoryDbProperties artifactoryDbProperties = new ArtifactoryDbProperties(internalHome);

        assertEquals(artifactoryDbProperties.getLockingDbSpecificType().get().name(), DbType.ORACLE.name());
        assertEquals(artifactoryDbProperties.getLockingDbConnectionUrl(), "ext-url");
        assertEquals(artifactoryDbProperties.getLockingDbUsername(), "ext-user");
        assertEquals(artifactoryDbProperties.getLockingDbPassword(), "ext-pass");
        assertEquals(artifactoryDbProperties.getLockingDbDriverClass(), "ext-driver");
        assertDbPropsParams(artifactoryDbProperties);
    }

    public void testNoDifferentDbForLocking() throws IOException {
        setDbParams();

        ArtifactoryHome internalHome = getArtifactoryHomeWithYaml();
        ArtifactoryDbProperties artifactoryDbProperties = new ArtifactoryDbProperties(internalHome);

        assertFalse(artifactoryDbProperties.getLockingDbSpecificType().isPresent());
        assertNull(artifactoryDbProperties.getLockingDbConnectionUrl());
        assertNull(artifactoryDbProperties.getLockingDbPassword());
        assertNull(artifactoryDbProperties.getLockingDbUsername());
        assertNull(artifactoryDbProperties.getLockingDbDriverClass());
        assertDbPropsParams(artifactoryDbProperties);
    }

    @DataProvider
    private Object[][] partialDifferentLockingParams() {
        return new Object[][] {
                {ImmutableList.of(Key.LOCKING_DB_TYPE.key())},
                {ImmutableList.of(Key.LOCKING_DB_TYPE.key(), Key.LOCKING_DB_DRIVER.key())},
                {ImmutableList.of(Key.LOCKING_DB_TYPE.key(), Key.LOCKING_DB_URL.key())},
                {ImmutableList.of(Key.LOCKING_DB_DRIVER.key())},
                {ImmutableList.of(Key.LOCKING_DB_DRIVER.key(), Key.LOCKING_DB_TYPE.key())},
                {ImmutableList.of(Key.LOCKING_DB_DRIVER.key(), Key.LOCKING_DB_URL.key())},
                {ImmutableList.of(Key.LOCKING_DB_URL.key())},
                {ImmutableList.of(Key.LOCKING_DB_URL.key(), Key.LOCKING_DB_DRIVER.key())},
                {ImmutableList.of(Key.LOCKING_DB_URL.key(), Key.LOCKING_DB_TYPE.key())}
        };
    }

    private void setDbParams() {
        addPropWithoutPrefix(Key.TYPE.key(), DbType.MSSQL.name());
        addPropWithoutPrefix(Key.USERNAME.key(), "someuser");
        addPropWithoutPrefix(Key.PASSWORD.key(), "somepass");
        addPropWithoutPrefix(Key.URL.key(), "someurl");
        addPropWithoutPrefix(Key.DRIVER.key(), "mssqldriver");
    }

    private void addPropWithoutPrefix(String key, String value) {
        key = key.replace("database.", EMPTY_STRING);
        keys.put(key, value);
    }

    private void addLockingPropWithoutPrefix(List<String> props, String value) {
        props.forEach(key -> lockingdbPropsMap.put(key.replace("lockingdb.", EMPTY_STRING), value));
    }

    private void assertDbPropsParams(ArtifactoryDbProperties dbProperties) {
        assertEquals(dbProperties.getDbType().name(), DbType.MSSQL.name());
        assertEquals(dbProperties.getUsername(), "someuser");
        assertEquals(dbProperties.getPassword(), "somepass");
        assertEquals(dbProperties.getConnectionUrl(), "someurl");
        assertEquals(dbProperties.getDriverClass(), "mssqldriver");
    }

    /**
     * artifactory:
     * database:
     * type: derby
     * url: jdbc:derby:{db.home};create=true
     * lockingdb:
     * type: blat
     */
    private void initDbPropsModel() {
        dbPropsModel = new HashMap<>();
        Map<String, Map<String, Object>> database = new HashMap<>();
        keys = new HashMap<>();
        dbPropsModel.put("artifactory", database);
        database.put("database", keys);
        lockingdbPropsMap = new HashMap<>();
    }

    private ArtifactoryHome getArtifactoryHomeWithYaml() throws IOException {
        Path systemYamlPath = Paths.get(homeDir.getAbsolutePath(), VAR_FOLDER, ETC_FOLDER, SYSTEM_YAML_FILENAME);
        File systemYamlFile = systemYamlPath.toFile();
        // Clean the env before copy the desired yaml
        systemYamlFile.mkdirs();
        systemYamlFile.delete();
        // save the .yaml to file
        byte[] yamlConfig = YamlUtils.getInstance().valueToByteArray(dbPropsModel);
        FileUtils.writeToFileAtomic(systemYamlPath, yamlConfig);
        return new ArtifactoryHome(homeDir.getAbsolutePath());
    }
}