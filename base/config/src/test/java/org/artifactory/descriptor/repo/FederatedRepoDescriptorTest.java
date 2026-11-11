package org.artifactory.descriptor.repo;

import com.google.common.collect.Maps;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.common.JsonUtils;
import org.junit.jupiter.api.Assertions;
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
import static org.artifactory.repo.xray.XrayNotAvailableMode.FAIL_CLOSE;
import static org.artifactory.repo.xray.XrayNotAvailableMode.USE_SYSTEM_PROPERTY;
import static org.artifactory.descriptor.repo.RandomObjectPopulator.createAndFill;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author Tamar Rosen
 */
public class FederatedRepoDescriptorTest extends ArtifactoryHomeBoundTest {
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
    public void testDescriptorToAndFromRepositoryConfig() throws Exception {
        FederatedRepoDescriptor sourceFedRepoDescriptor = createAndFill(FederatedRepoDescriptor.class);

        // Generating all types of package configs
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        for (RepoType value : RepoType.values()) {
            sourceFedRepoDescriptor.buildPackageTypeConfig(value, packageTypeConfig);
        }

        RepoLayout repoLayout = new RepoLayout();
        repoLayout.setName(sourceFedRepoDescriptor.getRepoLayout().getName());
        KeyPairDescriptor primaryKeyPair = new KeyPairDescriptor();
        primaryKeyPair.setKey(sourceFedRepoDescriptor.getPrimaryKeyPairDescriptor().getKey());
        KeyPairDescriptor secondaryKeyPair = new KeyPairDescriptor();
        secondaryKeyPair.setKey(sourceFedRepoDescriptor.getSecondaryKeyPairDescriptor().getKey());

        PropertySet propertySet1 = new PropertySet();
        propertySet1.setName("propertySetRef1");
        PropertySet propertySet2 = new PropertySet();
        propertySet2.setName("propertySetRef2");
        ProxyDescriptor proxyDescriptor = new ProxyDescriptor();
        proxyDescriptor.setKey(sourceFedRepoDescriptor.getProxy().getKey());

        when(centralConfigDescriptor.getRepoLayouts()).thenReturn(List.of(repoLayout));
        when(centralConfigDescriptor.getPropertySets()).thenReturn(List.of(propertySet1, propertySet2));
        when(centralConfigDescriptor.getKeyPairs()).thenReturn(List.of(primaryKeyPair, secondaryKeyPair));
        when(centralConfigDescriptor.getProxies()).thenReturn(List.of(proxyDescriptor));

        FederatedRepoConfig sourceRepositoryConfig = sourceFedRepoDescriptor.toRepositoryConfig();
        Field packageTypeConfigField = ReflectionUtils.findField(FederatedRepoConfig.class, "packageTypeConfig");
        Assert.assertNotNull(packageTypeConfigField);
        ReflectionUtils.makeAccessible(packageTypeConfigField);
        ReflectionUtils.setField(packageTypeConfigField, sourceRepositoryConfig, packageTypeConfig);

        FederatedRepoDescriptor mappedLocalDescriptor =
                FederatedRepoDescriptor.fromRepositoryConfig(centralConfigDescriptor, sourceRepositoryConfig);

        for (RepoType value : RepoType.values()) {
            mappedLocalDescriptor.setPackageTypeConfig(sourceRepositoryConfig.getPackageTypeConfig(), value);
        }
        String sourceFedRepoDescriptorJson = JsonUtils.getInstance().writeValueAsString(sourceFedRepoDescriptor);
        String mappedFedRepoDescriptorJson = JsonUtils.getInstance().writeValueAsString(mappedLocalDescriptor);

        System.out.println(sourceFedRepoDescriptorJson);
        System.out.println(mappedFedRepoDescriptorJson);

        assertEquals(parseString(mappedFedRepoDescriptorJson), parseString(sourceFedRepoDescriptorJson));
    }

    @Test
    void testIgnoreHelmEnforceLayout() {
        FederatedRepoDescriptor federatedRepoDescriptor = new FederatedRepoDescriptor();
        Assertions.assertFalse(federatedRepoDescriptor.isForceMetadataNameVersion());
        Assertions.assertFalse(federatedRepoDescriptor.isForceNonDuplicateChart());
        federatedRepoDescriptor.setForceMetadataNameVersion(true);
        federatedRepoDescriptor.setForceNonDuplicateChart(true);
        Assertions.assertFalse(federatedRepoDescriptor.isForceMetadataNameVersion());
        Assertions.assertFalse(federatedRepoDescriptor.isForceNonDuplicateChart());
    }

    @Test
    void applyIsMLRepoLayoutForHuggingFace() {
        FederatedRepoDescriptor federatedRepoDescriptor = new FederatedRepoDescriptor();
        Assertions.assertFalse(federatedRepoDescriptor.isMlRepoLayout());
        federatedRepoDescriptor.setMlRepoLayout(true);
        Assertions.assertTrue(federatedRepoDescriptor.isMlRepoLayout());
    }

    @Test
    void testXrayNotAvailableMode() {
        FederatedRepoDescriptor federatedRepoDescriptor = new FederatedRepoDescriptor();
        assertEquals(federatedRepoDescriptor.getXrayNotAvailableMode(), USE_SYSTEM_PROPERTY);
        federatedRepoDescriptor.setXrayNotAvailableMode(FAIL_CLOSE);
        assertEquals(federatedRepoDescriptor.getXrayNotAvailableMode(), FAIL_CLOSE);
    }

    @Test
    public void testGridTopologyIdPreservation_fromRepositoryConfigToDescriptor() {
        FederatedRepoConfig sourceRepoConfig = FederatedRepoConfig.create("test-federated-repo", RepoType.Generic);
        sourceRepoConfig.getBaseConfig().setGridTopologyId("fed-topology-abc-123");

        RepoLayout repoLayout = new RepoLayout();
        repoLayout.setName("simple-default");

        when(centralConfigDescriptor.getRepoLayouts()).thenReturn(List.of(repoLayout));
        when(centralConfigDescriptor.getPropertySets()).thenReturn(List.of());
        when(centralConfigDescriptor.getKeyPairs()).thenReturn(List.of());
        when(centralConfigDescriptor.getProxies()).thenReturn(List.of());

        FederatedRepoDescriptor descriptor = FederatedRepoDescriptor.fromRepositoryConfig(centralConfigDescriptor,
                sourceRepoConfig);

        assertEquals("fed-topology-abc-123", descriptor.getGridTopologyId());
    }

    @Test
    public void testGridTopologyIdPreservation_fromDescriptorToRepositoryConfig() {
        FederatedRepoDescriptor sourceDescriptor = new FederatedRepoDescriptor();
        sourceDescriptor.setKey("test-federated-repo");
        sourceDescriptor.setType(RepoType.Generic);
        sourceDescriptor.setGridTopologyId("fed-topology-xyz-789");

        FederatedRepoConfig repoConfig = sourceDescriptor.toRepositoryConfig();

        assertEquals("fed-topology-xyz-789", repoConfig.getBaseConfig().getFederationConfig().getGridTopologyId());
    }
}