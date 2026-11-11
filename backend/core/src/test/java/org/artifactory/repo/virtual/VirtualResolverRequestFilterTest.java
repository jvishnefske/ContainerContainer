package org.artifactory.repo.virtual;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VirtualResolverRequestFilterTest {

    @Mock
    private VirtualRepo virtualRepo;
    @Mock
    private RepoPath repoPath;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private PathTranslator pathTranslator;

    @Mock
    private CentralConfigService centralConfigService;

    @Test
    void checkImprovementInSkippingComparePaths() {
        VirtualResolverRequestFilter virtualResolverRequestFilter = spy(
                new VirtualResolverRequestFilter(virtualRepo, repoPath, repositoryService, pathTranslator,
                        centralConfigService));
        VirtualRepoConfig repoConfigA = VirtualRepoConfig.create("repoA", RepoType.Alpine);
        VirtualRepoConfig repoConfigB = VirtualRepoConfig.create("repoB", RepoType.Alpine);
        VirtualRepoConfig notFound = VirtualRepoConfig.create("notFound", RepoType.Alpine);
        when(repositoryService.virtualRepositoryByKey(startsWith("repo"))).thenReturn(virtualRepo);
        when(repositoryService.virtualRepositoryByKey(eq("notFound"))).thenReturn(null);
        doReturn(Boolean.TRUE)
                .doReturn(Boolean.FALSE)
                .when(virtualResolverRequestFilter)
                .comparePaths(any(), any(), any(), any());
        doReturn("asasa")
                .when(virtualResolverRequestFilter)
                .translateRepoPath(any(), any(), any());
        virtualResolverRequestFilter.accepts(repoConfigA);
        virtualResolverRequestFilter.accepts(repoConfigB);
        virtualResolverRequestFilter.accepts(notFound);
        virtualResolverRequestFilter.accepts(repoConfigA);
        Map<String, Boolean> expectedMap =
                new HashMap<>();
        expectedMap.put(repoConfigA.getKey(), Boolean.TRUE);
        expectedMap.put(repoConfigB.getKey(), Boolean.FALSE);
        expectedMap.put(notFound.getKey(), Boolean.FALSE);
        Assertions.assertEquals(expectedMap, virtualResolverRequestFilter.getFilterResultsForRepo());
        verify(repositoryService,times(3)).virtualRepositoryByKey(any());
        verify(virtualResolverRequestFilter,times(2)).comparePaths(any(),any(),any(),any());
        verify(virtualResolverRequestFilter,times(2)).translateRepoPath(any(),any(),any());
        verify(virtualResolverRequestFilter,times(4)).accepts(any(VirtualRepoConfig.class));
    }
}
