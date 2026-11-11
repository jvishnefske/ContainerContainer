/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.storage.db.security.service.access;

import jakarta.annotation.Nullable;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.model.security.RepoPermissionTargetImpl;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoConfigReload;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.security.AceInfo;
import org.artifactory.security.Acl;
import org.artifactory.security.BuildPermissionTarget;
import org.artifactory.security.MutableAceInfo;
import org.artifactory.security.PermissionTarget;
import org.artifactory.security.PrincipalPermission;
import org.artifactory.security.ReleaseBundlePermissionTarget;
import org.artifactory.security.RepoAcl;
import org.artifactory.security.RepoPermissionTarget;
import org.artifactory.security.access.AccessService;
import org.artifactory.storage.db.security.service.AclCacheLoader;
import org.artifactory.storage.db.security.service.AllAclCacheLoader;
import org.artifactory.storage.db.security.service.VersionCache;
import org.artifactory.test.ArtifactoryHomeStub;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.permission.PermissionsClient;
import org.jfrog.access.common.ServiceId;
import org.jfrog.common.ClockUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static org.artifactory.common.ConstantValues.permissionsCacheType;
import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.getField;

/**
 * @author Noam Shemesh
 */
class AccessAclStoreServiceTest {
    private AclStoreServiceImpl aclStoreServiceImpl;

    @Mock
    private AccessService accessService;
    @Mock
    private PermissionsClient permissionsClient;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    VersionCache repoAclsCache;
    @Mock
    private VersionCache<AclCacheLoader.AclCacheItem<ReleaseBundlePermissionTarget>> releaseBundlesAclsCache;
    @Mock
    private VersionCache<AclCacheLoader.AclCacheItem<BuildPermissionTarget>> buildAclsCache;
    @Mock
    VersionCache<AllAclCacheLoader.AllAclCacheItem> allAclsCache;
    @Mock
    AclCacheLoader.AclCacheItem<RepoPermissionTarget> aclCacheItem;
    private AutoCloseable mocks;

    @BeforeEach
    void beforeTest() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryHome.bind(new ArtifactoryHomeStub("testhome"));
        ArtifactoryHome.get().getArtifactoryProperties().setProperty(permissionsCacheType.getPropertyName(), "legacy");
        aclStoreServiceImpl = new AclStoreServiceImpl();
        aclStoreServiceImpl.setAccessService(accessService);
        aclStoreServiceImpl.setAuthorizationService(authorizationService);
        aclStoreServiceImpl.init();

        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.aclMinimumWaitRefreshDataSeconds.getPropertyName(), "2");
        AccessClient accessClient = mock(AccessClient.class);
        when(accessClient.permissions()).thenReturn(permissionsClient);
        when(accessService.getAccessClient()).thenReturn(accessClient);
        when(accessService.getArtifactoryServiceId()).thenReturn(ServiceId.fromFormattedName("jfrt@service"));

        when(repoAclsCache.promoteVersion()).thenReturn(1);
        when(releaseBundlesAclsCache.promoteVersion()).thenReturn(1);
        when(buildAclsCache.promoteVersion()).thenReturn(1);
        when(allAclsCache.promoteVersion()).thenReturn(1);
        ReflectionTestUtils.setField(aclStoreServiceImpl, "repoAclsCache", repoAclsCache);
        ReflectionTestUtils.setField(aclStoreServiceImpl, "releaseBundlesAclsCache", releaseBundlesAclsCache);
        ReflectionTestUtils.setField(aclStoreServiceImpl, "buildsAclsCache", buildAclsCache);
        ReflectionTestUtils.setField(aclStoreServiceImpl, "allAclsCache", allAclsCache);
        Map<String, Map<String, Set<PrincipalPermission<RepoPermissionTarget>>>> groupResultMap = new HashMap<>();
        when(aclCacheItem.getGroupResultMap()).thenReturn(groupResultMap);
        when(repoAclsCache.get()).thenReturn(aclCacheItem);
        ReentrantLock lock = new ReentrantLock();
        ReflectionTestUtils.setField(aclStoreServiceImpl, "lock", lock);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(permissionsCacheType.getPropertyName(), permissionsCacheType.getDefValue());
        ArtifactoryHome.unbind();
        mocks.close();
    }

    @Test
    void cacheDisabled() {
        ArtifactoryHome.get().getArtifactoryProperties().setProperty(permissionsCacheType.getPropertyName(), "access");
        AclStoreServiceImpl aclStoreServiceImpl = new AclStoreServiceImpl();
        aclStoreServiceImpl.init();
        Object repoAclsCache = getField(aclStoreServiceImpl, "repoAclsCache");
        assertNull(repoAclsCache);
        Object buildsAclsCache = getField(aclStoreServiceImpl, "buildsAclsCache");
        assertNull(buildsAclsCache);
        Object releaseBundlesAclsCache = getField(aclStoreServiceImpl, "releaseBundlesAclsCache");
        assertNull(releaseBundlesAclsCache);
        Object allAclsCache = getField(aclStoreServiceImpl, "allAclsCache");
        assertNull(allAclsCache);
        assertEquals("Nothing to invalidate since the cache is disabled.", aclStoreServiceImpl.invalidateAclCache());
    }

    @ParameterizedTest
    @ValueSource(strings = {"legacy", "shadow"})
    void cacheEnabled(String cacheType) {
        ArtifactoryHome.get().getArtifactoryProperties().setProperty(permissionsCacheType.getPropertyName(), cacheType);
        AclStoreServiceImpl aclStoreServiceImpl = new AclStoreServiceImpl();
        aclStoreServiceImpl.init();
        Object repoAclsCache = getField(aclStoreServiceImpl, "repoAclsCache");
        assertNotNull(repoAclsCache);
        Object buildsAclsCache = getField(aclStoreServiceImpl, "buildsAclsCache");
        assertNotNull(buildsAclsCache);
        Object releaseBundlesAclsCache = getField(aclStoreServiceImpl, "releaseBundlesAclsCache");
        assertNotNull(releaseBundlesAclsCache);
        Object allAclsCache = getField(aclStoreServiceImpl, "allAclsCache");
        assertNotNull(allAclsCache);
    }

    @Test
    void testRefreshingCacheMultiThreaded() throws InterruptedException {
        setAclMinimumWaitRefreshDataSeconds(2);
        List<Thread> threadList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            when(accessService.ensureAuth(any())).thenReturn(ClockUtils.epochMillis() + 60000);
            threadList.add(new Thread(() -> aclStoreServiceImpl.getAclCache()));
        }
        threadList.forEach(Thread::run);
        for (Thread thread : threadList) {
            thread.join();
        }
        verify(accessService).ensureAuth(any());
        verify(repoAclsCache, times(1)).promoteVersion();
    }

    @Test
    void testRefreshingCacheWithAccessUpdatedOnce() {
        setAclMinimumWaitRefreshDataSeconds(2);
        when(accessService.ensureAuth(any())).thenReturn(ClockUtils.epochMillis() + 60000);
        aclStoreServiceImpl.getAclCache();
        aclStoreServiceImpl.getAclCache();
        AtomicLong cacheInvalidationLastChecked = new AtomicLong(ClockUtils.epochMillis() - 2500);
        ReflectionTestUtils.setField(aclStoreServiceImpl, "cacheInvalidationLastChecked", cacheInvalidationLastChecked);
        aclStoreServiceImpl.getAclCache();
        verify(accessService, times(2)).ensureAuth(any());
        verify(repoAclsCache, times(1)).promoteVersion();
    }

    @Test
    void testRefreshingCacheWithAccessUpdatedTwice() {
        setAclMinimumWaitRefreshDataSeconds(2);
        when(accessService.ensureAuth(any())).thenReturn(ClockUtils.epochMillis() + 40000);
        aclStoreServiceImpl.getAclCache();
        aclStoreServiceImpl.getAclCache();
        AtomicLong cacheInvalidationLastChecked = new AtomicLong(ClockUtils.epochMillis() - 2500);
        ReflectionTestUtils.setField(aclStoreServiceImpl, "cacheInvalidationLastChecked", cacheInvalidationLastChecked);
        when(accessService.ensureAuth(any())).thenReturn(ClockUtils.epochMillis() + 60000);
        aclStoreServiceImpl.getAclCache();
        verify(accessService, times(2)).ensureAuth(any());
        verify(repoAclsCache, times(2)).promoteVersion();
    }

    @Test
    void testSkipRefreshingWithinTheRefreshInterval() {
        setAclMinimumWaitRefreshDataSeconds(10);
        when(accessService.ensureAuth(any())).thenReturn(ClockUtils.epochMillis() + 60000);
        aclStoreServiceImpl.getAclCache();
        aclStoreServiceImpl.getAclCache();
        verify(accessService).ensureAuth(any());
        verify(repoAclsCache, times(1)).promoteVersion();
    }

    @Test
    void testSkipRefreshingWithinTheRefreshIntervalAndAccessIsNotUpdated() {
        setAclMinimumWaitRefreshDataSeconds(10);
        AtomicLong accessPermissionLastUpdated = new AtomicLong(ClockUtils.epochMillis());
        ReflectionTestUtils.setField(aclStoreServiceImpl, "accessPermissionLastUpdated", accessPermissionLastUpdated);
        when(accessService.ensureAuth(any())).thenReturn(ClockUtils.epochMillis() - 60000);
        aclStoreServiceImpl.getAclCache();
        aclStoreServiceImpl.getAclCache();
        verify(accessService).ensureAuth(any());
        verify(repoAclsCache, times(0)).promoteVersion();
    }

    @Test
    void testSkipRefreshingNotWithinTheRefreshInterval() {
        setAclMinimumWaitRefreshDataSeconds(10);
        AtomicLong cacheInvalidationLastChecked = new AtomicLong(ClockUtils.epochMillis());
        ReflectionTestUtils.setField(aclStoreServiceImpl, "cacheInvalidationLastChecked", cacheInvalidationLastChecked);
        aclStoreServiceImpl.getAclCache();
        verify(permissionsClient, times(0)).lastUpdated();
        verify(repoAclsCache, times(0)).promoteVersion();
    }

    @Test
    void testUpdateAclIsSkippedForInternal() {
        setAclMinimumWaitRefreshDataSeconds(2);
        when(permissionsClient.lastUpdated()).thenReturn(ClockUtils.epochMillis());
        Acl<? extends PermissionTarget> acl = new MockAcl();
        when(authorizationService.isInternalAcl(acl)).thenReturn(false);
        aclStoreServiceImpl.updateAcl(acl);
        verify(permissionsClient).replacePermission(any());
        clearInvocations(permissionsClient);

        when(authorizationService.isInternalAcl(acl)).thenReturn(true);
        aclStoreServiceImpl.updateAcl(acl);
        verifyNoMoreInteractions(permissionsClient);
    }

    @ParameterizedTest
    @EnumSource(RepoConfigChange.ConfigAction.class)
    void testOnRepoConfigReload(RepoConfigChange.ConfigAction configAction) {
        Map<RepoDetailsType, List<RepoConfigReload>> repoConfigReload = Map.of(
                RepoDetailsType.LOCAL, List.of(
                        new RepoConfigReload("repoKey", RepoDetailsType.LOCAL, Generic,
                                configAction, 0L)
                )
        );

        aclStoreServiceImpl.onRepoConfigReload(repoConfigReload);

        if (REPO_CREATE == configAction) {
            verify(repoAclsCache, times(1)).promoteVersion();
            verify(buildAclsCache, times(1)).promoteVersion();
            verify(releaseBundlesAclsCache, times(1)).promoteVersion();
            verify(allAclsCache, times(1)).promoteVersion();
        } else {
            verifyNoInteractions(repoAclsCache);
            verifyNoInteractions(buildAclsCache);
            verifyNoInteractions(releaseBundlesAclsCache);
            verifyNoInteractions(allAclsCache);
        }
    }

    private void setAclMinimumWaitRefreshDataSeconds(int refreshWaitIntervalSeconds) {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.aclMinimumWaitRefreshDataSeconds.getPropertyName(),
                        Integer.toString(refreshWaitIntervalSeconds));
        aclStoreServiceImpl.initRefreshTimeMillis();

    }

    public static class MockAcl implements RepoAcl {

        @Override
        public RepoPermissionTarget getPermissionTarget() {
            return new RepoPermissionTargetImpl("permissionTarget");
        }

        @Override
        public Set<AceInfo> getAces() {
            return Collections.emptySet();
        }

        @Override
        public Set<MutableAceInfo> getMutableAces() {
            return Collections.emptySet();
        }

        @Override
        public String getUpdatedBy() {
            return null;
        }

        @Override
        public long getLastUpdated() {
            return 0;
        }

        @Nullable
        @Override
        public String getAccessIdentifier() {
            return "identifier";
        }
    }
}