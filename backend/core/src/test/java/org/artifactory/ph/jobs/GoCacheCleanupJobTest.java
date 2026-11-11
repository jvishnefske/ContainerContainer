package org.artifactory.ph.jobs;

import com.jfrog.ph.acl.service.PackageJobController;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;

import static org.artifactory.ph.jobs.GoCacheCleanupJob.GO_CLEANUP_SERVICE;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class GoCacheCleanupJobTest extends ArtifactoryHomeBoundJUnitTest {

    private GoCacheCleanupJob goCacheCleanupJob;
    @Mock
    private ArtifactoryContext context;
    @Mock
    private PackageJobController packageJobController;
    private AutoCloseable mocksClosable;

    @BeforeEach
    void setUp() {
        mocksClosable = MockitoAnnotations.openMocks(this);
        goCacheCleanupJob = Mockito.spy(new GoCacheCleanupJob());
        ArtifactoryContextThreadBinder.bind(context);
        when(context.beanForType(PackageJobController.class)).thenReturn(packageJobController);
        getBound();
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocksClosable.close();
        unbindArtifactoryHome();
    }

    @Test
    void testRun() {
        goCacheCleanupJob.onExecute(null);
        verify(packageJobController).run(GO_CLEANUP_SERVICE);
        verifyNoMoreInteractions(packageJobController);
    }

    @Test
    void testBuildQuartzExp() {
        Assert.assertEquals(GoCacheCleanupJob.buildQuartzExp(), "0 16 3 1/7 * ? *");
    }

    @Test
    void testBuildQuartzExpWhenOverride() {
        setStringSystemProperty(ConstantValues.goRemoteCacheCleanupJobCron, "0 1 1 1 * ? *");
        Assert.assertEquals(GoCacheCleanupJob.buildQuartzExp(), "0 1 1 1 * ? *");
    }

    @Test
    void testBuildQuartzExpWhenOverrideWithBadExpression() {
        setStringSystemProperty(ConstantValues.goRemoteCacheCleanupJobCron, "0 1 1 1");
        Assert.assertEquals(GoCacheCleanupJob.buildQuartzExp(), "0 16 3 1/7 * ? *");
    }
}