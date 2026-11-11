package org.artifactory.metadata.service;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.opentracing.ArtifactoryTracerConfiguration;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.awaitility.Awaitility;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Uriah Levy
 */
@Test
public class MetadataReindexConverterTest extends ArtifactoryHomeBoundTest {
    @Mock
    RepositoryService repositoryService;

    @Mock
    MetadataEventService metadataEventService;

    @Mock
    ArtifactoryApplicationContext applicationContext;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(applicationContext);
        when(applicationContext.beanForType(MetadataEventService.class)).thenReturn(metadataEventService);
        when(applicationContext.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(applicationContext.beanForType(ArtifactoryTracer.class))
                .thenReturn(new ArtifactoryTracer(ArtifactoryTracerConfiguration.testTracer()));
    }

    @AfterMethod
    public void unbind() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    public void testReindexByType() {
        MetadataReindexConverter metadataReindexConverter =
                new MetadataReindexConverter(Set.of(RepoType.Docker, RepoType.NuGet), true);

        when(repositoryService.getRepoConfigsByPackageType(RepoType.Docker))
                .thenReturn(List.of(LocalRepoConfig.create("docker-local", RepoType.Docker),
                        RemoteRepoConfig.create("docker-remote", RepoType.Docker)));

        when(repositoryService.getRepoConfigsByPackageType(RepoType.NuGet))
                .thenReturn(List.of(LocalRepoConfig.create("nuget-local", RepoType.NuGet)));

        metadataReindexConverter.convert();

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(metadataEventService).reindexSync(List.of("docker-local"), true));
        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(metadataEventService).reindexSync(List.of("docker-remote"), true));
        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(metadataEventService).reindexSync(List.of("nuget-local"), true));
    }

    public void testConverterRunCriteria() {
        assertFalse(
                new MetadataReindexConverter(Set.of(), false).isInterested(
                        ArtifactoryServiceVersionProvider.v6170m001.get()));
        assertTrue(
                new MetadataReindexConverter(Set.of(), false).isInterested(
                        ArtifactoryServiceVersionProvider.v700m001.get()));
    }
}