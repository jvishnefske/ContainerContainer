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

package org.artifactory.repo.virtual.interceptor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.jfrog.test.log.MemoryAppender;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.repo.exception.maven.BadPomException;
import org.artifactory.descriptor.repo.PomCleanupPolicy;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.RepoResource;
import org.artifactory.io.SimpleResourceStreamHandle;
import org.artifactory.md.MetadataInfo;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.request.NullRequestContext;
import org.artifactory.request.RemoteRequestException;
import org.artifactory.resource.FileResource;
import org.artifactory.resource.MetadataResource;
import org.artifactory.resource.UnfoundRepoResource;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.junit.jupiter.api.Assertions;
import org.slf4j.LoggerFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.artifactory.util.PackageTypeConfigFields.POM_REPOSITORY_REFERENCES_CLEANUP_POLICY;
import static org.jfrog.common.ResourceUtils.getResource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * @author Uriah Levy
 */
@Test
public class PomInterceptorTest extends ArtifactoryHomeBoundTest {


    public void sameResourceReturnedIfNotPom() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Generic);
        virtualRepoConfig.getPackageTypeConfig()
                .put(POM_REPOSITORY_REFERENCES_CLEANUP_POLICY.getKey(),
                        PomCleanupPolicy.nothing.name());
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(virtualRepo.getRepoConfig()).thenReturn(virtualRepoConfig);

        RepoPathImpl nonPomRepoPath = new RepoPathImpl("repo", "not-pom");
        RepoResource original = new FileResource(new FileInfoImpl(nonPomRepoPath));
        RepoResource returnedResource = new PomInterceptor(null).onBeforeReturn(virtualRepo,
                new NullRequestContext(nonPomRepoPath), original);
        assertSame(returnedResource, original);
    }

    public void pomNotFoundError() throws RepoRejectException, IOException {
        InternalRepositoryService repoService = mock(InternalRepositoryService.class);
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Maven);
        virtualRepoConfig.getPackageTypeConfig()
                .put(POM_REPOSITORY_REFERENCES_CLEANUP_POLICY.getKey(),
                     PomCleanupPolicy.discard_any_reference.name());
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(virtualRepo.getRepoConfig()).thenReturn(virtualRepoConfig);
        when(virtualRepo.getKey()).thenReturn("virtual");
        when(repoService.getResourceStreamHandle(any(), any(), any())).thenAnswer(invocation -> {
            throw new RemoteRequestException("Unable to find some url to pom", 404, "not found");
        });
        RepoPathImpl pomPath = new RepoPathImpl("repo", "file.pom");
        RepoResource original = new FileResource(new FileInfoImpl(pomPath));
        PomInterceptor pomInterceptor = new PomInterceptor(repoService);
        RepoResource returnedResource =
                pomInterceptor.onBeforeReturn(virtualRepo, new NullRequestContext(pomPath), original);
        assertTrue(returnedResource instanceof UnfoundRepoResource);
    }

    public void sameResourceReturnedOnNothingPolicy() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Generic);
        virtualRepoConfig.getPackageTypeConfig()
                .put(POM_REPOSITORY_REFERENCES_CLEANUP_POLICY.getKey(), PomCleanupPolicy.nothing.name());
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(virtualRepo.getRepoConfig()).thenReturn(virtualRepoConfig);
        RepoPathImpl pomRepoPath = new RepoPathImpl("repo", "some.pom");
        RepoResource original = new FileResource(new FileInfoImpl(pomRepoPath));
        RepoResource returnedResource = new PomInterceptor(null)
                .onBeforeReturn(virtualRepo, new NullRequestContext(pomRepoPath), original);
        assertSame(returnedResource, original);
    }

    public void unchangedPomResourceNotSavedWithDiscardPolicy() throws IOException, RepoRejectException {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Generic);
        virtualRepoConfig.getPackageTypeConfig()
                .put(POM_REPOSITORY_REFERENCES_CLEANUP_POLICY.getKey(), PomCleanupPolicy.discard_any_reference.name());
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(virtualRepo.getRepoConfig()).thenReturn(virtualRepoConfig);
        RepoPathImpl pomRepoPath = new RepoPathImpl("repo", "nothing-to-clean.pom");
        RepoResource original = new FileResource(new FileInfoImpl(pomRepoPath));
        InternalRepositoryService repoService = mock(InternalRepositoryService.class);
        when(repoService.repositoryByKey("a-repo")).thenReturn(null);
        when(repoService.getResourceStreamHandle(any(), any(), any())).thenReturn(new SimpleResourceStreamHandle(
                getResource("/org/artifactory/repo/virtual/interceptor/clean-test.pom")));

        RepoResource returnedResource = new PomInterceptor(repoService)
                .onBeforeReturn(virtualRepo, new NullRequestContext(pomRepoPath), original);

        assertSame(returnedResource, original);
        verify(repoService, times(0)).saveResource(any(), any());
    }

    @Test(dataProvider = "compatiblePackageTypeProvider")
    public void changedPomResourceSavedWithDiscardPolicy(RepoType repoType, boolean cleanupExpected)
            throws IOException, RepoRejectException {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", repoType);
        virtualRepoConfig.getPackageTypeConfig()
                .put(POM_REPOSITORY_REFERENCES_CLEANUP_POLICY.getKey(),
                        PomCleanupPolicy.discard_any_reference.name());
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(virtualRepo.getRepoConfig()).thenReturn(virtualRepoConfig);

        when(virtualRepo.getKey()).thenReturn("virtual");
        RepoPathImpl pomRepoPath = new RepoPathImpl("a-repo", "clean-this.pom");
        RepoResource original = new FileResource(new FileInfoImpl(pomRepoPath));
        InternalRepositoryService repoService = mock(InternalRepositoryService.class);
        when(repoService.getResourceStreamHandle(any(), any(), any())).thenReturn(new SimpleResourceStreamHandle(
                getResource("/org/artifactory/repo/virtual/interceptor/activeByDefault-test.pom")));
        when(repoService.repositoryByKey("a-repo")).thenReturn(null);
        when(repoService.saveResource(any(), any())).thenReturn(new MetadataResource((MetadataInfo) null));

        RepoResource returnedResource = new PomInterceptor(repoService)
                .onBeforeReturn(virtualRepo, new NullRequestContext(pomRepoPath), original);

        assertTrue(returnedResource.isFound());
        if (cleanupExpected) {
            assertNotSame(returnedResource, original);
            verify(repoService, times(1)).saveResource(any(), any());
        } else {
            assertSame(returnedResource, original);
            verify(repoService, times(0)).saveResource(any(), any());
        }
    }

    @Test
    public void testValidateWarnMsgForBadPomException() throws IOException, RepoRejectException {
        MemoryAppender memoryAppender = getMemoryAppender(null);
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Maven);
        virtualRepoConfig.getPackageTypeConfig()
                .put(POM_REPOSITORY_REFERENCES_CLEANUP_POLICY.getKey(),
                        PomCleanupPolicy.discard_any_reference.name());
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(virtualRepo.getRepoConfig()).thenReturn(virtualRepoConfig);

        when(virtualRepo.getKey()).thenReturn("virtual");
        RepoPathImpl pomRepoPath = new RepoPathImpl("a-repo", "clean-this.pom");
        RepoResource original = new FileResource(new FileInfoImpl(pomRepoPath));
        InternalRepositoryService repoService = mock(InternalRepositoryService.class);
        when(repoService.getResourceStreamHandle(any(), any(), any())).thenThrow(new BadPomException("Invalid POM"));
        when(repoService.repositoryByKey("a-repo")).thenReturn(null);

        new PomInterceptor(repoService).onBeforeReturn(virtualRepo, new NullRequestContext(pomRepoPath), original);

        Assertions.assertEquals(1,
                memoryAppender.search("Failed to transform pom file : Invalid POM", Level.WARN).size());
    }

    @DataProvider
    public static Object[][] compatiblePackageTypeProvider() {
        return new Object[][] {
                {RepoType.Maven, true},
                {RepoType.Gradle, true},
                {RepoType.Ivy, true},
                {RepoType.P2, true},
                {RepoType.Generic, true},
                {RepoType.Docker, false},
                {RepoType.Npm, false},
                {RepoType.Alpine, false},
                {RepoType.Vagrant, false}
        };
    }

    public void getPomRepositoryReferencesCleanupPolicyTest() {
        MemoryAppender memoryAppender = getMemoryAppender(Level.DEBUG);
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Generic);
        PomInterceptor pomInterceptor = new PomInterceptor(null);
        PomCleanupPolicy cleanupPolicy =
                pomInterceptor.getPomRepositoryReferencesCleanupPolicy(virtualRepoConfig);
        // expected default
        assertSame(cleanupPolicy, PomCleanupPolicy.discard_active_reference);
        Assertions.assertEquals(1,
                memoryAppender.search("Could not find pom cleanup policy configuration", Level.DEBUG).size());

        virtualRepoConfig.getPackageTypeConfig()
                .put(POM_REPOSITORY_REFERENCES_CLEANUP_POLICY.getKey(),
                        PomCleanupPolicy.nothing.name());
        cleanupPolicy =
                pomInterceptor.getPomRepositoryReferencesCleanupPolicy(virtualRepoConfig);
        assertSame(cleanupPolicy, PomCleanupPolicy.nothing);

        virtualRepoConfig.getPackageTypeConfig()
                .put(POM_REPOSITORY_REFERENCES_CLEANUP_POLICY.getKey(),
                        PomCleanupPolicy.discard_active_reference.name());
        cleanupPolicy =
                pomInterceptor.getPomRepositoryReferencesCleanupPolicy(virtualRepoConfig);
        assertSame(cleanupPolicy, PomCleanupPolicy.discard_active_reference);
        Assertions.assertEquals(1,
                memoryAppender.search("Could not find pom cleanup policy configuration", Level.DEBUG).size());
    }

    private static MemoryAppender getMemoryAppender(Level level) {
        Logger logger = (Logger) LoggerFactory.getLogger("org.artifactory.repo.virtual.interceptor.PomInterceptor");
        if (level != null) {
            logger.setLevel(level);
        }
        MemoryAppender appender = new MemoryAppender();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        logger.addAppender(appender);
        appender.start();
        return appender;
    }
}