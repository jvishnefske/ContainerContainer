package org.artifactory.repo.interceptor.config;

import org.artifactory.api.repo.RepositoryService;
import org.artifactory.config.ConfigurationException;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.descriptor.repo.RepoType.Maven;
import static org.artifactory.descriptor.repo.RepoType.Npm;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VirtualRepoConfigChangeInterceptorTest {

    @Mock
    private RepositoryService repositoryService;
    @InjectMocks
    private VirtualRepoConfigChangeInterceptor interceptor;

    @Test
    void testOnBeforeSave_CreateCompatibleRefs() {
        RepoConfigChanges changes = mock(RepoConfigChanges.class);
        RepoConfigChange change = mock(RepoConfigChange.class);

        VirtualRepoConfig virtualConfig = VirtualRepoConfig.create("maven-virtual", Maven);
        virtualConfig.getRepoTypeConfig().setRepositoryRefs(List.of("ref1", "ref2"));

        when(changes.filterBy(REPO_CREATE, VIRTUAL)).thenReturn(List.of(change));
        when(changes.filterBy(REPO_UPDATE, VIRTUAL)).thenReturn(List.of());
        when(change.getNewRepoConfig()).thenReturn(virtualConfig);
        when(change.isCreate()).thenReturn(true);

        RepoConfig ref1 = LocalRepoConfig.create("ref1", Maven);
        RepoConfig ref2 = LocalRepoConfig.create("ref2", Maven);
        when(repositoryService.getRepoConfigByKey("ref1")).thenReturn(ref1);
        when(repositoryService.getRepoConfigByKey("ref2")).thenReturn(ref2);

        assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
    }

    @Test
    void testOnBeforeSave_CreateCompatibleRefs_MavenGroup() {
        Set<RepoType> mavenGroupTypes = RepoType.getMavenGroupTypes();
        for (RepoType mavenGroupTypeVirtual : mavenGroupTypes) {
            RepoConfigChanges changes = mock(RepoConfigChanges.class);
            RepoConfigChange change = mock(RepoConfigChange.class);

            VirtualRepoConfig virtualConfig = VirtualRepoConfig.create("maven-virtual", mavenGroupTypeVirtual);
            virtualConfig.getRepoTypeConfig().setRepositoryRefs(List.of("ref1", "ref2"));

            when(changes.filterBy(REPO_CREATE, VIRTUAL)).thenReturn(List.of(change));
            when(changes.filterBy(REPO_UPDATE, VIRTUAL)).thenReturn(List.of());
            when(change.getNewRepoConfig()).thenReturn(virtualConfig);
            when(change.isCreate()).thenReturn(true);

            for (RepoType mavenGroupTypeRef : mavenGroupTypes) {
                RepoConfig ref1 = LocalRepoConfig.create("ref1", mavenGroupTypeRef);
                RepoConfig ref2 = LocalRepoConfig.create("ref2", mavenGroupTypeRef);
                when(repositoryService.getRepoConfigByKey("ref1")).thenReturn(ref1);
                when(repositoryService.getRepoConfigByKey("ref2")).thenReturn(ref2);

                assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
            }
        }
    }

    @Test
    void testOnBeforeSave_CreateCompatibleDeployRepo_MavenGroup() {
        Set<RepoType> mavenGroupTypes = RepoType.getMavenGroupTypes();
        for (RepoType mavenGroupTypeVirtual : mavenGroupTypes) {
            RepoConfigChanges changes = mock(RepoConfigChanges.class);
            RepoConfigChange change = mock(RepoConfigChange.class);

            VirtualRepoConfig virtualConfig = VirtualRepoConfig.create("maven-virtual", mavenGroupTypeVirtual);
            virtualConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("local1");

            when(changes.filterBy(REPO_CREATE, VIRTUAL)).thenReturn(List.of(change));
            when(changes.filterBy(REPO_UPDATE, VIRTUAL)).thenReturn(List.of());
            when(change.getNewRepoConfig()).thenReturn(virtualConfig);
            when(change.isCreate()).thenReturn(true);

            for (RepoType mavenGroupTypeDeployRepo : mavenGroupTypes) {
                LocalConfig localConfig = LocalRepoConfig.create("local1", mavenGroupTypeDeployRepo);
                when(repositoryService.getLocalOrFederatedRepoConfigByKey("local1")).thenReturn(localConfig);

                assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
            }
        }
    }

    @Test
    void testOnBeforeSave_CreateCompatibleRefs_HelmOciGroup() {
        Set<RepoType> ociGroupTypes = Set.of(RepoType.OCI, RepoType.HelmOCI);
        for (RepoType ociGroupTypeVirtual : ociGroupTypes) {
            RepoConfigChanges changes = mock(RepoConfigChanges.class);
            RepoConfigChange change = mock(RepoConfigChange.class);

            VirtualRepoConfig virtualConfig = VirtualRepoConfig.create("oci-virtual", ociGroupTypeVirtual);
            virtualConfig.getRepoTypeConfig().setRepositoryRefs(List.of("ref1", "ref2"));

            when(changes.filterBy(REPO_CREATE, VIRTUAL)).thenReturn(List.of(change));
            when(changes.filterBy(REPO_UPDATE, VIRTUAL)).thenReturn(List.of());
            when(change.getNewRepoConfig()).thenReturn(virtualConfig);
            when(change.isCreate()).thenReturn(true);

            for (RepoType ociGroupTypeRef : ociGroupTypes) {
                RepoConfig ref1 = LocalRepoConfig.create("ref1", ociGroupTypeRef);
                RepoConfig ref2 = LocalRepoConfig.create("ref2", ociGroupTypeRef);
                when(repositoryService.getRepoConfigByKey("ref1")).thenReturn(ref1);
                when(repositoryService.getRepoConfigByKey("ref2")).thenReturn(ref2);

                assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
            }
        }
    }

    @Test
    void testOnBeforeSave_CreateCompatibleDeployRepo_HelmOciGroup() {
        Set<RepoType> ociGroupTypes = Set.of(RepoType.OCI, RepoType.HelmOCI);
        for (RepoType ociGroupTypeVirtual : ociGroupTypes) {
            RepoConfigChanges changes = mock(RepoConfigChanges.class);
            RepoConfigChange change = mock(RepoConfigChange.class);

            VirtualRepoConfig virtualConfig = VirtualRepoConfig.create("oci-virtual", ociGroupTypeVirtual);
            virtualConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("local1");

            when(changes.filterBy(REPO_CREATE, VIRTUAL)).thenReturn(List.of(change));
            when(changes.filterBy(REPO_UPDATE, VIRTUAL)).thenReturn(List.of());
            when(change.getNewRepoConfig()).thenReturn(virtualConfig);
            when(change.isCreate()).thenReturn(true);

            for (RepoType ociGroupTypeDeployRepo : ociGroupTypes) {
                LocalConfig localConfig = LocalRepoConfig.create("local1", ociGroupTypeDeployRepo);
                when(repositoryService.getLocalOrFederatedRepoConfigByKey("local1")).thenReturn(localConfig);

                assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
            }
        }
    }

    @Test
    void testOnBeforeSave_CreateIncompatibleRefs() {
        RepoConfigChanges changes = mock(RepoConfigChanges.class);
        RepoConfigChange change = mock(RepoConfigChange.class);

        VirtualRepoConfig virtualConfig = VirtualRepoConfig.create("npm-virtual", Npm);
        virtualConfig.getRepoTypeConfig().setRepositoryRefs(List.of("ref1"));

        when(changes.filterBy(REPO_CREATE, VIRTUAL)).thenReturn(List.of(change));
        when(changes.filterBy(REPO_UPDATE, VIRTUAL)).thenReturn(List.of());
        when(change.getNewRepoConfig()).thenReturn(virtualConfig);
        when(change.isCreate()).thenReturn(true);

        RepoConfig ref1 = LocalRepoConfig.create("ref1", Maven);
        when(repositoryService.getRepoConfigByKey("ref1")).thenReturn(ref1);

        ConfigurationException ex = assertThrows(ConfigurationException.class, () -> interceptor.onBeforeSave(changes));
        assertTrue(ex.getMessage().contains("non-matching repository"));
    }

    @Test
    void testOnBeforeSave_CreateGenericRefs() {
        RepoConfigChanges changes = mock(RepoConfigChanges.class);
        RepoConfigChange change = mock(RepoConfigChange.class);

        VirtualRepoConfig virtualConfig = VirtualRepoConfig.create("generic-virtual", Generic);
        virtualConfig.getRepoTypeConfig().setRepositoryRefs(List.of("ref1"));

        when(changes.filterBy(REPO_CREATE, VIRTUAL)).thenReturn(List.of(change));
        when(changes.filterBy(REPO_UPDATE, VIRTUAL)).thenReturn(List.of());
        when(change.getNewRepoConfig()).thenReturn(virtualConfig);
        when(change.isCreate()).thenReturn(true);

        RepoConfig ref1 = LocalRepoConfig.create("ref1", Maven);
        when(repositoryService.getRepoConfigByKey("ref1")).thenReturn(ref1);

        assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
    }

    @Test
    void testOnBeforeSave_CreateNoRefs() {
        RepoConfigChanges changes = mock(RepoConfigChanges.class);
        RepoConfigChange change = mock(RepoConfigChange.class);

        VirtualRepoConfig virtualConfig = VirtualRepoConfig.create("empty-virtual", Maven);

        when(changes.filterBy(REPO_CREATE, VIRTUAL)).thenReturn(List.of(change));
        when(changes.filterBy(REPO_UPDATE, VIRTUAL)).thenReturn(List.of());
        when(change.getNewRepoConfig()).thenReturn(virtualConfig);
        when(change.isCreate()).thenReturn(true);

        assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
    }

    @Test
    void testOnBeforeSave_CreateNullRefs() {
        RepoConfigChanges changes = mock(RepoConfigChanges.class);
        RepoConfigChange change = mock(RepoConfigChange.class);

        VirtualRepoConfig virtualConfig = VirtualRepoConfig.create("null-refs-virtual", Maven);
        virtualConfig.getRepoTypeConfig().setRepositoryRefs(null);

        when(changes.filterBy(REPO_CREATE, VIRTUAL)).thenReturn(List.of(change));
        when(changes.filterBy(REPO_UPDATE, VIRTUAL)).thenReturn(List.of());
        when(change.getNewRepoConfig()).thenReturn(virtualConfig);
        when(change.isCreate()).thenReturn(true);

        assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
    }

    @Test
    void testOnBeforeSave_CreateNullRefRepoConfig() {
        RepoConfigChanges changes = mock(RepoConfigChanges.class);
        RepoConfigChange change = mock(RepoConfigChange.class);

        VirtualRepoConfig virtualConfig = VirtualRepoConfig.create("virtual", Maven);
        virtualConfig.getRepoTypeConfig().setRepositoryRefs(List.of("ref1"));

        when(changes.filterBy(REPO_CREATE, VIRTUAL)).thenReturn(List.of(change));
        when(changes.filterBy(REPO_UPDATE, VIRTUAL)).thenReturn(List.of());
        when(change.getNewRepoConfig()).thenReturn(virtualConfig);
        when(change.isCreate()).thenReturn(true);

        when(repositoryService.getRepoConfigByKey("ref1")).thenReturn(null);

        assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
    }

    @Test
    void testOnBeforeSave_CreateDefaultDeploymentRepoCompatible() {
        RepoConfigChanges changes = mock(RepoConfigChanges.class);
        RepoConfigChange change = mock(RepoConfigChange.class);

        VirtualRepoConfig virtualConfig = VirtualRepoConfig.create("virtual", Maven);
        virtualConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("local1");

        when(changes.filterBy(REPO_CREATE, VIRTUAL)).thenReturn(List.of(change));
        when(changes.filterBy(REPO_UPDATE, VIRTUAL)).thenReturn(List.of());
        when(change.getNewRepoConfig()).thenReturn(virtualConfig);
        when(change.isCreate()).thenReturn(true);

        LocalConfig localConfig = LocalRepoConfig.create("local1", Maven);
        when(repositoryService.getLocalOrFederatedRepoConfigByKey("local1")).thenReturn(localConfig);

        assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
    }

    @Test
    void testOnBeforeSave_CreateDefaultDeploymentRepoIncompatible() {
        RepoConfigChanges changes = mock(RepoConfigChanges.class);
        RepoConfigChange change = mock(RepoConfigChange.class);

        VirtualRepoConfig virtualConfig = VirtualRepoConfig.create("virtual", Npm);
        virtualConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("local1");

        when(changes.filterBy(REPO_CREATE, VIRTUAL)).thenReturn(List.of(change));
        when(changes.filterBy(REPO_UPDATE, VIRTUAL)).thenReturn(List.of());
        when(change.getNewRepoConfig()).thenReturn(virtualConfig);
        when(change.isCreate()).thenReturn(true);

        LocalConfig localConfig = LocalRepoConfig.create("local1", Maven);
        when(repositoryService.getLocalOrFederatedRepoConfigByKey("local1")).thenReturn(localConfig);

        ConfigurationException ex = assertThrows(ConfigurationException.class, () -> interceptor.onBeforeSave(changes));
        assertTrue(ex.getMessage().contains("non-matching repository"));
    }

    @Test
    void testOnBeforeSave_CreateDefaultDeploymentRepoBlank() {
        RepoConfigChanges changes = mock(RepoConfigChanges.class);
        RepoConfigChange change = mock(RepoConfigChange.class);

        VirtualRepoConfig virtualConfig = VirtualRepoConfig.create("virtual", Maven);
        virtualConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("   ");

        when(changes.filterBy(REPO_CREATE, VIRTUAL)).thenReturn(List.of(change));
        when(changes.filterBy(REPO_UPDATE, VIRTUAL)).thenReturn(List.of());
        when(change.getNewRepoConfig()).thenReturn(virtualConfig);
        when(change.isCreate()).thenReturn(true);

        assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
        verify(repositoryService, never()).getLocalOrFederatedRepoConfigByKey(any());
    }

    @Test
    void testOnBeforeSave_CreateDefaultDeploymentRepoNullConfig() {
        RepoConfigChanges changes = mock(RepoConfigChanges.class);
        RepoConfigChange change = mock(RepoConfigChange.class);

        VirtualRepoConfig virtualConfig = VirtualRepoConfig.create("virtual", Maven);
        virtualConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("local1");

        when(changes.filterBy(REPO_CREATE, VIRTUAL)).thenReturn(List.of(change));
        when(changes.filterBy(REPO_UPDATE, VIRTUAL)).thenReturn(List.of());
        when(change.getNewRepoConfig()).thenReturn(virtualConfig);
        when(change.isCreate()).thenReturn(true);

        when(repositoryService.getLocalOrFederatedRepoConfigByKey("local1")).thenReturn(null);

        assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
    }

    @Test
    void testOnBeforeSave_UpdateRefsUnchanged() {
        RepoConfigChanges changes = mock(RepoConfigChanges.class);
        RepoConfigChange change = mock(RepoConfigChange.class);

        VirtualRepoConfig oldConfig = VirtualRepoConfig.create("virtual", Maven);
        oldConfig.getRepoTypeConfig().setRepositoryRefs(List.of("ref1"));

        VirtualRepoConfig newConfig = VirtualRepoConfig.create("virtual", Maven);
        newConfig.getRepoTypeConfig().setRepositoryRefs(List.of("ref1"));

        when(changes.filterBy(REPO_CREATE, VIRTUAL)).thenReturn(List.of());
        when(changes.filterBy(REPO_UPDATE, VIRTUAL)).thenReturn(List.of(change));
        when(change.getNewRepoConfig()).thenReturn(newConfig);
        when(change.getOldRepoConfig()).thenReturn(oldConfig);
        when(change.isUpdate()).thenReturn(true);

        assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
    }

    @Test
    void testOnBeforeSave_UpdateRefsChanged() {
        RepoConfigChanges changes = mock(RepoConfigChanges.class);
        RepoConfigChange change = mock(RepoConfigChange.class);

        VirtualRepoConfig oldConfig = VirtualRepoConfig.create("virtual", Maven);
        oldConfig.getRepoTypeConfig().setRepositoryRefs(List.of("ref1"));

        VirtualRepoConfig newConfig = VirtualRepoConfig.create("virtual", Maven);
        newConfig.getRepoTypeConfig().setRepositoryRefs(List.of("ref2"));

        when(changes.filterBy(REPO_CREATE, VIRTUAL)).thenReturn(List.of());
        when(changes.filterBy(REPO_UPDATE, VIRTUAL)).thenReturn(List.of(change));
        when(change.getNewRepoConfig()).thenReturn(newConfig);
        when(change.getOldRepoConfig()).thenReturn(oldConfig);
        when(change.isUpdate()).thenReturn(true);

        RepoConfig ref2 = LocalRepoConfig.create("ref2", Maven);
        when(repositoryService.getRepoConfigByKey("ref2")).thenReturn(ref2);

        assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
    }

    @Test
    void testOnBeforeSave_UpdateDefaultDeploymentRepoUnchanged() {
        RepoConfigChanges changes = mock(RepoConfigChanges.class);
        RepoConfigChange change = mock(RepoConfigChange.class);

        VirtualRepoConfig oldConfig = VirtualRepoConfig.create("virtual", Maven);
        oldConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("local1");

        VirtualRepoConfig newConfig = VirtualRepoConfig.create("virtual", Maven);
        newConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("local1");

        when(changes.filterBy(REPO_CREATE, VIRTUAL)).thenReturn(List.of());
        when(changes.filterBy(REPO_UPDATE, VIRTUAL)).thenReturn(List.of(change));
        when(change.getNewRepoConfig()).thenReturn(newConfig);
        when(change.getOldRepoConfig()).thenReturn(oldConfig);
        when(change.isUpdate()).thenReturn(true);

        assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
    }

    @Test
    void testOnBeforeSave_UpdateDefaultDeploymentRepoChangedCompatible() {
        RepoConfigChanges changes = mock(RepoConfigChanges.class);
        RepoConfigChange change = mock(RepoConfigChange.class);

        VirtualRepoConfig oldConfig = VirtualRepoConfig.create("virtual", Maven);
        oldConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("local1");

        VirtualRepoConfig newConfig = VirtualRepoConfig.create("virtual", Maven);
        newConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("local2");

        when(changes.filterBy(REPO_CREATE, VIRTUAL)).thenReturn(List.of());
        when(changes.filterBy(REPO_UPDATE, VIRTUAL)).thenReturn(List.of(change));
        when(change.getNewRepoConfig()).thenReturn(newConfig);
        when(change.getOldRepoConfig()).thenReturn(oldConfig);
        when(change.isUpdate()).thenReturn(true);

        LocalConfig local2 = LocalRepoConfig.create("local2", Maven);
        when(repositoryService.getLocalOrFederatedRepoConfigByKey("local2")).thenReturn(local2);

        assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
    }

    @Test
    void testOnBeforeSave_UpdateDefaultDeploymentRepoChangedIncompatible() {
        RepoConfigChanges changes = mock(RepoConfigChanges.class);
        RepoConfigChange change = mock(RepoConfigChange.class);

        VirtualRepoConfig oldConfig = VirtualRepoConfig.create("virtual", Maven);
        oldConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("local1");

        VirtualRepoConfig newConfig = VirtualRepoConfig.create("virtual", Maven);
        newConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("local2");

        when(changes.filterBy(REPO_CREATE, VIRTUAL)).thenReturn(List.of());
        when(changes.filterBy(REPO_UPDATE, VIRTUAL)).thenReturn(List.of(change));
        when(change.getNewRepoConfig()).thenReturn(newConfig);
        when(change.getOldRepoConfig()).thenReturn(oldConfig);
        when(change.isUpdate()).thenReturn(true);

        LocalConfig local2 = LocalRepoConfig.create("local2", Npm);
        when(repositoryService.getLocalOrFederatedRepoConfigByKey("local2")).thenReturn(local2);

        ConfigurationException ex = assertThrows(ConfigurationException.class, () -> interceptor.onBeforeSave(changes));
        assertTrue(ex.getMessage().contains("non-matching repository"));
    }

    @Test
    void testOnBeforeSave_NoVirtualRepoChanges() {
        RepoConfigChanges changes = mock(RepoConfigChanges.class);
        when(changes.filterBy(REPO_CREATE, VIRTUAL)).thenReturn(List.of());
        when(changes.filterBy(REPO_UPDATE, VIRTUAL)).thenReturn(List.of());
        assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
    }

    @Test
    void testOnBeforeSave_NewRepoConfigIsNull() {
        RepoConfigChanges changes = mock(RepoConfigChanges.class);
        RepoConfigChange change = mock(RepoConfigChange.class);
        when(changes.filterBy(REPO_CREATE, VIRTUAL)).thenReturn(List.of(change));
        when(changes.filterBy(REPO_UPDATE, VIRTUAL)).thenReturn(List.of());
        when(change.getNewRepoConfig()).thenReturn(null);
        when(change.isCreate()).thenReturn(true);
        assertThrows(NullPointerException.class, () -> interceptor.onBeforeSave(changes));
    }
}