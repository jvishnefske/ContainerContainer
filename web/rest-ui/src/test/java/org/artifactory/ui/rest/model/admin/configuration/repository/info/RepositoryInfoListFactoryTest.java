package org.artifactory.ui.rest.model.admin.configuration.repository.info;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.repo.RepoDetailsType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepositoryInfoListFactoryTest {

    @Test
    void createRepositoryInfo_local_excludesReleaseBundlesV2OnlyWhenRequested() {
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        CentralConfigService configService = mock(CentralConfigService.class);
        RepositoryService repositoryService = mock(RepositoryService.class);
        PackageSpecification packageSpecification = mock(PackageSpecification.class);
        when(packageSpecification.hasReindex(any(), any())).thenReturn(false);

        when(repositoryConfigService.getLocalRepoConfigsExcludingInfoRepos(false)).thenReturn(List.of(
                LocalRepoConfig.create("p-release-bundles-v2", RepoType.ReleaseBundles),
                LocalRepoConfig.create("p-application-versions", RepoType.ReleaseBundles),
                LocalRepoConfig.create("p-generic-local", RepoType.Generic)));

        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        when(descriptor.getMultiLocalReplications(any())).thenReturn(List.of());
        when(configService.getDescriptor()).thenReturn(descriptor);

        List<RepositoryInfo> infosWithoutExclusion = RepositoryInfoListFactory.createRepositoryInfo(
                RepoDetailsType.LOCAL_REPO, false, false,
                configService, repositoryService, repositoryConfigService, packageSpecification);

        assertEquals(3, infosWithoutExclusion.size());

        List<RepositoryInfo> infosWithExclusion = RepositoryInfoListFactory.createRepositoryInfo(
                RepoDetailsType.LOCAL_REPO, false, true,
                configService, repositoryService, repositoryConfigService, packageSpecification);

        assertEquals(1, infosWithExclusion.size());
        assertEquals("p-generic-local", infosWithExclusion.getFirst().getRepoKey());
        assertInstanceOf(LocalRepositoryInfo.class, infosWithExclusion.getFirst());
    }
}
