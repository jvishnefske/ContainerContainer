package org.artifactory.repo;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class VirtualRepositoryConfigurationImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Test
    void testSetFederationConfig_shouldSetMembers_whenFederationMembersExist() {
        homeStub.setProperty(ConstantValues.virtualRepoFederationEnabled, "true");
        ArtifactoryContext mockContext = mock(ArtifactoryContext.class);
        CentralConfigService mockConfigService = mock(CentralConfigService.class);
        CentralConfigDescriptor mockDescriptor = mock(CentralConfigDescriptor.class);
        when(mockContext.getCentralConfig()).thenReturn(mockConfigService);
        when(mockConfigService.getDescriptor()).thenReturn(mockDescriptor);
        when(mockDescriptor.getFederatedRepoUrlBaseOrDefault()).thenReturn("http://localhost:8080/artifactory");

        try (MockedStatic<ContextHelper> contextHelperMock = mockStatic(ContextHelper.class)) {
            contextHelperMock.when(ContextHelper::get).thenReturn(mockContext);

            VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Generic);

            FederatedMember member = new FederatedMember();
            member.setUrlOrTemplate("http://federated-server/repo");
            member.setMode("READ_WRITE");
            member.setEnabled(true);
            virtualRepoConfig.getBaseConfig().getFederationConfig().setMembers(List.of(member));

            VirtualRepositoryConfigurationImpl virtualConfig =
                    new VirtualRepositoryConfigurationImpl(virtualRepoConfig);

            List<FederatedMemberConfiguration> members = virtualConfig.getMembers();
            assertEquals(1, members.size());
            assertEquals("READ_WRITE", members.get(0).getMode());
            assertTrue(members.get(0).getEnabled());
        }
    }

    @Test
    void testSetFederationConfig_shouldNotSetMembers_whenFederationMembersEmpty() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Generic);

        VirtualRepositoryConfigurationImpl virtualConfig =
                new VirtualRepositoryConfigurationImpl(virtualRepoConfig);

        assertNull(virtualConfig.getMembers());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void constructor_federationOnGrid_reflectsValueSetInFederationConfig(boolean federationOnGrid) {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Generic);
        virtualRepoConfig.getBaseConfig().getFederationConfig().setFederationOnGrid(federationOnGrid);

        VirtualRepositoryConfigurationImpl config = new VirtualRepositoryConfigurationImpl(virtualRepoConfig);

        assertEquals(federationOnGrid, config.isFederationOnGrid());
    }

    @Test
    void testSetFederationConfig_shouldNotSetMembers_whenVirtualFederationFeatureDisabled() {
        homeStub.setProperty(ConstantValues.virtualRepoFederationEnabled, "false");
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Generic);

        FederatedMember member = new FederatedMember();
        member.setUrlOrTemplate("http://federated-server/repo");
        member.setMode("READ_WRITE");
        member.setEnabled(true);
        virtualRepoConfig.getBaseConfig().getFederationConfig().setMembers(List.of(member));

        VirtualRepositoryConfigurationImpl virtualConfig =
                new VirtualRepositoryConfigurationImpl(virtualRepoConfig);

        assertNull(virtualConfig.getMembers());
    }
}
