package org.artifactory.repo.service.deploy;

import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.maven.MavenService;
import org.artifactory.api.repo.DeployService;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.repo.exception.maven.BadPomException;
import org.artifactory.common.ConstantValues;
import org.artifactory.concurrent.ArtifactoryExecutor;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.Repo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.common.ResourceUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.io.File;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

public class DeployServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    private DeployServiceImpl deployService;
    private InternalRepositoryService repositoryService;
    private BasicStatusHolder status;
    private RealRepoConfig targetRepo;

    @Mock
    private InternalArtifactoryContext context;
    private AutoCloseable autoCloseable;

    @BeforeEach
    public void beforeEach() throws InterruptedException {
        autoCloseable = openMocks(this);
        repositoryService = mock(InternalRepositoryService.class);
        when(repositoryService.repositoryByKey("targetRepo")).thenReturn(mock(Repo.class));
        createDeployServiceAndAddToContext();
        status = new BasicStatusHolder();
        targetRepo = mock(RealRepoConfig.class);
        when(targetRepo.getKey()).thenReturn("targetRepo");
        when(context.getArtifactoryHome()).thenReturn(getOrCreateArtifactoryHomeStub());
        ArtifactoryContextThreadBinder.bind(context);
        this.setLongSystemProperty(ConstantValues.explodeArchiveThreads, 1);
        ArtifactoryTracer tracer = mock(ArtifactoryTracer.class);
        when(context.beanForType(ArtifactoryTracer.class)).thenReturn(tracer);
    }

    private void createDeployServiceAndAddToContext() {
        MavenService mavenService = mock(MavenService.class);
        try {
            doNothing().when(mavenService).validatePomFile(any(), anyString(), any(), anyBoolean());
        } catch (BadPomException e) {
            // ignore
        }
        deployService = spy(new DeployServiceImpl(repositoryService, null, mavenService));
        when(context.beanForType(DeployService.class)).thenReturn(deployService);
    }

    @AfterEach
    public void afterEach() throws Exception {
        autoCloseable.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testBundleDeploySingleThread() throws RepoRejectException {
        this.setLongSystemProperty(ConstantValues.explodeArchiveThreads, 1);
        File bundleToDeploy = ResourceUtils.getResourceAsFile("/org/artifactory/service/deploy/test-bundle.zip");
        deployService.deployBundle(bundleToDeploy, targetRepo, status, false, "prefix", new PropertiesImpl());
        verify(deployService, times(20)).deploy(any(RealRepoConfig.class), any(), any(), isNull(String.class),
                anyBoolean(),
                anyBoolean(), any());
    }

    @Test
    void testBundleDeploySingleThreadFailFastStatusHolder() throws RepoRejectException {
        this.setLongSystemProperty(ConstantValues.explodeArchiveThreads, 1);
        status.setFastFail(true);
        File bundleToDeploy = ResourceUtils.getResourceAsFile("/org/artifactory/service/deploy/test-bundle.zip");
        doThrow(new IllegalArgumentException("test")).doNothing().when(deployService)
                .deploy(any(RealRepoConfig.class), any(), any(), isNull(String.class),
                        anyBoolean(), anyBoolean(), any());
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> deployService.deployBundle(bundleToDeploy, targetRepo, status, true, "prefix",
                        new PropertiesImpl()));
        verify(deployService, times(1)).deploy(any(RealRepoConfig.class), any(), any(), isNull(String.class),
                anyBoolean(),
                anyBoolean(), any());
    }

    @Test
    void testBundleDeployMultipleThread() throws RepoRejectException {
        this.setLongSystemProperty(ConstantValues.explodeArchiveThreads, 5);
        createDeployServiceAndAddToContext();
        File bundleToDeploy = ResourceUtils.getResourceAsFile("/org/artifactory/service/deploy/test-bundle.zip");
        ArtifactoryExecutor artifactoryExecutor = spy(deployService.createExplodeExecutor());
        doReturn(artifactoryExecutor).when(deployService).createExplodeExecutor();
        clearInvocations(deployService);
        doNothing().when(deployService)
                .deploy(any(RealRepoConfig.class), any(), any(), isNull(String.class),
                        anyBoolean(), anyBoolean(), any());
        deployService.deployBundle(bundleToDeploy, targetRepo, status, false, "prefix", new PropertiesImpl());
        verify(deployService, times(20)).deploy(any(RealRepoConfig.class), any(), any(), isNull(String.class),
                anyBoolean(),
                anyBoolean(), any());
        verify(deployService, times(1)).createExplodeExecutor();
        Assertions.assertTrue(artifactoryExecutor.isShutdown());
    }

    @Test
    void testBundleDeployMultipleThreadError() throws RepoRejectException {
        this.setLongSystemProperty(ConstantValues.explodeArchiveThreads, 5);
        createDeployServiceAndAddToContext();
        File bundleToDeploy = ResourceUtils.getResourceAsFile("/org/artifactory/service/deploy/test-bundle.zip");
        ArtifactoryExecutor artifactoryExecutor = spy(deployService.createExplodeExecutor());
        doReturn(artifactoryExecutor).when(deployService).createExplodeExecutor();
        clearInvocations(deployService);
        doNothing().doThrow(new IllegalArgumentException("test")).doNothing().when(deployService)
                .deploy(any(RealRepoConfig.class), any(), any(), isNull(String.class),
                        anyBoolean(), anyBoolean(), any());
        deployService.deployBundle(bundleToDeploy, targetRepo, status, false, "prefix", new PropertiesImpl());
        verify(deployService, times(20)).deploy(any(RealRepoConfig.class), any(), any(), isNull(String.class),
                anyBoolean(),
                anyBoolean(), any());
        verify(deployService, times(1)).createExplodeExecutor();
        Assertions.assertTrue(artifactoryExecutor.isShutdown());
    }

    @Test
    void testBundleDeployMultipleThreadErrorFailFast() throws RepoRejectException {
        this.setLongSystemProperty(ConstantValues.explodeArchiveThreads, 5);
        createDeployServiceAndAddToContext();
        File bundleToDeploy = ResourceUtils.getResourceAsFile("/org/artifactory/service/deploy/test-bundle.zip");
        ArtifactoryExecutor artifactoryExecutor = spy(deployService.createExplodeExecutor());
        doReturn(artifactoryExecutor).when(deployService).createExplodeExecutor();
        when(targetRepo.getPackageTypeConfig()).thenReturn(Map.of());
        clearInvocations(deployService);
        doThrow(new IllegalArgumentException("test")).doNothing().when(deployService)
                .deploy(any(RealRepoConfig.class), any(), any(), isNull(String.class),
                        anyBoolean(), anyBoolean(), any());
        status = new BasicStatusHolder();
        deployService.deployBundle(bundleToDeploy, targetRepo, status, true, "prefix", new PropertiesImpl());
        // We have errors even before the first deploy
        verify(deployService, atMost(9)).deploy(any(RealRepoConfig.class), any(), any(), isNull(String.class),
                anyBoolean(),
                anyBoolean(), any());
        verify(deployService, times(1)).createExplodeExecutor();
        Assertions.assertTrue(artifactoryExecutor.isShutdown());
    }

    @Test
    void testBundleDeployMultipleThreadErrorFailFastStatusHolder() throws RepoRejectException {
        this.setLongSystemProperty(ConstantValues.explodeArchiveThreads, 5);
        createDeployServiceAndAddToContext();
        File bundleToDeploy = ResourceUtils.getResourceAsFile("/org/artifactory/service/deploy/test-bundle.zip");
        ArtifactoryExecutor artifactoryExecutor = spy(deployService.createExplodeExecutor());
        doReturn(artifactoryExecutor).when(deployService).createExplodeExecutor();
        when(targetRepo.getPackageTypeConfig()).thenReturn(Map.of());
        clearInvocations(deployService);
        doThrow(new IllegalArgumentException("test")).doNothing().when(deployService)
                .deploy(any(RealRepoConfig.class), any(), any(), anyString(),
                        anyBoolean(), anyBoolean(), any());
        status = new BasicStatusHolder();
        status.setFastFail(true);
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                deployService.deployBundle(bundleToDeploy, targetRepo, status, true, "prefix",
                        new PropertiesImpl()));
        // We have errors even before the first deploy
        verify(deployService, atMost(5)).deploy(any(RealRepoConfig.class), any(), any(), isNull(String.class),
                anyBoolean(),
                anyBoolean(), any());
        verify(deployService, times(1)).createExplodeExecutor();
        Assertions.assertTrue(artifactoryExecutor.isShutdown());
    }

}