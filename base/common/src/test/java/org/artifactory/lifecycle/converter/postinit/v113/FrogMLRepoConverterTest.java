package org.artifactory.lifecycle.converter.postinit.v113;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.opentracing.ArtifactoryTracer;
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

import java.util.List;
import java.util.stream.Stream;

import static org.artifactory.descriptor.repo.RepoType.FrogML;
import static org.artifactory.descriptor.repo.RepoType.MachineLearning;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v71040;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7530;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7770;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7930;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author Volodymyr Shulga
 */
class FrogMLRepoConverterTest extends ArtifactoryHomeBoundJUnitTest {
    private final FrogMLRepoConverter converter = new FrogMLRepoConverter(v71040.get());
    @Mock
    private ArtifactoryContext context;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private ArtifactoryTracer artifactoryTracer;
    @Mock
    private SecurityService securityService;
    @Captor
    private ArgumentCaptor<List<RepoConfig>> listArgumentCaptor;
    private AutoCloseable mocks;

    private LocalRepoConfig frogMLRepo;
    private LocalRepoConfig machineLearningRepo;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        frogMLRepo = LocalRepoConfig.create("frogml-local", FrogML);
        machineLearningRepo = LocalRepoConfig.create("ml-local", MachineLearning);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void testConvert() {
        doReturn(repositoryService).when(context).beanForType(RepositoryService.class);
        doReturn(securityService).when(context).beanForType(SecurityService.class);
        doReturn(artifactoryTracer).when(context).beanForType(ArtifactoryTracer.class);
        doReturn(List.of(frogMLRepo, machineLearningRepo)).when(repositoryService)
                .getLocalAndFederatedRepoConfigsExcludingInfoRepos();
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            Runnable runnable = (Runnable) args[0];
            runnable.run();
            return null;
        }).when(securityService).doAsSystem(any(Runnable.class));

        CompoundVersionDetails compoundVersionDetails = mock(CompoundVersionDetails.class);
        converter.convert(compoundVersionDetails, compoundVersionDetails);

        verify(securityService).doAsSystem(any(Runnable.class));
        verify(repositoryService).bulkUpsertRepoConfigs(listArgumentCaptor.capture(), eq(true));

        final List<RepoConfig> captorValue = listArgumentCaptor.getValue();
        assertEquals(1, captorValue.size());
        captorValue.forEach(
                repoConfig -> assertEquals(RepoType.MachineLearning, repoConfig.getPackageType())
        );
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

    private static Stream<Arguments> sourceTargetVersions() {
        return Stream.of(
                Arguments.of(v7770.get(), v71040.get(), false),
                Arguments.of(v7530.get(), v71040.get(), false),
                Arguments.of(v7930.get(), v71040.get(), true),
                Arguments.of(v71040.get(), v71040.get(), false)
        );
    }
}