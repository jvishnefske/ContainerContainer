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

package org.artifactory.version;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.backup.BackupDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.descriptor.property.SystemPropertyDescriptor;
import org.artifactory.descriptor.repo.ChecksumPolicyTypeDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.SnapshotVersionBehaviorDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.descriptor.security.PasswordSettings;
import org.artifactory.test.ArtifactoryHomeStub;
import org.artifactory.test.TestUtils;
import org.artifactory.util.RepoLayoutUtils;
import org.artifactory.version.converter.XmlConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.xml.XMLConstants;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.InputStream;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.artifactory.common.ConstantValues.substituteRepoKeys;
import static org.artifactory.version.ArtifactoryConfigVersion.getConfigVersion;
import static org.artifactory.version.ArtifactoryConfigVersion.v100;
import static org.artifactory.version.ArtifactoryConfigVersion.v110;
import static org.artifactory.version.ArtifactoryConfigVersion.v120;
import static org.artifactory.version.ArtifactoryConfigVersion.v130;
import static org.artifactory.version.ArtifactoryConfigVersion.v132;
import static org.artifactory.version.ArtifactoryConfigVersion.v134;
import static org.artifactory.version.ArtifactoryConfigVersion.v135;
import static org.artifactory.version.ArtifactoryConfigVersion.v140;
import static org.artifactory.version.ArtifactoryConfigVersion.v141;
import static org.artifactory.version.ArtifactoryConfigVersion.v1411;
import static org.artifactory.version.ArtifactoryConfigVersion.v1412;
import static org.artifactory.version.ArtifactoryConfigVersion.v142;
import static org.artifactory.version.ArtifactoryConfigVersion.v143;
import static org.artifactory.version.ArtifactoryConfigVersion.v146;
import static org.artifactory.version.ArtifactoryConfigVersion.v179;
import static org.artifactory.version.ArtifactoryConfigVersion.v308;
import static org.artifactory.version.ArtifactoryConfigVersion.v311;
import static org.artifactory.version.ArtifactoryConfigVersion.v3113;
import static org.artifactory.version.ArtifactoryConfigVersion.values;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @author freds
 * @author Yossi Shaul
 */
@Test
public class ConfigXmlConversionTest /** Don't extend ArtHomeBoundTest */
{
    private static final Logger log = LoggerFactory.getLogger(ConfigXmlConversionTest.class);
    private ArtifactoryHomeStub artifactory;

    @BeforeMethod
    public void setup() {
        artifactory = new ArtifactoryHomeStub(getClass().getName());
        ArtifactoryHome.bind(artifactory);
    }

    @AfterMethod
    public void cleanup() {
        ArtifactoryHome.unbind();
    }

    public void convert100() throws Exception {
        Properties propTest = new Properties();
        propTest.setProperty(substituteRepoKeys.getPropertyName() + "3rdp-releases", "third-party-releases");
        propTest.setProperty(substituteRepoKeys.getPropertyName() + "3rdp-snapshots", "third-party-snapshots");
        propTest.setProperty(substituteRepoKeys.getPropertyName() + "3rd-party", "third-party");
        // load the repo key substitute
        Map<String, String> subs = (Map<String, String>) TestUtils.invokeStaticMethod(
                ArtifactorySystemProperties.class, "fillRepoKeySubstitute",
                new Class[] {Properties.class}, new Object[] {propTest});
        assertEquals(subs.size(), 3);
        TestUtils.setField(artifactory.getArtifactoryProperties(), "substituteRepoKeys", subs);

        // convert the default config
        CentralConfigDescriptor cc = transform("/config/install/config.1.0.0.xml", v100);
        assertNotNull(cc.getSecurity());

        // convert custom config
        cc = transform("/config/test/config.1.0.0.xml", v100);
        Map<String, LocalRepoDescriptor> localRepos = cc.getLocalRepositoriesMap();

        LocalRepoDescriptor frogReleases = localRepos.get("frog-releases");
        assertEquals(frogReleases.getSnapshotVersionBehavior(), SnapshotVersionBehaviorDescriptor.DEPLOYER,
                "Should have been converted from 'true' to 'deployer'");

        LocalRepoDescriptor frogSnapshots = localRepos.get("frog-snapshots");
        assertEquals(frogSnapshots.getSnapshotVersionBehavior(), SnapshotVersionBehaviorDescriptor.NONUNIQUE,
                "Should have been converted from 'false' to 'non-unique'");

        LocalRepoDescriptor pluginsReleases = localRepos.get("plugins-releases");
        assertEquals(pluginsReleases.getSnapshotVersionBehavior(), SnapshotVersionBehaviorDescriptor.UNIQUE,
                "Should have kept the default");
    }

    public void convert100NoBackupCron() throws Exception {
        CentralConfigDescriptor cc = transform("/config/test/config.1.0.0_no-backup-cron.xml", v100);
        assertNotNull(cc.getBackups());
        assertTrue(cc.getBackups().isEmpty());
    }

    public void convert110() throws Exception {
        CentralConfigDescriptor cc =
                transform("/config/install/config.1.1.0.xml", v110);
        assertNotNull(cc.getSecurity());
        cc = transform("/config/test/config.1.1.0.xml", v110);
    }

    public void convert120() throws Exception {
        CentralConfigDescriptor cc = transform("/config/install/config.1.2.0.xml", v120);
        cc = transform("/config/test/config.1.2.0.xml", v120);
    }

    public void convert130() throws Exception {
        CentralConfigDescriptor cc = transform("/config/install/config.1.3.0.xml", v130);
        cc = transform("/config/test/config.1.3.0.xml", v130);
        assertNotNull(cc.getSecurity());
    }

    public void convert132Install() throws Exception {
        CentralConfigDescriptor cc = transform("/config/install/config.1.3.2.xml", v132);
        List<BackupDescriptor> backups = cc.getBackups();
        assertEquals(backups.size(), 1);

        // check backups conversion
        BackupDescriptor backup = backups.get(0);
        assertEquals(backup.getKey(), "backup1", "Unexpected backup key generated");
        assertTrue(backup.isEnabled(), "All existing backups should be enabled");
    }

    public void convert132WithNoSchemaLocation() throws Exception {
        CentralConfigDescriptor cc = transform("/config/test/config.1.3.2_no-location.xml", v132);
        assertEquals(cc.getFileUploadMaxSizeMb(), 100);
    }

    public void convert134Install() throws Exception {
        CentralConfigDescriptor cc = transform("/config/install/config.1.3.4.xml", v134);

        // PasswordSettings were added in 1.3.5 - no converter was needed as this is not
        // a required tag and we can use the default
        PasswordSettings passwordSettings = cc.getSecurity().getPasswordSettings();
        assertNotNull(passwordSettings, "Passwords settings should not be null");
    }

    public void convert135Install() throws Exception {
        CentralConfigDescriptor cc = transform("/config/install/config.1.3.5.xml", v135);

        // PropertySets were added in 1.3.6 - no converter was needed as this is not
        // a required tag and we can use the default
        List<PropertySet> propertySets = cc.getPropertySets();
        assertNotNull(propertySets, "Property sets should not be null.");
        assertTrue(propertySets.isEmpty(), "Property sets list should be empty by default.");

        // keypair (no conversion needed)
        VirtualRepoDescriptor virtualRepo = cc.getVirtualRepositoriesMap().values().iterator().next();
        assertNull(virtualRepo.getKeyPair(), "Keypair should be null");
    }

    public void convert135ProxyWithDomain() throws Exception {
        CentralConfigDescriptor cc = transform("/config/test/config.1.3.5_proxy-with-domain.xml", v135);

        List<ProxyDescriptor> proxies = cc.getProxies();
        ProxyDescriptor proxy = proxies.get(0);
        if (!"nt-proxy".equals(proxy.getKey())) {
            proxy = proxies.get(1);
        }
        try {
            assertEquals(proxy.getNtHost(), InetAddress.getLocalHost().getHostName(), "Wrong hostname");
        } catch (UnknownHostException e) {
            // in some cases (missing entry /etc/hosts) getLocalHost throws exception. prefer to ignore here
        }
    }

    public void convert140DefaultProxy() throws Exception {
        CentralConfigDescriptor cc = transform("/config/test/config.1.4.0_default-proxy.xml", v140);

        List<ProxyDescriptor> proxies = cc.getProxies();
        ProxyDescriptor proxy = proxies.get(0);
        assertTrue(proxy.isPlatformDefault(), "Proxy not default");
    }

    public void convert141() throws Exception {
        CentralConfigDescriptor cc = transform("/config/install/config.1.4.1.xml", v141);
        assertNull(cc.getLocalRepositoriesMap().get("libs-releases-local").getExcludesPattern());
        assertEquals(cc.getRemoteRepositoriesMap().get("java.net.m2").getExcludesPattern(),
                "commons-*,org/apache/**");
        assertEquals(cc.getRemoteRepositoriesMap().get("java.net.m2").getIncludesPattern(), "**/*");
        assertEquals(cc.getRemoteRepositoriesMap().get("codehaus").getIncludesPattern(),
                "org/**,com/**,net/**");
        assertEquals(cc.getRemoteRepositoriesMap().get("codehaus").getExcludesPattern(),
                "org/apache/**,commons-*,org/artifactory/**,org/jfrog/**");
        assertEquals(cc.getVirtualRepositoriesMap().get("libs-releases").getIncludesPattern(), "**/*");
        assertNull(cc.getVirtualRepositoriesMap().get("libs-releases").getExcludesPattern());
    }

    public void convert141Custom() throws Exception {
        CentralConfigDescriptor cc =
                transform("/config/test/config.1.4.1_with_type.xml", v141);

        assertEquals(cc.getLocalRepositoriesMap().get("libs-snapshots-local").getIncludesPattern(),
                "org/jfrog/**");
        assertNull(cc.getLocalRepositoriesMap().get("libs-snapshots-local").getExcludesPattern());
        assertEquals(cc.getRemoteRepositoriesMap().get("jfrog-libs").getExcludesPattern(),
                "org/apache/maven/**");
    }

    public void convert142Custom() throws Exception {
        CentralConfigDescriptor cc = transform("/config/test/config.1.4.2_with_checksum_policy.xml",
                v142);
        assertEquals(cc.getRemoteRepositoriesMap().get("repo1").getChecksumPolicyType(),
                ChecksumPolicyTypeDescriptor.GEN_IF_ABSENT);
    }

    public void convert143WithServerId() throws Exception {
        // just removes an element, so if it passes it's ok
        transform("/config/test/config.1.4.3_with_serverId.xml", v143);
    }

    public void convert308withSystemProperties() throws Exception {
        CentralConfigDescriptor transform = transform("/config/test/config.3.0.8_with-system-property.xml", v308);
        List<SystemPropertyDescriptor> systemProperties = transform.getSystemProperties();
        Assert.assertNotNull(systemProperties);
        Assert.assertEquals(systemProperties.get(0).getKey(), "artifactory.test");
        Assert.assertEquals(systemProperties.get(0).getValue(), "value_test");
    }

    public void convert3113withoutMetadata() throws Exception {
        CentralConfigDescriptor transform =
                transform("/config/test/config.3.1.13_remote_with_metadata_timeout.xml", v3113);
        Map<String, RemoteRepoDescriptor> remoteRepositoriesMap = transform.getRemoteRepositoriesMap();
        remoteRepositoriesMap.forEach((s, remoteRepoDescriptor) -> {
            assertEquals(remoteRepoDescriptor.getMetadataRetrievalTimeoutSecs(), 60);
        });
    }

    public void convert146() throws Exception {
        CentralConfigDescriptor descriptor = transform("/config/test/config.1.4.6_wrong_url.xml", v146);

        //Checks global repo layout definitions were added
        List<RepoLayout> globalRepoLayouts = descriptor.getRepoLayouts();
        assertNotNull(globalRepoLayouts, "Expected to find default global repo layouts after the conversion.");
        assertFalse(globalRepoLayouts.isEmpty(), "Expected to find default global repo layouts after the conversion.");

        assertTrue(EqualsBuilder.reflectionEquals(globalRepoLayouts.get(0), RepoLayoutUtils.MAVEN_2_DEFAULT),
                "Expected first global repo layout to be Maven 2 default.");

        assertTrue(EqualsBuilder.reflectionEquals(globalRepoLayouts.get(1), RepoLayoutUtils.IVY_DEFAULT),
                "Expected second global repo layout to be Ivy default.");

        assertTrue(EqualsBuilder.reflectionEquals(globalRepoLayouts.get(2), RepoLayoutUtils.GRADLE_DEFAULT_DEPRECATED),
                "Expected third global repo layout to be Gradle default.");

        assertTrue(EqualsBuilder.reflectionEquals(globalRepoLayouts.get(3), RepoLayoutUtils.MAVEN_1_DEFAULT),
                "Expected fourth global repo layout to be Maven 1 default.");

        //Checks local repo layout definitions were added
        for (Map.Entry<String, LocalRepoDescriptor> localEntry : descriptor.getLocalRepositoriesMap().entrySet()) {
            assertTrue(EqualsBuilder.reflectionEquals(localEntry.getValue().getRepoLayout(),
                    RepoLayoutUtils.MAVEN_2_DEFAULT), "Expected default repo ('" + localEntry.getKey() +
                    "') layout to be Maven 2 default.");
        }

        //Checks remote repo layout definitions were added
        for (Map.Entry<String, RemoteRepoDescriptor> remoteEntry : descriptor.getRemoteRepositoriesMap().entrySet()) {
            String repoKey = remoteEntry.getKey();
            RemoteRepoDescriptor remoteDescriptor = remoteEntry.getValue();
            assertTrue(EqualsBuilder.reflectionEquals(remoteDescriptor.getRepoLayout(),
                    RepoLayoutUtils.MAVEN_2_DEFAULT), "Expected default repo ('" + repoKey +
                    "') layout to be Maven 2 default.");

            if ("java.net.m1".equals(repoKey)) {
                assertTrue(EqualsBuilder.reflectionEquals(remoteDescriptor.getRemoteRepoLayout(),
                        RepoLayoutUtils.MAVEN_1_DEFAULT), "Expected default remote repo ('" + repoKey +
                        "') layout to be Maven 1 default.");
            }
        }
    }

    // TORE: rename the method and config - they aren't for 1.5.0
    public void convert150custom() throws Exception {
        CentralConfigDescriptor cc = transform("/config/test/config.1.5.0_indexing.xml", v1411);
        assertEquals(cc.getIndexer().getCronExp(), "0 23 5 * * ?");
    }

    public void convert1412custom() throws Exception {
        CentralConfigDescriptor cc = transform("/config/test/config.1.4.12_failed_retrieval_cache.xml", v1412);
        RemoteRepoDescriptor repo = cc.getRemoteRepositoriesMap().get("java.net.m2");
        assertEquals(repo.getAssumedOfflinePeriodSecs(), 300);
    }

    public void convert179custom() throws Exception {
        transform("/config/test/config.1.7.9-external-providers.xml", v179);
    }

    public void convert311ForOnlyDockerhubRepos() throws Exception {
        CentralConfigDescriptor transform =
                transform("/config/test/config.3.1.1_docker_retrieval_period.xml", v311);
        assertEquals(
                transform.getRemoteRepositoriesMap().get("bintray-docker-remote").getRetrievalCachePeriodSecs(),
                7200L);
        assertEquals(
                transform.getRemoteRepositoriesMap().get("docker-remote-https").getRetrievalCachePeriodSecs(),
                21600L);
        assertEquals(
                transform.getRemoteRepositoriesMap().get("docker-remote-http").getRetrievalCachePeriodSecs(),
                21600L);
    }

    public void configVersionSanityCheck() {
        ArtifactoryConfigVersion[] versions = values();
        Set<XmlConverter> allConversions = new HashSet<>();
        ArtifactoryConfigVersion configVersionTest = null;
        for (ArtifactoryConfigVersion configVersion : versions) {
            XmlConverter[] versionConversions = configVersion.getConverters();
            for (XmlConverter converter : versionConversions) {
                assertFalse(allConversions.contains(converter), "XML Converter element can only be used once!\n" +
                        "XML Converter " + converter + " is used in " + configVersion + " but was already used.");
                allConversions.add(converter);
            }
            configVersionTest = configVersion;
        }

        boolean configNotCurrent = configVersionTest != getCurrentVersion();
        assertFalse(configNotCurrent, "The last config version " + configVersionTest +
                " is not the current one " + getCurrentVersion());

        // The last should not have any conversion
        XmlConverter[] currentConversions = getCurrentVersion().getConverters();
        boolean noConversionsLeft = (currentConversions != null) && (currentConversions.length > 0);
        assertFalse(noConversionsLeft, "The last config version " + configVersionTest +
                " should not have any conversions declared");
    }

    /**
     * @param textXml The configuration as string
     * @param version Version of the passed configuration
     * @return Result central config
     */
    private CentralConfigDescriptor transform(String textXml, ArtifactoryConfigVersion version)
            throws Exception {
        InputStream is = getClass().getResourceAsStream(textXml);
        String originalXmlString = IOUtils.toString(is, StandardCharsets.UTF_8);
        ArtifactoryConfigVersion foundConfigVersion = getConfigVersion(originalXmlString);
        assertEquals(version, foundConfigVersion);
        String finalConfigXml = version.convert(originalXmlString, false);
        log.debug("Converted:\n{}\nto:\n{}", originalXmlString, finalConfigXml);
        return getConfigValid(finalConfigXml);
    }
    private CentralConfigDescriptor getConfigValid(String configXml) throws Exception {
        JAXBContext context = JAXBContext.newInstance(CentralConfigDescriptorImpl.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        URL xsdUrl = getClass().getResource("/artifactory.xsd");
        Schema schema = sf.newSchema(xsdUrl);
        unmarshaller.setSchema(schema);
        return (CentralConfigDescriptor) unmarshaller.unmarshal(new StringReader(configXml));
    }

    private ArtifactoryConfigVersion getCurrentVersion() {
        ArtifactoryConfigVersion[] artifactoryConfigVersions = values();
        return artifactoryConfigVersions[artifactoryConfigVersions.length - 1];
    }
}
