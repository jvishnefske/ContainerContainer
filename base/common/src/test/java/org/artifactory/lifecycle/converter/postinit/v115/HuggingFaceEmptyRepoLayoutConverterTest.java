package org.artifactory.lifecycle.converter.postinit.v115;

import lombok.SneakyThrows;
import org.artifactory.addon.huggingfaceml.HuggingFaceLayoutConverter;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.aql.AqlService;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.CompoundVersionDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.descriptor.repo.RepoType.HuggingFaceML;
import static org.artifactory.descriptor.repo.RepoType.MachineLearning;
import static org.artifactory.util.HuggingFaceMLPackageTypeConfigMapper.IS_ML_REPO_LAYOUT;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v71040;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v71110;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7530;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7635;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7930;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Volodymyr Shulga
 */
class HuggingFaceEmptyRepoLayoutConverterTest extends ArtifactoryHomeBoundJUnitTest {
    private static final String EMPTY_FROGML_REPO_KEY = "emptyFrogMLRepo";
    private static final String EMPTY_REMOTE_REPO_KEY = "empty-remote";
    private static final String EMPTY_FEDERATED_REPO_KEY = "empty-federated";

    private HuggingFaceEmptyRepoLayoutConverter converter;
    @Mock
    private ArtifactoryContext context;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private SecurityService securityService;
    @Mock
    private AqlService aqlService;
    @Mock
    private HuggingFaceLayoutConverter layoutConverter;
    @Captor
    private ArgumentCaptor<String> repoKeyCaptor;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        converter = spy(new HuggingFaceEmptyRepoLayoutConverter(v71110.get()));
        doReturn(repositoryService).when(context).beanForType(RepositoryService.class);
        doReturn(securityService).when(context).beanForType(SecurityService.class);
        doReturn(aqlService).when(context).beanForType(AqlService.class);
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            Runnable runnable = (Runnable) args[0];
            runnable.run();
            return null;
        }).when(securityService).doAsSystem(any(Runnable.class));
        doReturn(layoutConverter).when(converter).getHuggingFaceLayoutConverter();
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @SneakyThrows
    @Test
    void testConvert() {
        LocalRepoConfig nonEmptyFrogMLRepo = LocalRepoConfig.create("frogml-local", HuggingFaceML);
        LocalRepoConfig emptyFrogMLRepo = LocalRepoConfig.create(EMPTY_FROGML_REPO_KEY, HuggingFaceML);
        LocalRepoConfig emptyFrogMLRepoNewLayout = spy(LocalRepoConfig.create("emptyFrogMLRepoNewLayout",
                HuggingFaceML));
        when(emptyFrogMLRepoNewLayout.getPackageTypeConfig()).thenReturn(Map.of(IS_ML_REPO_LAYOUT, "true"));
        LocalRepoConfig machineLearningRepo = LocalRepoConfig.create("ml-local", MachineLearning);
        RemoteRepoConfig nonEmptyRemoteRepoConfig = RemoteRepoConfig.create("non-empty", HuggingFaceML);
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create(EMPTY_REMOTE_REPO_KEY, HuggingFaceML);
        LocalCacheRepoConfig cacheRepo = LocalCacheRepoConfig.create(nonEmptyRemoteRepoConfig);
        LocalCacheRepoConfig emptyCacheRepo = LocalCacheRepoConfig.create(remoteRepoConfig);
        FederatedRepoConfig emptyFederatedRepo = FederatedRepoConfig.create(EMPTY_FEDERATED_REPO_KEY, HuggingFaceML);

        doReturn(List.of(nonEmptyFrogMLRepo, machineLearningRepo, cacheRepo,
                emptyFederatedRepo, emptyCacheRepo, emptyFrogMLRepo, emptyFrogMLRepoNewLayout))
                .when(repositoryService).getLocalFederatedAndCachedRepoConfigs();

        when(repositoryService.hasChildren(createRepoPath(nonEmptyFrogMLRepo))).thenReturn(true);
        when(repositoryService.hasChildren(createRepoPath(machineLearningRepo))).thenReturn(false);
        when(repositoryService.hasChildren(createRepoPath(cacheRepo))).thenReturn(true);
        when(repositoryService.hasChildren(createRepoPath(emptyFederatedRepo))).thenReturn(false);
        when(repositoryService.hasChildren(createRepoPath(emptyCacheRepo))).thenReturn(false);
        when(repositoryService.hasChildren(createRepoPath(emptyFrogMLRepo))).thenReturn(false);
        when(repositoryService.hasChildren(createRepoPath(emptyFrogMLRepoNewLayout))).thenReturn(false);

        CompoundVersionDetails compoundVersionDetails = mock(CompoundVersionDetails.class);
        converter.convert(compoundVersionDetails, compoundVersionDetails);
        verify(layoutConverter, times(2)).convert(repoKeyCaptor.capture());

        final Set<String> capturedRepoKeys = new HashSet<>(repoKeyCaptor.getAllValues());
        assertEquals(2, capturedRepoKeys.size());
        assertTrue(capturedRepoKeys.contains(EMPTY_FROGML_REPO_KEY));
        assertTrue(capturedRepoKeys.contains(EMPTY_REMOTE_REPO_KEY));
    }

    @SneakyThrows
    @Test
    void testExceptionHandledProperlyDuringConvert() {
        LocalRepoConfig emptyFrogMLRepo = LocalRepoConfig.create(EMPTY_FROGML_REPO_KEY, HuggingFaceML);
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create(EMPTY_REMOTE_REPO_KEY, HuggingFaceML);
        LocalCacheRepoConfig emptyCacheRepo = LocalCacheRepoConfig.create(remoteRepoConfig);
        FederatedRepoConfig emptyFederatedRepo = FederatedRepoConfig.create(EMPTY_FEDERATED_REPO_KEY, HuggingFaceML);

        doReturn(List.of(emptyFederatedRepo, emptyCacheRepo, emptyFrogMLRepo))
                .when(repositoryService).getLocalFederatedAndCachedRepoConfigs();

        when(repositoryService.hasChildren(createRepoPath(emptyFederatedRepo))).thenReturn(false);
        when(repositoryService.hasChildren(createRepoPath(emptyCacheRepo))).thenReturn(false);
        when(repositoryService.hasChildren(createRepoPath(emptyFrogMLRepo))).thenReturn(false);

        doAnswer(invocation -> {
            throw new Exception("Failed to convert layout");
        }).when(layoutConverter).convert(EMPTY_FROGML_REPO_KEY);

        CompoundVersionDetails compoundVersionDetails = mock(CompoundVersionDetails.class);
        converter.convert(compoundVersionDetails, compoundVersionDetails);
        verify(layoutConverter, times(2)).convert(repoKeyCaptor.capture());

        final Set<String> capturedRepoKeys = new HashSet<>(repoKeyCaptor.getAllValues());
        assertEquals(2, capturedRepoKeys.size());
        assertTrue(capturedRepoKeys.contains(EMPTY_FROGML_REPO_KEY));
        assertTrue(capturedRepoKeys.contains(EMPTY_REMOTE_REPO_KEY));
    }

    @ParameterizedTest
    @MethodSource("sourceTargetVersions")
    void testIsInterested(ArtifactoryServiceVersion source, ArtifactoryServiceVersion target, boolean interested) {
        CompoundVersionDetails sourceDetails = mock(CompoundVersionDetails.class);
        CompoundVersionDetails targetDetails = mock(CompoundVersionDetails.class);
        doReturn(source).when(sourceDetails).getVersion();
        doReturn(target).when(targetDetails).getVersion();
        assertEquals(interested, converter.isInterested(sourceDetails, targetDetails));
    }

    private RepoPath createRepoPath(LocalConfig repoConfig) {
        return RepoPathFactory.create(repoConfig.getKey());
    }

    private static Stream<Arguments> sourceTargetVersions() {
        return Stream.of(
                Arguments.of(v7635.get(), v71040.get(), false),
                Arguments.of(v7530.get(), v71110.get(), false),
                Arguments.of(v7930.get(), v71110.get(), true),
                Arguments.of(v71110.get(), v71110.get(), false)
        );
    }
}