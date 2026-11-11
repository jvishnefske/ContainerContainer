package org.artifactory.repo.service;

import com.google.common.collect.ImmutableList;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.cache.RepoConfigCache;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.artifactory.storage.db.repo.service.RepositoryConfigStorageService;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RepositoryConfigServiceDeleteFlowV2Test extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryContext context;
    @Mock
    private CentralConfigService centralConfigService;
    private AutoCloseable mocks;

    @BeforeEach
    void beforeEach() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        mockCentralConfig();
    }

    private void mockCentralConfig() {
        when(centralConfigService.getMutableDescriptor()).thenReturn(new CentralConfigDescriptorImpl());
        when(context.getCentralConfig()).thenReturn(centralConfigService);
    }

    @AfterEach
    void afterEach() throws Exception {
        mocks.close();
        ArtifactoryContextThreadBinder.unbind();
        setDefaultRemoveRepoFlowV2();
    }

    private void setDefaultRemoveRepoFlowV2() {
        getBound().setProperty(ConstantValues.removeRepositoryFlowV2Enabled.getPropertyName(),
                ConstantValues.removeRepositoryFlowV2Enabled.getDefValue());
    }

    @Test
    void testBulkDeleteFlowV1CallInterceptors() {
        disableRemoveRepoFlowV2();
        RepoConfigChangeInterceptor interceptor = prepareAndExecuteBulkDelete(false);
        verify(interceptor).onBeforeSave(any(RepoConfigChanges.class));
    }

    @Test
    void testBulkDeleteFlowV2CallInterceptorsOnImport() {
        enableRemoveRepoFlowV2();
        RepoConfigChangeInterceptor interceptor = prepareAndExecuteBulkDelete(true);
        verify(interceptor).onBeforeSave(any(RepoConfigChanges.class));
    }

    @Test
    void testBulkDeleteFlowV2DoNotCallInterceptors() {
        enableRemoveRepoFlowV2();
        RepoConfigChangeInterceptor interceptor = prepareAndExecuteBulkDelete(false);
        verifyNoInteractions(interceptor);
    }

    private static RepoConfigChangeInterceptor prepareAndExecuteBulkDelete(boolean isImport) {
        RepoConfigCache repoConfigCache = mockRepoConfigCache();
        AddonsManager addonsManager = mock(AddonsManager.class);
        HaCommonAddon haCommonAddon = mock(HaCommonAddon.class);
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        @SuppressWarnings("unchecked")
        ConflictsGuard<Object> conflictsGuard = mock(ConflictsGuard.class);
        when(haCommonAddon.getConflictsGuard(any())).thenReturn(conflictsGuard);
        try {
            when(conflictsGuard.tryToLock(any(), anyLong(), any())).thenReturn(true);
        } catch (InterruptedException e) {
            // Won't happen in setup
        }

        RepositoryConfigServiceImpl repositoryConfigService = new RepositoryConfigServiceImpl(
                mock(RepositoryConfigStorageService.class), Set.of(), addonsManager);
        repositoryConfigService.setCache(repoConfigCache);

        RepoConfigChangeInterceptor interceptor = mockAndSetRepoConfigInterceptors(repositoryConfigService);

        RepoConfig repoConfig = mockRepoConfig();
        repositoryConfigService.bulkDeleteRepositoryConfigsInternal(
                List.of(repoConfig), false, false, isImport);
        return interceptor;
    }

    private static RepoConfigChangeInterceptor mockAndSetRepoConfigInterceptors(
            RepositoryConfigServiceImpl repositoryConfigService) {
        RepoConfigChangeInterceptor interceptor = mock(RepoConfigChangeInterceptor.class);
        ReflectionTestUtils.setField(repositoryConfigService, "interceptors", Set.of(interceptor));
        return interceptor;
    }

    private static RepoConfig mockRepoConfig() {
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getRepoType()).thenReturn(LOCAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);
        return repoConfig;
    }

    private void enableRemoveRepoFlowV2() {
        getBound().setProperty(ConstantValues.removeRepositoryFlowV2Enabled.getPropertyName(),
                Boolean.TRUE.toString());
    }

    private void disableRemoveRepoFlowV2() {
        getBound().setProperty(ConstantValues.removeRepositoryFlowV2Enabled.getPropertyName(),
                Boolean.FALSE.toString());
    }

    private static RepoConfigCache mockRepoConfigCache() {
        RepoConfigCache repoConfigCache = mock(RepoConfigCache.class);
        ImmutableList<RepoConfig> repoConfigs = new ImmutableList.Builder<RepoConfig>().build();
        when(repoConfigCache.getRepositoryConfigsByKeys(any())).thenReturn(repoConfigs);
        return repoConfigCache;
    }
}