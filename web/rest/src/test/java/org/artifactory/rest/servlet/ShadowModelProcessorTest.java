package org.artifactory.rest.servlet;

import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShadowModelProcessorTest extends ArtifactoryHomeBoundJUnitTest {

    ShadowModelProcessor shadowModelProcessor;

    @BeforeEach
    void setUp() {
        shadowModelProcessor = new ShadowModelProcessor();

        // Enable shadowing
        ArtifactoryHome home = Mockito.spy(getOrCreateArtifactoryHomeStub());
        ArtifactorySystemProperties systemProperties = Mockito.mock(ArtifactorySystemProperties.class);
        when(systemProperties.getBooleanProperty(ConstantValues.shadowingEnabled)).thenReturn(true);
        when(home.getArtifactoryProperties()).thenReturn(systemProperties);
        ArtifactoryHome.bind(home);
    }

    @AfterEach
    void tearDown() {
        ArtifactoryHome.unbind();
    }

    @Test
    void processResourceModel() {
        Resource.Builder builder1 = Resource.builder().path("");
        builder1.addChildResource(Resource.builder().path("{packageType}/{context}").build());
        Resource resource1 = builder1.build();
        Resource resource2 = Resource.builder().path("api/vcs").build();
        ResourceModel resourceModel = mock(ResourceModel.class);
        when(resourceModel.getResources()).thenReturn(Arrays.asList(resource1, resource2));
        ResourceModel updatedModel = shadowModelProcessor.processResourceModel(resourceModel, null);

        assertEquals(2, updatedModel.getResources().size());
        assertEquals("", updatedModel.getResources().get(0).getPath());
        assertEquals("api/vcs", updatedModel.getResources().get(1).getPath());
        assertEquals(2, updatedModel.getResources().get(0).getChildResources().size());
        assertEquals("{packageType}/{context}",
                updatedModel.getResources().get(0).getChildResources().get(0).getPath());
        assertEquals("shadow/{packageType}/{context}",
                updatedModel.getResources().get(0).getChildResources().get(1).getPath());
    }
}