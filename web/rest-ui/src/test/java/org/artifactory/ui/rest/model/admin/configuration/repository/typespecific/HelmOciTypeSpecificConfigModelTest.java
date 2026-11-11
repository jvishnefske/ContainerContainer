package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.when;


class HelmOciTypeSpecificConfigModelTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    ArtifactoryContext artifactoryContext;
    AutoCloseable closeable;

    @BeforeEach
    void setup() {
        closeable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryContext.beanForType(AolService.class)).thenReturn(Mockito.mock(AolService.class));
    }

    @AfterEach
    void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        closeable.close();
    }

    @Test
    void getRepoType() {
        Assertions.assertEquals(RepoType.HelmOCI, new HelmOciTypeSpecificConfigModel().getRepoType());
    }

    @Test
    void isBlockPushingSchema1() {
        Assertions.assertTrue(new HelmOciTypeSpecificConfigModel().isBlockPushingSchema1());
    }
}