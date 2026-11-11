package org.artifactory.storage.db.environment.converter.shared.version.v2;

import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.config.ConfigurationManagerImpl;
import org.testng.annotations.Test;

import java.io.File;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * @author Uriah Levy
 */
public class ConfigurationManagerCleanupConverterTest extends ArtifactoryHomeBoundTest {

    @Test
    public void testOldFormatToNewFormat() {
        ConfigurationManagerCleanupConverter converter = new ConfigurationManagerCleanupConverter();

        assertEquals(converter.convertOldConfigNameToNewFormat("artifactory.binarystore.xml"),
                ConfigurationManagerImpl.BASE_CONFIG_PREFIX + "binarystore.xml");
        assertEquals(converter.convertOldConfigNameToNewFormat("artifactory.lic"),
                ConfigurationManagerImpl.BASE_CONFIG_PREFIX + "artifactory.lic");
        assertEquals(converter.convertOldConfigNameToNewFormat("artifactory.cluster.license"),
                ConfigurationManagerImpl.BASE_CONFIG_PREFIX + "artifactory.cluster.license");
        assertEquals(converter.convertOldConfigNameToNewFormat("artifactory.mimeType"),
                ConfigurationManagerImpl.BASE_CONFIG_PREFIX + "mimetypes.xml");
        assertEquals(converter.convertOldConfigNameToNewFormat("artifactory.system.properties"),
                ConfigurationManagerImpl.BASE_CONFIG_PREFIX + "artifactory.system.properties");

        // ui
        assertEquals(converter.convertOldConfigNameToNewFormat("artifactory.ui.logo.png"),
                ConfigurationManagerImpl.BASE_CONFIG_PREFIX + "ui" + File.separator + "logo.png");
        assertEquals(converter.convertOldConfigNameToNewFormat("artifactory.ui.a/foo.txt"),
                ConfigurationManagerImpl.BASE_CONFIG_PREFIX + "ui" + File.separator + "a" + File.separator + "foo" +
                        ".txt");
        assertEquals(converter.convertOldConfigNameToNewFormat("artifactory.ui.a/b/foo.txt"),
                ConfigurationManagerImpl.BASE_CONFIG_PREFIX + "ui" + File.separator + "a" + File.separator + "b" +
                        File.separator + "foo.txt");

        // security
        assertEquals(converter.convertOldConfigNameToNewFormat("artifactory.security.artifactory.key"),
                ConfigurationManagerImpl.SECURITY_PREFIX + "artifactory.key");
        assertEquals(converter.convertOldConfigNameToNewFormat("artifactory.security.access/access.admin.token"),
                ConfigurationManagerImpl.SECURITY_PREFIX + "access" + File.separator + "access.admin.token");
        assertEquals(converter.convertOldConfigNameToNewFormat("artifactory.security.access/keys/access.creds"),
                ConfigurationManagerImpl.SECURITY_PREFIX + "access" + File.separator + "keys" + File.separator +
                        "access.creds");

        // plugins
        assertEquals(converter.convertOldConfigNameToNewFormat("artifactory.plugin.latestVersion.groovy"),
                "artifactory" + File.separator + "config" + File.separator + "plugins" + File.separator +
                        "latestVersion.groovy");
        assertEquals(converter.convertOldConfigNameToNewFormat("artifactory.plugin.lib/xml-resolver-1.2.jar"),
                ConfigurationManagerImpl.BASE_CONFIG_PREFIX + "plugins" + File.separator + "lib" + File.separator +
                        "xml-resolver-1.2.jar");
        assertEquals(converter.convertOldConfigNameToNewFormat("artifactory.plugin.lib/sub-lib/xml-resolver-1.2.jar"),
                ConfigurationManagerImpl.BASE_CONFIG_PREFIX + "plugins" + File.separator + "lib" + File.separator +
                        "sub-lib" + File.separator + "xml-resolver-1.2.jar");

        // excluded files
        assertNull(converter.convertOldConfigNameToNewFormat("artifactory.config.xml"));
        assertNull(converter.convertOldConfigNameToNewFormat("artifactory.service_id"));
        assertNull(converter.convertOldConfigNameToNewFormat("artifactory.migration.conan.v2"));

        assertNull(converter.convertOldConfigNameToNewFormat("licenses.xml"));
        assertNull(converter.convertOldConfigNameToNewFormat("key.config.e46886cf9b12659408a007f78fe617ef75f11024"));

        assertNull(converter.convertOldConfigNameToNewFormat("artifactory/config/security/access/keys/access.creds"));
        assertNull(converter.convertOldConfigNameToNewFormat("artifactory/config/ui/a/b/foo.txt"));
    }
}