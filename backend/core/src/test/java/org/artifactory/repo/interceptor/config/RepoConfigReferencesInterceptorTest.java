package org.artifactory.repo.interceptor.config;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.config.ConfigurationException;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.federated.FederatedMember;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tamar Rosen
 */
public class RepoConfigReferencesInterceptorTest {
    private RepoConfigReferencesInterceptor interceptor;
    private CentralConfigService configService;
    private RepositoryService repositoryService;

    @BeforeMethod
    public void setup() {
        configService = mock(CentralConfigService.class);
        repositoryService = mock(RepositoryService.class);
        when(configService.isContextCreated()).thenReturn(true);
        when(configService.getDescriptor()).thenReturn(new CentralConfigDescriptorImpl());
        interceptor = new RepoConfigReferencesInterceptor(configService, repositoryService);
    }


    @Test(expectedExceptions = ConfigurationException.class)
    public void testNewVirtualRepoRepositoryRefsDoesNotExist() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Generic);
        virtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("does-not-exist"));
        interceptor.onBeforeSave(
                Map.of(VIRTUAL, List.of(new RepoConfigChange("ver-repo", REPO_CREATE, virtualRepoConfig, null))));
    }

    @Test
    public void testNewFederatedVirtualRepoRepositoryRefsDoesNotExist() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Generic);
        virtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("does-not-exist"));
        FederatedMember member = new FederatedMember();
        member.setUrlOrTemplate("http://site/artifactory/repoKey");
        virtualRepoConfig.getBaseConfig().getFederationConfig().getMembers().add(member);

        interceptor.onBeforeSave(
                Map.of(VIRTUAL, List.of(new RepoConfigChange("ver-repo", REPO_CREATE, virtualRepoConfig, null))));

        assertTrue(virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs().contains("does-not-exist"));
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void testUpdatedVirtualRepoNewRepositoryRefsDoesNotExist() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Generic);
        virtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("does-not-exist"));
        VirtualRepoConfig oldVirtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Generic);
        oldVirtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("old-ref"));
        interceptor.onBeforeSave(
                Map.of(VIRTUAL, List.of(new RepoConfigChange("ver-repo", REPO_UPDATE, virtualRepoConfig,
                        oldVirtualRepoConfig))));
    }

    @Test
    public void testUpdatedVirtualRepoOldRepositoryRefsDoesNotExist() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Generic);
        virtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("does-not-exist"));
        VirtualRepoConfig oldVirtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Generic);
        oldVirtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("does-not-exist"));
        interceptor.onBeforeSave(
                Map.of(VIRTUAL, List.of(new RepoConfigChange("ver-repo", REPO_UPDATE, virtualRepoConfig,
                        oldVirtualRepoConfig))));
        assertTrue(virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs().isEmpty());
    }

    @Test
    public void testNewVirtualRepoRepositoryRefCreatedInSameSave() {
        LocalRepoConfig childRepoConfig = LocalRepoConfig.create("child", RepoType.Generic);
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virt", RepoType.Generic);
        virtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("child"));

        interceptor.onBeforeSave(Map.of(
                LOCAL, List.of(new RepoConfigChange("child", REPO_CREATE, childRepoConfig, null)),
                VIRTUAL, List.of(new RepoConfigChange("virt", REPO_CREATE, virtualRepoConfig, null))
        ));
    }

    @Test
    public void testNewFederatedVirtualRepoRepositoryRefCreatedInSameSave() {
        LocalRepoConfig childRepoConfig = LocalRepoConfig.create("child", RepoType.Generic);
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virt", RepoType.Generic);
        virtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("child"));
        FederatedMember member = new FederatedMember();
        member.setUrlOrTemplate("http://site/artifactory/virt");
        virtualRepoConfig.getBaseConfig().getFederationConfig().getMembers().add(member);

        interceptor.onBeforeSave(Map.of(
                LOCAL, List.of(new RepoConfigChange("child", REPO_CREATE, childRepoConfig, null)),
                VIRTUAL, List.of(new RepoConfigChange("virt", REPO_CREATE, virtualRepoConfig, null))
        ));

        verify(repositoryService, never()).existsByRepoKey("child");
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void testUpdatedVirtualRepoNewRepositoryRefsDoesNotExistWhenOldRepositoryRefsNull() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Generic);
        virtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("does-not-exist"));
        VirtualRepoConfig oldVirtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Generic);
        oldVirtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(null);
        when(repositoryService.existsByRepoKey("does-not-exist")).thenReturn(false);

        interceptor.onBeforeSave(
                Map.of(VIRTUAL, List.of(new RepoConfigChange("ver-repo", REPO_UPDATE, virtualRepoConfig,
                        oldVirtualRepoConfig))));
    }


    @Test(expectedExceptions = ConfigurationException.class)
    public void testNewVirtualRepoDefaultDeploymentRepoRefDoesNotExist() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Generic);
        virtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("does-not-exist");
        interceptor.onBeforeSave(
                Map.of(VIRTUAL, List.of(new RepoConfigChange("ver-repo", REPO_CREATE, virtualRepoConfig, null))));
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void testUpdatedVirtualRepoNewDefaultDeploymentRepoRefDoesNotExist() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Generic);
        virtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("does-not-exist");
        VirtualRepoConfig oldVirtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Generic);
        oldVirtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("old-ref");
        interceptor.onBeforeSave(
                Map.of(VIRTUAL, List.of(new RepoConfigChange("ver-repo", REPO_UPDATE, virtualRepoConfig,
                        oldVirtualRepoConfig))));
    }

    @Test
    public void testUpdatedVirtualRepoOldDefaultDeploymentRepoRefDoesNotExist() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Generic);
        virtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("does-not-exist");
        VirtualRepoConfig oldVirtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Generic);
        oldVirtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("does-not-exist");
        interceptor.onBeforeSave(
                Map.of(VIRTUAL, List.of(new RepoConfigChange("ver-repo", REPO_UPDATE, virtualRepoConfig,
                        oldVirtualRepoConfig))));
        assertNull(virtualRepoConfig.getRepoTypeConfig().getDefaultDeploymentRepoRef());
    }

    @Test
    public void testNewVirtualRepoDefaultDeploymentRepoRefCreatedInSameSave() {
        LocalRepoConfig childRepoConfig = LocalRepoConfig.create("child", RepoType.Generic);
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virt", RepoType.Generic);
        virtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("child");

        interceptor.onBeforeSave(Map.of(
                LOCAL, List.of(new RepoConfigChange("child", REPO_CREATE, childRepoConfig, null)),
                VIRTUAL, List.of(new RepoConfigChange("virt", REPO_CREATE, virtualRepoConfig, null))
        ));
    }


    @Test(expectedExceptions = ConfigurationException.class)
    public void testNewRepoRemoteProxyRefDoesNotExist() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setProxyRef("does-not-exist");
        interceptor.onBeforeSave(
                Map.of(REMOTE, List.of(new RepoConfigChange("remote-repo", REPO_CREATE, remoteRepoConfig, null))));
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void testUpdatedRepoNewRemoteProxyRefDoesNotExist() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setProxyRef("does-not-exist");
        RemoteRepoConfig oldRemoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        oldRemoteRepoConfig.getRepoTypeConfig().setProxyRef("old-proxy");

        interceptor.onBeforeSave(
                Map.of(REMOTE, List.of(new RepoConfigChange("remote-repo", REPO_UPDATE, remoteRepoConfig,
                        oldRemoteRepoConfig))));
    }

    @Test
    public void testUpdatedRepoOldRemoteProxyRefDoesNotExist() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setProxyRef("does-not-exist");
        RemoteRepoConfig oldRemoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        oldRemoteRepoConfig.getRepoTypeConfig().setProxyRef("does-not-exist");

        interceptor.onBeforeSave(
                Map.of(REMOTE, List.of(new RepoConfigChange("remote-repo", REPO_UPDATE, remoteRepoConfig,
                        oldRemoteRepoConfig))));
        assertNull(remoteRepoConfig.getRepoTypeConfig().getProxyRef());
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void testFederatedProxyRefDoesNotExist() {
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("repoKey", RepoType.Generic);
        federatedRepoConfig.getRepoTypeConfig().setProxyRef("does-not-exist");
        interceptor.onBeforeSave(
                Map.of(FEDERATED, List.of(new RepoConfigChange("fed-repo", REPO_CREATE, federatedRepoConfig, null))));
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void testConvertedRepoFederatedProxyRefDoesNotExist() {
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("repoKey", RepoType.Generic);
        federatedRepoConfig.getRepoTypeConfig().setProxyRef("does-not-exist");
        LocalRepoConfig oldLocalRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        interceptor.onBeforeSave(
                Map.of(FEDERATED, List.of(new RepoConfigChange("fed-repo", REPO_CREATE, federatedRepoConfig,
                        oldLocalRepoConfig))));
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void testNewRepoBaseRepoLayoutRefDoesNotExist() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        localRepoConfig.getBaseConfig().setRepoLayoutRef("does-not-exist");
        interceptor.onBeforeSave(
                Map.of(LOCAL, List.of(new RepoConfigChange("local-repo", REPO_CREATE, localRepoConfig, null))));
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void testUpdatedRepoNewBaseRepoLayoutRefDoesNotExist() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        localRepoConfig.getBaseConfig().setRepoLayoutRef("does-not-exist");
        LocalRepoConfig oldLocalRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        oldLocalRepoConfig.getBaseConfig().setRepoLayoutRef("old-repo-layout");

        interceptor.onBeforeSave(
                Map.of(LOCAL,
                        List.of(new RepoConfigChange("local-repo", REPO_UPDATE, localRepoConfig, oldLocalRepoConfig))));
    }

    @Test
    public void testUpdatedRepoOldBaseRepoLayoutRefDoesNotExist() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        localRepoConfig.getBaseConfig().setRepoLayoutRef("does-not-exist");
        LocalRepoConfig oldLocalRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        oldLocalRepoConfig.getBaseConfig().setRepoLayoutRef("does-not-exist");

        interceptor.onBeforeSave(
                Map.of(LOCAL,
                        List.of(new RepoConfigChange("local-repo", REPO_UPDATE, localRepoConfig, oldLocalRepoConfig))));
        assertNull(localRepoConfig.getBaseConfig().getRepoLayoutRef());
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void testRemoteRepoLayoutRefDoesNotExist() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setRemoteRepoLayoutRef("does-not-exist");
        interceptor.onBeforeSave(
                Map.of(REMOTE, List.of(new RepoConfigChange("remote-repo", REPO_CREATE, remoteRepoConfig, null))));
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void testNewRepoPropertySetRefDoesNotExist() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        localRepoConfig.getRepoTypeConfig().setPropertySetRefs(List.of("does-not-exist"));
        interceptor.onBeforeSave(
                Map.of(LOCAL, List.of(new RepoConfigChange("local-repo", REPO_CREATE, localRepoConfig, null))));
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void testUpdatedRepoNewPropertySetRefDoesNotExist() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        localRepoConfig.getRepoTypeConfig().setPropertySetRefs(List.of("does-not-exist"));
        LocalRepoConfig oldLocalRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        oldLocalRepoConfig.getRepoTypeConfig().setPropertySetRefs(List.of("old-property-set"));

        interceptor.onBeforeSave(
                Map.of(LOCAL,
                        List.of(new RepoConfigChange("local-repo", REPO_UPDATE, localRepoConfig, oldLocalRepoConfig))));
    }

    @Test
    public void testUpdatedRepoOldPropertySetRefDoesNotExist() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        localRepoConfig.getRepoTypeConfig().setPropertySetRefs(new ArrayList<>(List.of("does-not-exist")));
        LocalRepoConfig oldLocalRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        oldLocalRepoConfig.getRepoTypeConfig().setPropertySetRefs(new ArrayList<>(List.of("does-not-exist")));

        interceptor.onBeforeSave(
                Map.of(LOCAL,
                        List.of(new RepoConfigChange("local-repo", REPO_UPDATE, localRepoConfig, oldLocalRepoConfig))));
        assertTrue(localRepoConfig.getRepoTypeConfig().getPropertySetRefs().isEmpty());
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void testNewRepoPrimaryKeyPairRefDoesNotExist() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        localRepoConfig.getSecurityConfig().setPrimaryKeyPairRef("does-not-exist");
        interceptor.onBeforeSave(
                Map.of(LOCAL, List.of(new RepoConfigChange("local-repo", REPO_CREATE, localRepoConfig, null))));
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void testUpdatedRepoNewPrimaryKeyPairRefDoesNotExist() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        localRepoConfig.getSecurityConfig().setPrimaryKeyPairRef("does-not-exist");
        LocalRepoConfig oldLocalRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        oldLocalRepoConfig.getSecurityConfig().setPrimaryKeyPairRef("old-key-pair");

        interceptor.onBeforeSave(
                Map.of(LOCAL,
                        List.of(new RepoConfigChange("local-repo", REPO_UPDATE, localRepoConfig, oldLocalRepoConfig))));
    }

    @Test
    public void testUpdatedRepoOldPrimaryKeyPairRefDoesNotExist() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        localRepoConfig.getSecurityConfig().setPrimaryKeyPairRef("does-not-exist");
        LocalRepoConfig oldLocalRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        oldLocalRepoConfig.getSecurityConfig().setPrimaryKeyPairRef("does-not-exist");

        interceptor.onBeforeSave(
                Map.of(LOCAL,
                        List.of(new RepoConfigChange("local-repo", REPO_UPDATE, localRepoConfig, oldLocalRepoConfig))));
        assertNull(localRepoConfig.getSecurityConfig().getPrimaryKeyPairRef());
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void testSecondaryKeyPairRefDoesNotExist() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        localRepoConfig.getSecurityConfig().setSecondaryKeyPairRef("does-not-exist");
        interceptor.onBeforeSave(
                Map.of(LOCAL, List.of(new RepoConfigChange("local-repo", REPO_CREATE, localRepoConfig, null))));
    }

    @Test
    public void testRemoteProxyRefExists() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setProxyRef("exists");
        mockProxyInDescriptor();
        interceptor.onBeforeSave(
                Map.of(REMOTE, List.of(new RepoConfigChange("remote-repo", REPO_CREATE, remoteRepoConfig, null))));
    }

    @Test
    public void testVirtualRepoRepositoryRefsExist() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Generic);
        virtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("does-exist"));
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("does-exist", RepoType.Generic);
        interceptor.onBeforeSave(
                Map.of(VIRTUAL, List.of(new RepoConfigChange("ver-repo", REPO_CREATE, virtualRepoConfig, null)),
                        LOCAL, List.of(new RepoConfigChange("does-exist", REPO_CREATE, localRepoConfig, null))));
    }

    @Test
    public void testFederatedProxyRefExists() {
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("repoKey", RepoType.Generic);
        federatedRepoConfig.getRepoTypeConfig().setProxyRef("exists");
        mockProxyInDescriptor();
        interceptor.onBeforeSave(
                Map.of(FEDERATED, List.of(new RepoConfigChange("fed-repo", REPO_CREATE, federatedRepoConfig, null))));
    }

    @Test
    public void testBaseRepoLayoutRefExists() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        localRepoConfig.getBaseConfig().setRepoLayoutRef("exists");
        mockRepoLayoutInDescriptor();
        interceptor.onBeforeSave(
                Map.of(LOCAL, List.of(new RepoConfigChange("local-repo", REPO_CREATE, localRepoConfig, null))));
    }

    @Test
    public void testRemoteRepoLayoutRefExists() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setRemoteRepoLayoutRef("exists");
        mockRepoLayoutInDescriptor();
        interceptor.onBeforeSave(
                Map.of(REMOTE, List.of(new RepoConfigChange("remote-repo", REPO_CREATE, remoteRepoConfig, null))));
    }

    @Test
    public void testPropertySetRefExists() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        localRepoConfig.getRepoTypeConfig().setPropertySetRefs(new ArrayList<>(List.of("exists")));
        mockPropertySetInDescriptor();
        interceptor.onBeforeSave(
                Map.of(LOCAL, List.of(new RepoConfigChange("local-repo", REPO_CREATE, localRepoConfig, null))));
    }

    @Test
    public void testPrimaryKeyPairRefExists() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        localRepoConfig.getSecurityConfig().setPrimaryKeyPairRef("exists");
        mockKeyPairInDescriptor();
        interceptor.onBeforeSave(
                Map.of(LOCAL, List.of(new RepoConfigChange("local-repo", REPO_CREATE, localRepoConfig, null))));
    }

    @Test
    public void testSecondaryKeyPairRefExists() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        localRepoConfig.getSecurityConfig().setSecondaryKeyPairRef("exists");
        mockKeyPairInDescriptor();
        interceptor.onBeforeSave(
                Map.of(LOCAL, List.of(new RepoConfigChange("local-repo", REPO_CREATE, localRepoConfig, null))));
    }

    private void mockProxyInDescriptor() {
        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        ProxyDescriptor proxyDescriptor = new ProxyDescriptor();
        proxyDescriptor.setKey("exists");
        configDescriptor.addProxy(proxyDescriptor, false);
        when(configService.getDescriptor()).thenReturn(configDescriptor);
    }

    private void mockRepoLayoutInDescriptor() {
        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        RepoLayout repoLayout = new RepoLayout();
        repoLayout.setName("exists");
        configDescriptor.addRepoLayout(repoLayout);
        when(configService.getDescriptor()).thenReturn(configDescriptor);
    }

    private void mockPropertySetInDescriptor() {
        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        PropertySet propertySet = new PropertySet();
        propertySet.setName("exists");
        configDescriptor.addPropertySet(propertySet);
        when(configService.getDescriptor()).thenReturn(configDescriptor);
    }

    private void mockKeyPairInDescriptor() {
        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        KeyPairDescriptor keyPair = new KeyPairDescriptor();
        keyPair.setKey("exists");
        configDescriptor.addKeyPair(keyPair);
        when(configService.getDescriptor()).thenReturn(configDescriptor);
    }

    @Test
    public void testValidateIsNullSafe() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual-repo", RepoType.Generic);
        virtualRepoConfig.getBaseConfig().setRepoLayoutRef(null);
        virtualRepoConfig.getSecurityConfig().setPrimaryKeyPairRef(null);
        virtualRepoConfig.getSecurityConfig().setSecondaryKeyPairRef(null);
        virtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef(null);
        virtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(null);

        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo", RepoType.Generic);
        localRepoConfig.getRepoTypeConfig().setPropertySetRefs(null);

        interceptor.onBeforeSave(
                Map.of(VIRTUAL, List.of(new RepoConfigChange("virtual-repo", REPO_UPDATE, virtualRepoConfig, null)),
                        LOCAL, List.of(new RepoConfigChange("local-repo", REPO_CREATE, localRepoConfig, null))));
    }
}