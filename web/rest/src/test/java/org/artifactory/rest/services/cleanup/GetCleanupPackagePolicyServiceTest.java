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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Oleksandr Legenkiy
 */
class GetCleanupPackagePolicyServiceTest {

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
        var policy = new PackageCleanupPolicy();
        policy.setKey("key");
        policy.setItemType(PolicyItemType.PACKAGE);
        policy.setConfig(PackageCleanupPolicyConfig.builder()
                .searchCriteria(PackagePolicySearchCriteria.builder().build()).build());
        CleanupPolicyAddon addonMock = mock(CleanupPolicyAddon.class);
        when(addonMock.getPackagePolicy("key")).thenReturn(policy);
        AddonsManager addonsManagerMock = mock(AddonsManager.class);
        when(addonsManagerMock.addonByType(CleanupPolicyAddon.class)).thenReturn(addonMock);

        var requestMock = (ArtifactoryRestRequest<String>) mock(ArtifactoryRestRequest.class);
        when(requestMock.getImodel()).thenReturn("key");

        var response = new ArtifactoryRestResponse<>();
        var service = new GetCleanupPackagePolicyService(addonsManagerMock);
        service.execute(requestMock, response);
        assertEquals(PackageCleanupPolicyDtoMapper.toDto(policy), response.getIModel());
        assertEquals(HttpServletResponse.SC_OK, response.getResponseCode());
    }
}