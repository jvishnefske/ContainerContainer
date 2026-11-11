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
import org.artifactory.rest.resource.cleanup.dto.PackageCleanupPolicyDto;
import org.artifactory.rest.resource.cleanup.dto.PackageCleanupPolicySearchCriteriaDto;
import org.artifactory.rest.services.cleanup.mapper.PackageCleanupPolicyDtoMapper;
import org.artifactory.test.ArtifactoryHomeStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Oleksandr Legenkiy
 */
class CreateCleanupPackagePolicyServiceTest {

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
        var policyToCreate = PackageCleanupPolicyDto.builder().key("key1")
                .searchCriteria(PackageCleanupPolicySearchCriteriaDto.builder().build()).projectKey("default")
                .durationInMinutes(2).build();
        var createdPolicy = new PackageCleanupPolicy();
        createdPolicy.setKey("key2"); // need different keys to assert that the correct instance is returned
        createdPolicy.setItemType(PolicyItemType.PACKAGE);
        createdPolicy.setConfig(
                PackageCleanupPolicyConfig.builder()
                        .searchCriteria(PackagePolicySearchCriteria.builder().build())
                        .build());
        CleanupPolicyAddon addonMock = mock(CleanupPolicyAddon.class);
        when(addonMock.addPackagePolicy(any(), eq("default")))
                .thenReturn(createdPolicy);
        AddonsManager addonsManagerMock = mock(AddonsManager.class);
        when(addonsManagerMock.addonByType(CleanupPolicyAddon.class)).thenReturn(addonMock);

        var requestMock = (ArtifactoryRestRequest<PackageCleanupPolicyDto>) mock(ArtifactoryRestRequest.class);
        when(requestMock.getImodel()).thenReturn(policyToCreate);

        var response = new ArtifactoryRestResponse<>();
        CreateCleanupPackagePolicyService service = new CreateCleanupPackagePolicyService(addonsManagerMock);
        service.execute(requestMock, response);
        assertEquals(PackageCleanupPolicyDtoMapper.toDto(createdPolicy), response.getIModel());
        assertEquals(HttpServletResponse.SC_CREATED, response.getResponseCode());
    }
}