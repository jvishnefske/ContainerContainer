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

package org.artifactory.descriptor.config;

import com.jfrog.sysconf.SysConfig;
import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.SerializationUtils;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.backup.BackupDescriptor;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.descriptor.property.SystemPropertyDescriptor;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.descriptor.security.ProxyServices;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.util.AlreadyExistsException;
import org.jfrog.access.common.ServiceType;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the MutableCentralConfigDescriptorImpl class.
 *
 * @author Yossi Shaul
 */
@Test
public class CentralConfigDescriptorImplTest {
    private CentralConfigDescriptorImpl cc;

    @BeforeMethod
    public void initCentralConfig() {
        cc = CentralConfigTestUtils.initCentralConfig();
        ArtifactoryHome home = mock(ArtifactoryHome.class);
        SysConfig sysConfig = mock(SysConfig.class);
        when(home.getSysConfig()).thenReturn(sysConfig);
        ArtifactoryHome.bind(home);
    }

    @AfterMethod
    public void cleanup() throws Exception {
        ArtifactoryHome.unbind();
    }

    public void defaultsTest() {
        CentralConfigDescriptorImpl cc = new CentralConfigDescriptorImpl();
        assertNotNull(cc.getLocalRepositoriesMap(), "Local repos map should not be null");
        assertNotNull(cc.getRemoteRepositoriesMap(), "Remote repos map should not be null");
        assertNotNull(cc.getFederatedRepositoriesMap(), "Federated repos map should not be null");
        assertNotNull(cc.getVirtualRepositoriesMap(), "Virtual repos map should not be null");
        assertNotNull(cc.getBackups(), "Backups list should not be null");
        assertNotNull(cc.getProxies(), "Proxies list should not be null");
        assertNotNull(cc.getPropertySets(), "Property sets list should not be null");
        assertNull(cc.getIndexer(), "Indexer should not be null");
        assertNotNull(cc.getSecurity(), "Security should not be null");
        assertNull(cc.getServerName(), "Server name should not be null");
        assertTrue(cc.getFileUploadMaxSizeMb() > 50,
                "Default max file upload size should be bigger than 50mb");
        assertFalse(cc.isOfflineMode(), "Offline mode should be false by default");
        assertNotNull(cc.getRepoLayouts(), "Repo layouts list should not be null");
        assertNotNull(cc.getRemoteReplications(), "Remote replication list should not be null");
        assertNotNull(cc.getLocalReplications(), "Local replication list should not be null");
    }

    public void uniqueKeyExistence() {
        assertTrue(cc.isKeyAvailable("proxy22"));
        cc.setSecurity(new SecurityDescriptor());
        assertTrue(cc.isKeyAvailable("ldap1"));

        assertTrue(cc.isKeyAvailable("repo")); // Global virtual repo has been deprecated

        assertTrue(cc.isPropertySetExists("set2"));

        assertFalse(cc.isKeyAvailable("layout1"));
        assertTrue(cc.isRepoLayoutExists("layout2"));

        assertFalse(cc.isKeyAvailable("keyPair1"));
        assertFalse(cc.isKeyPairExists("keypair2"));
    }

    public void keyPairsExistence() {
        assertEquals(cc.getKeyPairs().size(), 1, "Key pairs count mismatch");
        assertTrue(cc.isKeyPairExists("keyPair1"));
        KeyPairDescriptor keypair1 = cc.getKeyPair("keyPair1");
        assertNotNull(keypair1);
        assertEquals(keypair1.getKey(), "keyPair1");
    }

    public void proxyExistence() {
        assertEquals(cc.getProxies().size(), 2, "Proxies count mismatch");
        assertTrue(cc.isProxyExists("proxy1"));
        assertTrue(cc.isProxyExists("proxy2"));
    }

    @Test(dataProvider = "keyProvider")
    public void addReverseProxy(String key, boolean isValid) {
        ReverseProxyDescriptor reverseProxy = new ReverseProxyDescriptor();
        reverseProxy.setKey(key);
        try {
            cc.addReverseProxy(reverseProxy);
            cc.removeReverseProxy(key);
        } catch (IllegalArgumentException e) {
            if (!isValid) {
                Assert.assertNotNull(e);
            } else {
                Assert.fail("Key " + reverseProxy.getKey() + " should be valid");
            }
        }
    }

    public void reverseProxyKeyPrevention() {
        assertFalse(cc.isKeyAvailable("direct"));
        assertFalse(cc.isKeyAvailable("apache"));
        assertFalse(cc.isKeyAvailable("nginx"));
    }

    public void removeProxy() {
        assertTrue(cc.isProxyExists("proxy2"));
        ProxyDescriptor removedProxy = cc.removeProxy("proxy2");
        assertEquals(removedProxy.getKey(), "proxy2");
        assertEquals(cc.getProxies().size(), 1, "Only one proxy expected");
        assertFalse(cc.isProxyExists("proxy2"));
    }

    @Test(dataProvider = "keyProvider", description = "testing add and remove")
    public void addProxy(String proxyKey, boolean isValid) {
        ProxyDescriptor proxyDescriptor = new ProxyDescriptor();
        proxyDescriptor.setKey(proxyKey);
        try {
            cc.addProxy(proxyDescriptor, true);
            cc.removeProxy(proxyKey);
        } catch (IllegalArgumentException e) {
            if (!isValid) {
                Assert.assertNotNull(e);
            } else {
                Assert.fail("Proxy Key " + proxyDescriptor.getKey() + " should be valid");
            }
        }
    }

    public void removeReferencedProxy() {
        ProxyDescriptor proxy = new ProxyDescriptor();
        proxy.setKey("referencedProxy");
        cc.addProxy(proxy, true);

        // set non-default proxy
        LocalReplicationDescriptor localReplication1 = cc.getLocalReplication("local1");
        localReplication1.setProxy(proxy);
        LocalReplicationDescriptor localReplication2 = cc.getLocalReplication("local2");
        localReplication2.setProxy(proxy);

        cc.removeProxy("referencedProxy");

        assertNull(localReplication1.getProxy(), "Proxy should have been removed from the local replication 1.");
        assertNull(localReplication2.getProxy(), "Proxy should have been removed from the local replication 2.");
    }

    public void platformProxyChanged() {
        ProxyDescriptor proxy = new ProxyDescriptor();
        proxy.setKey("defaultProxy");
        proxy.setPlatformDefault(true);
        cc.addProxy(proxy, true);

        ProxyDescriptor newDefaultProxy = new ProxyDescriptor();
        newDefaultProxy.setKey("newDefaultProxy");
        cc.addProxy(newDefaultProxy, true);
        newDefaultProxy.setPlatformDefault(true);
        // Change shouldn't update the proxies
        cc.proxyChanged(newDefaultProxy, false);

        LocalReplicationDescriptor localReplication = cc.getLocalReplication("local1");
        assertNull(localReplication.getProxy(), "Local replication should have a proxy");

        assertFalse(proxy.isPlatformDefault(), "Original proxy should now be false");

        // Change the platformdefault
        proxy.setPlatformDefault(true);
        newDefaultProxy.setPlatformDefault(true);
        // Change should update proxies
        cc.proxyChanged(newDefaultProxy, true);

        localReplication = cc.getLocalReplication("local1");
        assertNull(localReplication.getProxy(), "Local replication should have a proxy");
    }

    public void artifactoryProxyChanged() {
        ProxyDescriptor proxy = new ProxyDescriptor();
        proxy.setKey("defaultProxy");
        proxy.setServices(ServiceType.ARTIFACTORY);
        cc.addProxy(proxy, true);

        ProxyDescriptor newArtifactoryProxy = new ProxyDescriptor();
        newArtifactoryProxy.setKey("newArtifactoryProxy");
        cc.addProxy(newArtifactoryProxy, true);
        newArtifactoryProxy.setServices(ServiceType.ARTIFACTORY);
        cc.proxyChanged(newArtifactoryProxy, true);

        LocalReplicationDescriptor localReplication1 = cc.getLocalReplication("local1");
        assertFalse(localReplication1.isDisableProxy(), "Proxy is enabled");
        assertNull(localReplication1.getProxy(), "Local replication shouldnt have a proxy");

        LocalReplicationDescriptor localReplication2 = cc.getLocalReplication("local2");
        assertTrue(localReplication2.isDisableProxy(), "Proxy is disabled");
        assertNull(localReplication2.getProxy(), "Local replication shouldn't have a proxy");

        assertFalse(proxy.isPlatformDefault(), "Original proxy should now be false");

        proxy.setServices(null);
        cc.proxyChanged(proxy, true);

        assertNull(localReplication1.getProxy(), "Remote repo should have a proxy");
        assertNull(localReplication2.getProxy(), "Local replication shouldn't have a proxy");
    }

    public void proxyServicesChanged() {
        ProxyDescriptor proxy = new ProxyDescriptor();
        proxy.setKey("defaultProxy");
        proxy.setServices(String.join(",", ServiceType.ARTIFACTORY, ServiceType.XRAY));
        cc.addProxy(proxy, true);

        ProxyDescriptor newProxy = new ProxyDescriptor();
        newProxy.setKey("newProxy");
        cc.addProxy(newProxy, true);
        newProxy.setServices(ServiceType.ARTIFACTORY);
        cc.proxyChanged(newProxy, true);

        assertEquals(proxy.getServices(), ServiceType.XRAY, "Proxy service does not match");
        assertEquals(newProxy.getServices(), ServiceType.ARTIFACTORY, "Proxy service does not match");
        assertEquals(newProxy.getServicesList(), Collections.singletonList(ServiceType.ARTIFACTORY),
                "Proxy service does not match");

        assertFalse(proxy.isPlatformDefault(), "Original proxy should be false");

        //

        proxy.setPlatformDefault(true);
        proxy.setServices(null);
        cc.proxyChanged(proxy, true);

        assertNull(newProxy.getServices(), "Other proxy services should be null, as there is another default");
        assertFalse(newProxy.isPlatformDefault(), "New proxy should be false");

        // Testing removal of default proxy of OTHER proxy in list

        newProxy.setServices(ServiceType.ARTIFACTORY);
        cc.proxyChanged(newProxy, true);

        assertFalse(proxy.isPlatformDefault(), "Original proxy should be false");
        assertEquals(proxy.getServicesList().size(), ProxyServices.VALID_PROXY_SERVICES.size() - 1,
                "The number of services should be all except Artifactory");
    }

    public void backupExistence() {
        assertEquals(cc.getBackups().size(), 3, "Backups count mismatch");
        assertTrue(cc.isBackupExists("backup1"));
        assertTrue(cc.isBackupExists("backup2"));
        assertTrue(cc.isBackupExists("backup3"));

    }

    public void removeBackup() {
        assertTrue(cc.isBackupExists("backup2"));
        BackupDescriptor removedBackup = cc.removeBackup("backup2");
        assertEquals(removedBackup.getKey(), "backup2");
        assertEquals(cc.getBackups().size(), 2, "Only 2 backups expected");
        assertFalse(cc.isBackupExists("backup2"));
    }

    public void testAddBackupKeyValidationWithValidName() {
        String validName = "A-valid_backup.name1";
        BackupDescriptor descriptor = new BackupDescriptor();
        descriptor.setKey(validName);
        cc.addBackup(descriptor);
        assertTrue(cc.getBackups().stream().map(BackupDescriptor::getKey).toList().contains(validName));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testAddBackupKeyValidationWithInvalidName() {
        String invalidName = "invalid:name";
        BackupDescriptor descriptor = new BackupDescriptor();
        descriptor.setKey(invalidName);
        cc.addBackup(descriptor);
    }

    public void testConditionallyAddToBackups() {
        cc.setBackups(new ArrayList<>(Arrays.asList(new BackupDescriptor(), new BackupDescriptor(),
                new BackupDescriptor())));
        assertFalse(cc.conditionallyAddToBackups("test-repo"));
        assertFalse(cc.getBackups().stream().anyMatch(b -> !b.getExcludedRepositories().isEmpty()));

        List<BackupDescriptor> backupDescriptors = cc.getBackups();
        assertEquals(backupDescriptors.size(), 3, "Backups count mismatch");
        backupDescriptors.forEach(backupDescriptor -> backupDescriptor.setExcludeNewRepositories(true));
        backupDescriptors.add(new BackupDescriptor());
        cc.setBackups(backupDescriptors);

        assertTrue(cc.conditionallyAddToBackups("test-repo"));

        assertEquals(cc.getBackups().stream().filter(BackupDescriptor::isExcludeNewRepositories).count(), 3,
                "Excluded repositories count mismatch");
        assertEquals(cc.getBackups().stream().filter(b -> !b.isExcludeNewRepositories()).count(), 1,
                "Included repositories count mismatch");
        // Verify the state of each BackupDescriptor
        for (BackupDescriptor backupDescriptor : cc.getBackups()) {
            if (backupDescriptor.isExcludeNewRepositories()) {
                assertEquals(backupDescriptor.getExcludedRepositories().size(), 1,
                        "Excluded repositories size mismatch");
                assertTrue(backupDescriptor.getExcludedRepositories().contains("test-repo"),
                        "Repository not found in excluded list");
            } else {
                assertTrue(backupDescriptor.getExcludedRepositories().isEmpty(),
                        "Included repositories list is not empty");
            }
        }
    }

    public void propertySetExistence() {
        assertEquals(cc.getPropertySets().size(), 2, "The config should contain 2 property sets");
        assertTrue(cc.isPropertySetExists("set1"), "The config should contain the property set: 'set1'");
        assertTrue(cc.isPropertySetExists("set2"), "The config should contain the property set: 'set2'");
    }

    @Test(dataProvider = "keyProvider", description = "testing add and remove")
    public void addPropertySet(String key, boolean isValid) {
        PropertySet propertySet = new PropertySet();
        propertySet.setName(key);
        try {
            cc.addPropertySet(propertySet);
            cc.removePropertySet(key);
        } catch (IllegalArgumentException e) {
            if (!isValid) {
                Assert.assertNotNull(e);
            } else {
                Assert.fail("Key " + propertySet.getName() + " should be valid");
            }
        }
    }

    public void removePropertySet() {
        PropertySet setToRemove = new PropertySet();
        String setName = "toRemove";
        setToRemove.setName(setName);
        cc.addPropertySet(setToRemove);

        assertNotNull(cc.removePropertySet(setName), "The property set should have been removed from the central " +
                "config descriptor.");
    }

    public void repoLayoutExistence() {
        assertEquals(cc.getRepoLayouts().size(), 2, "The config should contain 2 repository layouts");
        assertTrue(cc.isRepoLayoutExists("layout1"), "The config should contain the repo layout: 'layout1'");
        assertTrue(cc.isRepoLayoutExists("layout2"), "The config should contain the repo layout: 'layout2'");
    }

    @Test(dataProvider = "keyProvider", description = "testing add and remove")
    public void addRepoLayout(String name, boolean isValid) {
        RepoLayout repoLayout = new RepoLayout();
        repoLayout.setName(name);
        try {
            cc.addRepoLayout(repoLayout);
            cc.removeRepoLayout(name);
        } catch (IllegalArgumentException e) {
            if (!isValid) {
                Assert.assertNotNull(e);
            } else {
                Assert.fail("Key " + repoLayout.getName() + " should be valid");
            }
        }
    }

    @Test(dataProvider = "keyProvider", description = "testing add and remove")
    public void addKeyPair(String key, boolean isValid) {
        KeyPairDescriptor keyPair = new KeyPairDescriptor();
        keyPair.setKey(key);
        try {
            cc.addKeyPair(keyPair);
            cc.removeKeyPair(key);
        } catch (IllegalArgumentException e) {
            if (!isValid) {
                Assert.assertNotNull(e);
            } else {
                Assert.fail("Key " + keyPair.getKey() + " should be valid");
            }
        }
    }

    public void removeKeyPair() {
        // Assert we have the first ome
        assertEquals(cc.getKeyPairs().size(), 1);
        // Add
        KeyPairDescriptor keyPairToRemove = new KeyPairDescriptor();
        String name = "toRemove";
        keyPairToRemove.setKey(name);
        cc.addKeyPair(keyPairToRemove);
        assertEquals(cc.getKeyPairs().size(), 2);

        // Validate key that doesnt exist
        cc.removeKeyPair("doesntExist");

        // Act
        cc.removeKeyPair(name);

        // Assert it was removed
        assertEquals(cc.getKeyPairs().size(), 1);
    }

    public void replicationsExistence() {
        assertNotNull(cc.getRemoteReplication("remote1"));
        assertNotNull(cc.getRemoteReplication("remote2"));
        assertNotNull(cc.getLocalReplication("local1"));
        assertNotNull(cc.getLocalReplication("local2"));
    }

    public void removeReplicationUpdates() {
        RemoteReplicationDescriptor remoteReplication = cc.getRemoteReplication("remote2");
        assertNotNull(remoteReplication, "Expected to find second remote replication.");
        cc.removeRemoteReplication(remoteReplication);
        assertNull(cc.getRemoteReplication("remote2"), "Second remote replication should have been removed.");

        LocalReplicationDescriptor localReplication = cc.getLocalReplication("local2");
        assertNotNull(localReplication, "Expected to find second local replication.");
        cc.removeLocalReplication(localReplication);
        assertNull(cc.getLocalReplication("local2"), "Second local replication should have been removed.");
    }

    public void addSystemPropertiesTest() {
        addSystemProperty();
    }

    @Test(expectedExceptions = AlreadyExistsException.class, expectedExceptionsMessageRegExp = "System property" +
            ".*already exists")
    public void addSystemPropertiesTwiceTest() {
        SystemPropertyDescriptor systemPropertyDescriptor = addSystemProperty();
        cc.addSystemProperty(systemPropertyDescriptor);
    }

    public void removeSystemPropertiesTest() {
        SystemPropertyDescriptor systemPropertyDescriptor = addSystemProperty();

        SystemPropertyDescriptor propertyDescriptor = cc.removeSystemProperty("key");
        assertEquals(systemPropertyDescriptor, propertyDescriptor);

        propertyDescriptor = cc.removeSystemProperty("key");
        assertNull(propertyDescriptor);
    }

    public void isSystemPropertiesExistsTest() {
        addSystemProperty();
        assertTrue(cc.isSystemPropertyExists("key"));
        assertTrue(cc.isSystemPropertyExists("key1"));
        assertFalse(cc.isSystemPropertyExists("nonExistingKey"));
    }

    public void setAndGetSystemPropertiesTest() {
        List<SystemPropertyDescriptor> systemPropertyDescriptors = new ArrayList<>();
        systemPropertyDescriptors.add(createSystemProperty("key", "value"));
        systemPropertyDescriptors.add(createSystemProperty("key2", "value2"));
        systemPropertyDescriptors.add(createSystemProperty("key3", "value3"));

        cc.setSystemProperties(systemPropertyDescriptors);
        List<SystemPropertyDescriptor> systemProperties = cc.getSystemProperties();
        assertEquals(systemProperties, systemPropertyDescriptors);
    }

    public void testGetLocalReplicationsMap() {
        Map<String, Map<String, LocalReplicationDescriptor>> localReplicationsMap = cc.getLocalReplicationsMap();
        assertEquals(localReplicationsMap.size(), cc.getLocalReplications().size());
        List<LocalReplicationDescriptor> localReplications = cc.getLocalReplications();
        localReplications.forEach(localReplicationDescriptor -> {
            Map<String, LocalReplicationDescriptor> perUrlReplication =
                    localReplicationsMap.get(localReplicationDescriptor.getRepoKey());
            assertNotNull(perUrlReplication);
            assertEquals(localReplicationDescriptor, perUrlReplication.get(localReplicationDescriptor.getUrl()));
        });

        LocalReplicationDescriptor localReplicationDescriptor =
                SerializationUtils.clone(cc.getLocalReplications().get(0));
        localReplicationDescriptor.setReplicationKey("cloned");
        cc.addLocalReplication(localReplicationDescriptor);
        Map<String, Map<String, LocalReplicationDescriptor>> localReplicationsMapAfterChange =
                cc.getLocalReplicationsMap();
        assertEquals(localReplicationsMapAfterChange.values().size() + 1, cc.getLocalReplications().size());
        Map<String, LocalReplicationDescriptor> perUrlReplication =
                localReplicationsMapAfterChange.get(localReplicationDescriptor.getRepoKey());
        assertEquals(perUrlReplication.size(), 1);
        assertEquals(perUrlReplication.get(localReplicationDescriptor.getUrl()), localReplicationDescriptor);

        LocalReplicationDescriptor localReplicationDescriptor2 =
                SerializationUtils.clone(cc.getLocalReplications().get(0));
        localReplicationDescriptor2.setReplicationKey("cloned2");
        localReplicationDescriptor2.setUrl("http://copied.com");
        cc.addLocalReplication(localReplicationDescriptor2);
        localReplicationsMapAfterChange = cc.getLocalReplicationsMap();
        perUrlReplication = localReplicationsMapAfterChange.get(localReplicationDescriptor.getRepoKey());
        assertEquals(perUrlReplication.size(), 2);
        assertEquals(perUrlReplication.get(localReplicationDescriptor2.getUrl()), localReplicationDescriptor2);

        cc.removeLocalReplication(localReplicationDescriptor2);
        cc.removeLocalReplication(localReplicationDescriptor);
    }

    @Nonnull
    private SystemPropertyDescriptor addSystemProperty() {
        SystemPropertyDescriptor systemPropertyDescriptor = createSystemProperty("key", "Value");
        cc.addSystemProperty(systemPropertyDescriptor);
        assertTrue(cc.getSystemProperties().contains(systemPropertyDescriptor));
        return systemPropertyDescriptor;
    }

    @Nonnull
    private SystemPropertyDescriptor createSystemProperty(String key, String value) {
        return new SystemPropertyDescriptor(key, value);
    }

    @DataProvider
    private static Object[][] keyProvider() {
        return new Object[][] {
                {"", false},
                {"  ", false},
                {"1foo", false},
                {"1", false},
                {"123", false},
                {"$key", false},
                {"key&", false},
                {"key?", false},
                {"key*", false},
                {"key/", false},
                {"key\\", false},
                {"foo", true},
                {"foo-bar", true},
                {"_bar", true}
        };
    }
}

