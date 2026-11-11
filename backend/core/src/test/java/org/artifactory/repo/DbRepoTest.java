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

package org.artifactory.repo;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.db.DbLocalRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.util.RepoLayoutUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Unit tests for the DbRepo.
 *
 * @author Noam Tenne
 */
public class DbRepoTest extends ArtifactoryHomeBoundTest {

    @AfterMethod
    public void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void testNoFilePermission() {
        AuthorizationService authService = mock(AuthorizationService.class);
        SecurityService securityService = mock(SecurityService.class);

        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        configDescriptor.setRepoLayouts(List.of(RepoLayoutUtils.MAVEN_2_DEFAULT));
        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);
        InternalArtifactoryContext context = mock(InternalArtifactoryContext.class);
        when(context.getAuthorizationService()).thenReturn(authService);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        ArtifactoryContextThreadBinder.bind(context);
        InternalRepositoryService irs = mock(InternalRepositoryService.class);
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("libs", RepoType.Generic);
        localRepoConfig.getBaseConfig().setRepoLayoutRef(RepoLayoutUtils.MAVEN_2_DEFAULT.getName());
        DbLocalRepo dbLocalRepo = new DbLocalRepo<>(null, localRepoConfig, irs, null, centralConfigService);
        RepoPath path = InternalRepoPathFactory.create("libs", "jfrog/settings/jfrog-settings-sources.zip");
        when(authService.canRead(path)).thenReturn(false);
        when(securityService.currentUsername()).thenReturn("testUser");

        StatusHolder holder = dbLocalRepo.checkDownloadIsAllowed(path);
        Assert.assertTrue(holder.isError(), "User should not have access to src files");
    }

    @Test
    public void testNullPattern() {
        DbLocalRepo dbLocalRepo = createRepo();
        assertNull(dbLocalRepo.patternExcludes);
        assertTrue(dbLocalRepo.excludes.isEmpty());
    }

    @Test
    public void testIsPathPatternValid() {
        DbLocalRepo dbLocalRepo = createRepo();
        RepoPathImpl repoPath = new RepoPathImpl("libs", "", false);
        dbLocalRepo.isPathPatternValid(repoPath, "");
        assertTrue(dbLocalRepo.isPathPatternValid(repoPath, ""));
        dbLocalRepo.patternExcludes = Pattern.compile(".*");
        assertFalse(dbLocalRepo.isPathPatternValid(repoPath, "text.jar"));
        dbLocalRepo.patternExcludes = null;
        assertTrue(dbLocalRepo.isPathPatternValid(repoPath, "text.jar"));
        dbLocalRepo.excludes = List.of("*.jar");
        assertFalse(dbLocalRepo.isPathPatternValid(repoPath, "text.jar"));
    }

    private DbLocalRepo createRepo() {
        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        configDescriptor.setRepoLayouts(List.of(RepoLayoutUtils.MAVEN_2_DEFAULT));
        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);
        InternalArtifactoryContext context = mock(InternalArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        InternalRepositoryService irs = mock(InternalRepositoryService.class);
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("libs", RepoType.Generic);
        localRepoConfig.getBaseConfig().setRepoLayoutRef(RepoLayoutUtils.MAVEN_2_DEFAULT.getName());
        return new DbLocalRepo<>(null, localRepoConfig, irs, null, centralConfigService);
    }
}
