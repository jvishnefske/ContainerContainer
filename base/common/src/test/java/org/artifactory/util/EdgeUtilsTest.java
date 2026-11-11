package org.artifactory.util;

import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.entitlement.EntitlementType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Hussein Alshqerat
 */
class EdgeUtilsTest {

    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private AddonsManager addonsManager;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        autoCloseable = openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(ContextHelper.get().beanForType(AddonsManager.class)).thenReturn(addonsManager);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testIsEdgeLicensed(boolean isEdge) {
        when(addonsManager.isEntitled(EntitlementType.EDGE)).thenReturn(isEdge);
        boolean isEdgeLicensed = EdgeUtils.isEdgeLicensed();
        assertEquals(isEdge, isEdgeLicensed);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }
}