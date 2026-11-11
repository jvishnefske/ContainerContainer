package org.artifactory.repo.interceptor.config;

import org.artifactory.addon.AddonsManager;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.config.ConfigurationException;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.artifactory.descriptor.property.PropertySet.ARTIFACTORY_RESERVED_PROP_SET;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_DELETE;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.system.SystemRepos.EDGE_UPLOADS_REPO_KEY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

/**
 * @author Tamar Rosen
 */
public class EdgeUploadReposConfigInterceptorTest {

    private AddonsManager addonsManager;
    private CentralConfigService configService;
    private EdgeUploadReposConfigInterceptor interceptor;
    private RepositoryService repositoryService;

    @BeforeMethod
    public void setup() {
        addonsManager = mock(AddonsManager.class);
        configService = mock(CentralConfigService.class);
        repositoryService = mock(RepositoryService.class);
        interceptor = new EdgeUploadReposConfigInterceptor(addonsManager, configService);
        interceptor.setRepositoryService(repositoryService);
    }

    @Test
    public void testCreateEdgeUploadRepo() {
        // given - entitled + repo does not exist
        when(addonsManager.isEntitled(EntitlementType.EDGE)).thenReturn(true);
        when(repositoryService.existsByRepoKey(EDGE_UPLOADS_REPO_KEY)).thenReturn(false);
        CentralConfigDescriptor configDescriptor = mock(CentralConfigDescriptor.class);
        PropertySet artifactoryDefaultPropertySet = new PropertySet();
        artifactoryDefaultPropertySet.setName(ARTIFACTORY_RESERVED_PROP_SET);
        when(configDescriptor.getPropertySets()).thenReturn(List.of(artifactoryDefaultPropertySet));
        when(configService.getDescriptor()).thenReturn(configDescriptor);

        // when
        HashMap<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges = new HashMap<>();
        interceptor.onBeforeSave(new RepoConfigChanges(repoConfigChanges));

        // then - repo is created
        List<RepoConfigChange> localRepoConfigChanges =
                repoConfigChanges.getOrDefault(LOCAL, List.of());
        assertEquals(1, localRepoConfigChanges.size());
        assertEquals(EDGE_UPLOADS_REPO_KEY, localRepoConfigChanges.get(0).getRepoKey());
        List<String> propertySetRefs =
                ((LocalRepoConfig) localRepoConfigChanges.get(0).getNewRepoConfig()).getRepoTypeConfig()
                        .getPropertySetRefs();
        assertEquals(1, propertySetRefs.size());
        assertEquals(ARTIFACTORY_RESERVED_PROP_SET, propertySetRefs.get(0));
    }

    @Test
    public void testDoNotCreateEdgeUploadRepoIfExists() {
        // given - repo exists
        when(addonsManager.isEntitled(EntitlementType.EDGE)).thenReturn(true);
        when(repositoryService.existsByRepoKey(EDGE_UPLOADS_REPO_KEY)).thenReturn(true);
        // when
        HashMap<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges = new HashMap<>();
        interceptor.onBeforeSave(new RepoConfigChanges(repoConfigChanges));
        // then
        assertTrue(repoConfigChanges.isEmpty());
    }

    @Test
    public void testDoNotCreateEdgeUploadRepoIfNotEntitled() {
        when(addonsManager.isEntitled(EntitlementType.EDGE)).thenReturn(false);
        when(repositoryService.existsByRepoKey(EDGE_UPLOADS_REPO_KEY)).thenReturn(false);
        // when
        HashMap<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges = new HashMap<>();
        interceptor.onBeforeSave(new RepoConfigChanges(repoConfigChanges));
        // then
        assertTrue(repoConfigChanges.isEmpty());
    }

    @Test
    public void deprecatedOnBeforeSave_CreateEdgeUploadRepo() {
        // given - entitled + repo does not exist
        when(addonsManager.isEntitled(EntitlementType.EDGE)).thenReturn(true);
        when(repositoryService.existsByRepoKey(EDGE_UPLOADS_REPO_KEY)).thenReturn(false);
        CentralConfigDescriptor configDescriptor = mock(CentralConfigDescriptor.class);
        PropertySet artifactoryDefaultPropertySet = new PropertySet();
        artifactoryDefaultPropertySet.setName(ARTIFACTORY_RESERVED_PROP_SET);
        when(configDescriptor.getPropertySets()).thenReturn(List.of(artifactoryDefaultPropertySet));
        when(configService.getDescriptor()).thenReturn(configDescriptor);

        // when
        HashMap<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges = new HashMap<>();
        interceptor.onBeforeSave(repoConfigChanges);

        // then - repo is created
        List<RepoConfigChange> localRepoConfigChanges =
                repoConfigChanges.getOrDefault(LOCAL, List.of());
        assertEquals(1, localRepoConfigChanges.size());
        assertEquals(EDGE_UPLOADS_REPO_KEY, localRepoConfigChanges.get(0).getRepoKey());
        List<String> propertySetRefs =
                ((LocalRepoConfig) localRepoConfigChanges.get(0).getNewRepoConfig()).getRepoTypeConfig()
                        .getPropertySetRefs();
        assertEquals(1, propertySetRefs.size());
        assertEquals(ARTIFACTORY_RESERVED_PROP_SET, propertySetRefs.get(0));
    }

    @Test
    public void testOnAfterSave() {
        // given
        MutableCentralConfigDescriptor mutableCentralConfigDescriptor = mock(MutableCentralConfigDescriptor.class);
        when(configService.getMutableDescriptor()).thenReturn(mutableCentralConfigDescriptor);

        // when
        interceptor.onAfterSave(Map.of(LOCAL,
                List.of(new RepoConfigChange(EDGE_UPLOADS_REPO_KEY, REPO_CREATE,
                        LocalRepoConfig.create("repoKey", RepoType.Generic), null))));

        // then
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mutableCentralConfigDescriptor).conditionallyAddToBackups(captor.capture());
        assertEquals(EDGE_UPLOADS_REPO_KEY, captor.getValue());
    }

    @Test(expectedExceptions = ConfigurationException.class, expectedExceptionsMessageRegExp = "Cannot delete the " +
            "edge uploads repository")
    public void testOnBeforeSaveCannotDeleteEdgeUploadsRepo() {
        // given - entitled + repo exists
        when(addonsManager.isEntitled(EntitlementType.EDGE)).thenReturn(true);
        when(repositoryService.existsByRepoKey(EDGE_UPLOADS_REPO_KEY)).thenReturn(true);

        // when
        Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges =
                Map.of(LOCAL, List.of(new RepoConfigChange(EDGE_UPLOADS_REPO_KEY, REPO_DELETE, null, null)));

        // then - should throw ConfigurationException
        interceptor.onBeforeSave(new RepoConfigChanges(repoConfigChanges));
    }

    @Test
    public void testOnBeforeSaveAllowEdgeUploadsRepoDeletionDuringImport() {
        // given - entitled + repo exists
        when(addonsManager.isEntitled(EntitlementType.EDGE)).thenReturn(true);
        when(repositoryService.existsByRepoKey(EDGE_UPLOADS_REPO_KEY)).thenReturn(true);
        boolean isImport = true;

        // when
        Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges =
                Map.of(LOCAL, List.of(new RepoConfigChange(EDGE_UPLOADS_REPO_KEY, REPO_DELETE, null, null)));

        // then - should not throw ConfigurationException
        interceptor.onBeforeSave(new RepoConfigChanges(repoConfigChanges, isImport));
    }
}