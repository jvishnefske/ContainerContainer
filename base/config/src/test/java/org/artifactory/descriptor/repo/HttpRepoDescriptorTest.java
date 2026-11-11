package org.artifactory.descriptor.repo;

import com.google.common.collect.Maps;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.common.JsonUtils;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.util.ReflectionUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static com.google.gson.JsonParser.parseString;
import static org.artifactory.descriptor.repo.RandomObjectPopulator.createAndFill;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author Volodymyr Shulga
 */
public class HttpRepoDescriptorTest extends ArtifactoryHomeBoundTest {
    @Mock
    private CentralConfigDescriptor centralConfigDescriptor;

    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    public void parseCredentialsFromUrl() {
        HttpRepoDescriptor repoDescriptor = new HttpRepoDescriptor();

        Assert.assertNull(repoDescriptor.extractCredentialsFromUrl("http://localhost:8080/"));

        final String[] credentialsForHttp = repoDescriptor.extractCredentialsFromUrl(
                "https://admin:Password1@localhost:8080/conan-smart-remote"
        );
        Assert.assertNotNull(credentialsForHttp);
        assertEquals(credentialsForHttp[0], "admin");
        assertEquals(credentialsForHttp[1], "Password1");

        final String[] credentialsForHttps = repoDescriptor.extractCredentialsFromUrl(
                "http://admin:Password1@localhost:8080/conan-smart-remote"
        );
        Assert.assertNotNull(credentialsForHttps);
        assertEquals(credentialsForHttps[0], "admin");
        assertEquals(credentialsForHttps[1], "Password1");

        final String[] credentialsForEncodedAtSymbol = repoDescriptor.extractCredentialsFromUrl(
                "https://localhost:8080/%40conan-smart-remote"
        );
        Assert.assertNull(credentialsForEncodedAtSymbol);

        final String[] credentialsForNonEncodedAtSymbol = repoDescriptor.extractCredentialsFromUrl(
                "https://localhost:8080/@conan-smart-remote"
        );
        Assert.assertNull(credentialsForNonEncodedAtSymbol);

        final String[] nullCredentials = repoDescriptor.extractCredentialsFromUrl(
                "https://null:null@localhost:8080"
        );
        Assert.assertNotNull(nullCredentials);
        assertEquals(nullCredentials[0], "null");
        assertEquals(nullCredentials[1], "null");

        final String[] emptyCredentialsWithDots = repoDescriptor.extractCredentialsFromUrl(
                "https://:@localhost:8080"
        );
        Assert.assertNull(emptyCredentialsWithDots);

        final String[] emptyCredentials = repoDescriptor.extractCredentialsFromUrl(
                "https://aaa@localhost:8080"
        );
        Assert.assertNull(emptyCredentials);

        final String[] nullURL = repoDescriptor.extractCredentialsFromUrl(null);
        Assert.assertNull(nullURL);
    }

    @Test
    public void testDescriptorToRepositoryConfigMapping() throws Exception {
        // given
        HttpRepoDescriptor sourceHttpRepoDescriptor = createAndFill(HttpRepoDescriptor.class);
        sourceHttpRepoDescriptor.getAnsible().setToken(null);
        ExternalDependenciesConfig externalDependencies = new ExternalDependenciesConfig();
        externalDependencies.setEnabled(true);
        externalDependencies.setPatterns(List.of("pattern1", "pattern2"));
        sourceHttpRepoDescriptor.setExternalDependencies(externalDependencies);

        // Generating all types of package configs
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        for (RepoType value : RepoType.values()) {
            sourceHttpRepoDescriptor.buildPackageTypeConfig(value, packageTypeConfig);
        }

        RemoteRepoConfig sourceRepositoryConfig = sourceHttpRepoDescriptor.toRepositoryConfig();
        Field packageTypeConfigField = ReflectionUtils.findField(RemoteRepoConfig.class, "packageTypeConfig");
        Assert.assertNotNull(packageTypeConfigField);
        ReflectionUtils.makeAccessible(packageTypeConfigField);
        ReflectionUtils.setField(packageTypeConfigField, sourceRepositoryConfig, packageTypeConfig);
        // handle references
        ProxyDescriptor proxyDescriptor = new ProxyDescriptor();
        proxyDescriptor.setKey(sourceHttpRepoDescriptor.getProxy().getKey());
        RepoLayout repoLayout = new RepoLayout();
        repoLayout.setName(sourceHttpRepoDescriptor.getRepoLayout().getName());
        KeyPairDescriptor primaryKeyPair = new KeyPairDescriptor();
        primaryKeyPair.setKey(sourceHttpRepoDescriptor.getPrimaryKeyPairDescriptor().getKey());
        KeyPairDescriptor secondaryKeyPair = new KeyPairDescriptor();
        secondaryKeyPair.setKey(sourceHttpRepoDescriptor.getSecondaryKeyPairDescriptor().getKey());
        RepoLayout remoteRepoLayout = new RepoLayout();
        remoteRepoLayout.setName(sourceHttpRepoDescriptor.getRemoteRepoLayout().getName());
        PropertySet propertySet1 = new PropertySet();
        propertySet1.setName("propertySetRef1");
        PropertySet propertySet2 = new PropertySet();
        propertySet2.setName("propertySetRef2");

        when(centralConfigDescriptor.getRepoLayouts()).thenReturn(List.of(repoLayout, remoteRepoLayout));
        when(centralConfigDescriptor.getProxies()).thenReturn(List.of(proxyDescriptor));
        when(centralConfigDescriptor.getPropertySets()).thenReturn(List.of(propertySet1, propertySet2));
        when(centralConfigDescriptor.getKeyPairs()).thenReturn(List.of(primaryKeyPair, secondaryKeyPair));

        // when
        HttpRepoDescriptor mappedHttpDescriptor =
                HttpRepoDescriptor.fromRepositoryConfig(centralConfigDescriptor, sourceRepositoryConfig);

        for (RepoType value : RepoType.values()) {
            mappedHttpDescriptor.setPackageTypeConfig(sourceRepositoryConfig.getPackageTypeConfig(), value);
        }

        String sourceHttpRepoDescriptorJson = JsonUtils.getInstance().writeValueAsString(sourceHttpRepoDescriptor);
        String mappedHttpRepoDescriptorJson = JsonUtils.getInstance().writeValueAsString(mappedHttpDescriptor);

        System.out.println(sourceHttpRepoDescriptorJson);
        System.out.println(mappedHttpRepoDescriptorJson);

        assertEquals(parseString(mappedHttpRepoDescriptorJson), parseString(sourceHttpRepoDescriptorJson));
        assertNugetNormalizedVersion(sourceHttpRepoDescriptor, mappedHttpDescriptor);
    }

    private void assertNugetNormalizedVersion(HttpRepoDescriptor sourceHttpDescriptor,
            HttpRepoDescriptor mappedHttpDescriptor) {
        assertEquals(mappedHttpDescriptor.isEnableNormalizedVersion(),
                sourceHttpDescriptor.isEnableNormalizedVersion());
    }

    /**
     * A brand-new/pre-existing repo that never touched Cargo-specific settings (no {@code cargoConfig} at all,
     * e.g. a repo persisted before this field existed) must report the sparse HTTP index as enabled - not the
     * legacy Git index - matching {@link CargoConfiguration}'s own default.
     */
    @Test
    public void isCargoInternalIndexDefaultsToTrueWhenUnset() {
        HttpRepoDescriptor descriptor = new HttpRepoDescriptor();

        Assert.assertTrue(descriptor.isCargoInternalIndex(), "Cargo repos should default to the sparse index");
    }

    @Test
    public void isCargoInternalIndexReflectsExplicitlySetValue() {
        HttpRepoDescriptor descriptor = new HttpRepoDescriptor();
        CargoConfiguration cargoConfig = new CargoConfiguration();
        cargoConfig.setCargoInternalIndex(false);
        descriptor.setCargoConfig(cargoConfig);

        assertEquals(descriptor.isCargoInternalIndex(), false, "Explicit cargoConfig value should be honored");
    }

    @Test
    public void isCargoInternalIndexReflectsExplicitlySetTrueValue() {
        HttpRepoDescriptor descriptor = new HttpRepoDescriptor();
        CargoConfiguration cargoConfig = new CargoConfiguration();
        cargoConfig.setCargoInternalIndex(true);
        descriptor.setCargoConfig(cargoConfig);

        Assert.assertTrue(descriptor.isCargoInternalIndex(), "Explicit cargoConfig value should be honored");
    }

    @Test
    public void testAnsibleTypeConfigRoundTrip() {
        HttpRepoDescriptor descriptor = new HttpRepoDescriptor();
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        packageTypeConfig.put("authUrl", "https://sso.example.com/auth");

        descriptor.setPackageTypeConfig(packageTypeConfig, RepoType.Ansible);

        Map<String, String> rebuilt = Maps.newHashMap();
        descriptor.buildPackageTypeConfig(RepoType.Ansible, rebuilt);

        assertEquals(rebuilt.get("authUrl"), "https://sso.example.com/auth");
    }

    @Test
    public void testAnsibleTypeConfigEmptyValues() {
        HttpRepoDescriptor descriptor = new HttpRepoDescriptor();
        Map<String, String> packageTypeConfig = Maps.newHashMap();

        descriptor.setPackageTypeConfig(packageTypeConfig, RepoType.Ansible);

        Map<String, String> rebuilt = Maps.newHashMap();
        descriptor.buildPackageTypeConfig(RepoType.Ansible, rebuilt);

        Assert.assertFalse(rebuilt.containsKey("authUrl"));
    }
}