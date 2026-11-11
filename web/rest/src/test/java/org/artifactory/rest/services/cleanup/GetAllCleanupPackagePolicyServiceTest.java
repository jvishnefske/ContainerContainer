package org.artifactory.rest.services.cleanup;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.cleanup.CleanupPolicyAddon;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.policy.PackagePolicySearchCriteria;
import org.artifactory.policy.PolicyItemType;
import org.artifactory.policy.cleanup.packages.PackageCleanupPolicy;
import org.artifactory.policy.cleanup.packages.PackageCleanupPolicyConfig;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.services.cleanup.mapper.PackageCleanupPolicyDtoMapper;
import org.artifactory.test.ArtifactoryHomeStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Oleksandr Legenkiy
 */
class GetAllCleanupPackagePolicyServiceTest {

    @BeforeEach
    void setUp() {
        ArtifactoryHomeStub home = new ArtifactoryHomeStub(getClass().getName());
        home.loadSystemProperties();
        ArtifactoryHome.bind(home);
    }

    @AfterEach
    void tearDown() {
        // Unbind ArtifactoryHome from the current thread
        ArtifactoryHome.unbind();
    }

    @Test
    void testExecute() {
        var policies = createPolicies();
        CleanupPolicyAddon addonMock = mock(CleanupPolicyAddon.class);
        when(addonMock.getPackagePolicies(null)).thenReturn(policies);
        AddonsManager addonsManagerMock = mock(AddonsManager.class);
        when(addonsManagerMock.addonByType(CleanupPolicyAddon.class)).thenReturn(addonMock);
        var response = new ArtifactoryRestResponse<>();
        var service = new GetAllCleanupPackagePolicyService(addonsManagerMock);
        service.execute(mock(ArtifactoryRestRequest.class), response);
        assertEquals(policies.stream().map(PackageCleanupPolicyDtoMapper::toDto).toList(), response.getIModel());
        assertEquals(HttpServletResponse.SC_OK, response.getResponseCode());
    }

    private List<PackageCleanupPolicy> createPolicies() {
        var policy1 = new PackageCleanupPolicy();
        policy1.setKey("key1");
        policy1.setItemType(PolicyItemType.PACKAGE);
        policy1.setConfig(PackageCleanupPolicyConfig.builder()
                .searchCriteria(PackagePolicySearchCriteria.builder().build()).build());
        var policy2 = new PackageCleanupPolicy();
        policy2.setKey("key2");
        policy2.setItemType(PolicyItemType.PACKAGE);
        policy2.setConfig(PackageCleanupPolicyConfig.builder()
                .searchCriteria(PackagePolicySearchCriteria.builder().build()).build());
        return List.of(policy1, policy2);
    }
}