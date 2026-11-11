package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.repo.packagetype.DockerApiVersion;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_DOCKER_REMOTE_RETRIEVAL_CACHE_PERIOD;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author AndreiK.
 */
@Test
public class DockerTypeSpecificConfigModelTest extends ArtifactoryHomeBoundTest {

    @Test(description = "Tests the RetrievalCachePeriod default value for Docker remote repos. The setter and getter " +
            "are used for serializing the RetrievalCachePeriod to the 'defaultvalues' request under Docker: model")
    public void testValidateRemoteTypeSpecificRetrievalCachePeriod() {
        InternalArtifactoryContext context = mock(InternalArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(false);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        DockerTypeSpecificConfigModel dockerTypeSpecificConfigModel = new DockerTypeSpecificConfigModel();
        dockerTypeSpecificConfigModel.setRetrievalCachePeriodSecs(null);

        dockerTypeSpecificConfigModel.validateRemoteTypeSpecific();

        assertEquals((long) dockerTypeSpecificConfigModel.getRetrievalCachePeriodSecs(),
                DEFAULT_DOCKER_REMOTE_RETRIEVAL_CACHE_PERIOD);
    }

    @Test(dataProvider = "isBlockPushingSchema1Provider")
    public void isBlockPushingSchema1(org.artifactory.repo.packagetype.DockerApiVersion dockerApiVersion,
            boolean blockPushingSchema1, boolean expectedResult) {
        InternalArtifactoryContext context = mock(InternalArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(false);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        DockerTypeSpecificConfigModel dockerTypeSpecificConfigModel = new DockerTypeSpecificConfigModel();
        dockerTypeSpecificConfigModel.setBlockPushingSchema1(blockPushingSchema1);
        dockerTypeSpecificConfigModel.setDockerApiVersion(dockerApiVersion);
        assertEquals(dockerTypeSpecificConfigModel.isBlockPushingSchema1(), expectedResult);
    }

    @DataProvider
    public static Object[][] isBlockPushingSchema1Provider() {
        return new Object[][] {
                {DockerApiVersion.V1, true, false},
                {null, true, false},
                {DockerApiVersion.V2, false, false},
                {DockerApiVersion.V2, true, true},
        };
    }


    @AfterMethod
    public void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }
}