package org.artifactory.storage.db.environment.converter.shared.version.v2.itest;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.environment.converter.shared.version.v2.ConfigurationManagerCleanupConverter;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.jfrog.config.ConfigurationManagerImpl;
import org.jfrog.storage.util.DbUtils;
import org.jfrog.storage.wrapper.BlobWrapper;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Set;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Uriah Levy
 */
@Test
public class ConfigurationManagerCleanupConverterDbTest extends DbBaseTest {

    @BeforeClass
    public void setup() throws IOException {
        Set<String> oldConfigNames = Set.of("artifactory.binarystore.xml", "artifactory.cluster.license",
                "artifactory.lic", "artifactory.system.properties", "artifactory.ui.logo.png", "artifactory.ui.a/foo" +
                        ".txt", "artifactory.ui.a/b/foo.txt",
                "artifactory.security.artifactory.key", "artifactory.security.access/access.admin.token",
                "artifactory.security.access/keys/access.creds", "artifactory.plugin.latestVersion.groovy",
                "artifactory.plugin.lib/xml-resolver-1.2.jar", "artifactory.plugin.lib/sub-lib/xml-resolver-1.2.jar",
                "artifactory.config.xml", "artifactory.service_id", "artifactory.migration.conan.v2", "licenses.xml",
                "key.config.e46886cf9b12659408a007f78fe617ef75f11024", "artifactory/config/artifactory.cluster" +
                        ".license");

        oldConfigNames.forEach(oldName -> {
            try {
                byte[] bytes = "foo".getBytes();
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
                BlobWrapper blobWrapper = new BlobWrapper(byteArrayInputStream, bytes.length);
                jdbcHelper.executeUpdate("INSERT INTO configs (config_name, last_modified, data) VALUES(?, ?, ?)",
                        oldName, 1, blobWrapper);
            } catch (SQLException e) {
                throw new IllegalStateException("Unable to pre-load configs table with data", e);
            }
        });
    }

    @Test
    public void testConfigurationNamesConverter() throws SQLException {
        ConfigurationManagerCleanupConverter converter = new ConfigurationManagerCleanupConverter();
        converter.convert(ArtifactoryHome.get(), null, null);
        ResultSet resultSet = null;
        ArrayList<String> configNames = new ArrayList<>();
        try {
            resultSet = jdbcHelper.executeSelect("SELECT config_name FROM configs");
            while (resultSet.next()) {
                configNames.add(resultSet.getString("config_name"));
            }
        } finally {
            DbUtils.close(resultSet);
        }

        assertFalse(configNames.contains("artifactory.binarystore.xml"));
        assertTrue(configNames.contains(ConfigurationManagerImpl.BASE_CONFIG_PREFIX + "binarystore.xml"));

        assertFalse(configNames.contains("artifactory.lic"));
        assertTrue(configNames.contains(ConfigurationManagerImpl.BASE_CONFIG_PREFIX + "artifactory.lic"));

        assertFalse(configNames.contains("artifactory.cluster.license"));
        assertTrue(configNames.contains(ConfigurationManagerImpl.BASE_CONFIG_PREFIX + "artifactory.cluster.license"));

        assertFalse(configNames.contains("artifactory.system.properties"));
        assertTrue(configNames.contains(ConfigurationManagerImpl.BASE_CONFIG_PREFIX + "artifactory.system.properties"));

        assertFalse(configNames.contains("artifactory.ui.logo.png"));
        assertTrue(configNames.contains(ConfigurationManagerImpl.BASE_CONFIG_PREFIX + "ui" + File.separator + "logo" +
                ".png"));

        assertFalse(configNames.contains("artifactory.ui.a/foo.txt"));
        assertTrue(configNames.contains(ConfigurationManagerImpl.BASE_CONFIG_PREFIX + "ui" + File.separator + "a" +
                File.separator + "foo.txt"));

        assertFalse(configNames.contains("artifactory.ui.a/b/foo.txt"));
        assertTrue(configNames.contains(ConfigurationManagerImpl.BASE_CONFIG_PREFIX + "ui" + File.separator + "a"
                + File.separator + "b" + File.separator + "foo.txt"));

        assertFalse(configNames.contains("artifactory.security.artifactory.key"));
        assertTrue(configNames.contains(ConfigurationManagerImpl.SECURITY_PREFIX + "artifactory.key"));

        assertFalse(configNames.contains("artifactory.security.access/access.admin.token"));
        assertTrue(configNames.contains(ConfigurationManagerImpl.SECURITY_PREFIX + "access" + File.separator +
                "access.admin.token"));

        assertFalse(configNames.contains("artifactory.security.access/keys/access.creds"));
        assertTrue(configNames.contains(ConfigurationManagerImpl.SECURITY_PREFIX + "access" + File.separator + "keys" +
                File.separator + "access.creds"));

        assertFalse(configNames.contains("artifactory.plugin.latestVersion.groovy"));
        assertTrue(configNames.contains(ConfigurationManagerImpl.BASE_CONFIG_PREFIX + "plugins" + File.separator +
                "latestVersion.groovy"));

        assertFalse(configNames.contains("artifactory.plugin.lib/xml-resolver-1.2.jar"));
        assertTrue(configNames.contains(ConfigurationManagerImpl.BASE_CONFIG_PREFIX + "plugins" + File.separator +
                "lib" + File.separator + "xml-resolver-1.2.jar"));

        assertFalse(configNames.contains("artifactory.plugin.lib/sub-lib/xml-resolver-1.2.jar"));
        assertTrue(configNames.contains(ConfigurationManagerImpl.BASE_CONFIG_PREFIX + "plugins" + File.separator +
                "lib" + File.separator + "sub-lib" + File.separator + "xml-resolver-1.2.jar"));

        assertTrue(configNames.contains("artifactory.config.xml"));
        assertTrue(configNames.contains("artifactory.service_id"));
        assertTrue(configNames.contains("artifactory.migration.conan.v2"));
        assertTrue(configNames.contains("key.config.e46886cf9b12659408a007f78fe617ef75f11024"));
    }
}
