package org.artifactory.lifecycle.converter.postinit.v105;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.api.internal.AqlBase;
import org.artifactory.aql.model.AqlItemTypeEnum;
import org.artifactory.aql.result.AqlEagerResult;
import org.artifactory.aql.result.rows.AqlItem;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.ph.selector.IndexingSelector;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.name;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.path;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.property;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.repo;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.type;
import static org.artifactory.aql.api.internal.AqlBase.and;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NpmDeprecateConverterTest {

    @Mock
    ArtifactoryContext artifactoryContext;
    @Mock
    RepositoryService repositoryService;
    @Mock
    IndexingSelector indexingSelector;
    @Mock
    AqlService aqlService;
    AutoCloseable closeable;
    NpmDeprecateConverter npmDeprecateConverter;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        when(artifactoryContext.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(artifactoryContext.beanForType(IndexingSelector.class)).thenReturn(indexingSelector);
        SecurityService securityService = mock(SecurityService.class);
        when(artifactoryContext.beanForType(SecurityService.class)).thenReturn(securityService);
        when(artifactoryContext.beanForType(AqlService.class)).thenReturn(aqlService);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        npmDeprecateConverter = new NpmDeprecateConverter(null);
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void convert() throws Exception {
        String repoKey = "repoKey";
        String path = "package/-";
        String name = "package-100.100.100.tgz";
        String name2 = "package-100.100.101.tgz";

        when(repositoryService.getLocalAndFederatedRepoConfigsExcludingInfoRepos()).thenReturn(
                List.of(LocalRepoConfig.create(repoKey, RepoType.Npm),
                        LocalRepoConfig.create("repoKey2", RepoType.Debian)));
        doAnswer(invocationOnMock -> {
            AqlApiItem query = invocationOnMock.getArgument(0);
            AqlApiItem expectedQuery = AqlApiItem.create()
                    .filter(
                            and(
                                    repo().equal(repoKey),
                                    type().equal(AqlItemTypeEnum.file.signature),
                                    property().key().equal("npm.deprecated")
                            ))
                    .include(name(), repo(), path());
            assertEquals(expectedQuery.toNative(0), query.toNative(0));
            AqlEagerResult aqlEagerResult = mock(AqlEagerResult.class);
            AqlItem aqlBaseItem = mock(AqlItem.class);
            when(aqlBaseItem.getName()).thenReturn(name);
            when(aqlBaseItem.getRepo()).thenReturn(repoKey);
            when(aqlBaseItem.getPath()).thenReturn(path);
            AqlItem aqlBaseItem2 = mock(AqlItem.class);
            when(aqlBaseItem2.getName()).thenReturn(name2);
            when(aqlBaseItem2.getRepo()).thenReturn(repoKey);
            when(aqlBaseItem2.getPath()).thenReturn(path);
            when(aqlEagerResult.getResults()).thenReturn(List.of(aqlBaseItem, aqlBaseItem2));
            return aqlEagerResult;
        }).when(aqlService).executeQueryEager(any(AqlBase.class));
        RepoPath repoPath = RepoPathFactory.create(repoKey, path + "/" + name);
        doThrow(new RuntimeException("error")).when(indexingSelector).reindex(repoKey, path + "/" + name2);
        npmDeprecateConverter.convertAsync();
        verify(indexingSelector, times(1)).reindex(repoPath.getRepoKey(), repoPath.getPath());
        verify(indexingSelector, times(1)).reindex(repoPath.getRepoKey(), path + "/" + name2);
    }
}