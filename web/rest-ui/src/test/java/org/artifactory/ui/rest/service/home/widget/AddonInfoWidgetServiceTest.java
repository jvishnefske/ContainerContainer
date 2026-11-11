package org.artifactory.ui.rest.service.home.widget;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.home.HomeWidgetModel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddonInfoWidgetServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    ArtifactoryContext context;
    @Mock
    AddonsManager addonsManager;
    @InjectMocks
    AddonInfoWidgetService addonInfoWidgetService;

    @BeforeEach
    void setup() {
        AolService aolService = mock(AolService.class);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        when(aolService.isAol()).thenReturn(false);
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.getArtifactoryRunningMode()).thenReturn(ArtifactoryRunningMode.PRO);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testJemNotExists(boolean enableJemRepo) {
        boolean jemEnableOriginValue =
                getOrCreateArtifactoryHomeStub().getArtifactoryProperties()
                        .getBooleanProperty(ConstantValues.jemRepositoryEnabled);
        try {
            setStringSystemProperty(ConstantValues.jemRepositoryEnabled, String.valueOf(enableJemRepo));
            ArtifactoryRestResponse<HomeWidgetModel> response = new ArtifactoryRestResponse<>();
            addonInfoWidgetService.execute(null, response);
            HomeWidgetModel model = response.getIModel();
            Assertions.assertNotNull(model);
            Assertions.assertTrue(model.getWidgetData().get("addons") instanceof List);
            Assertions.assertEquals(enableJemRepo, ((List<?>) model.getWidgetData().get("addons")).stream()
                    .anyMatch(addon -> addon.toString().contains("jem")));
        } finally {
            setStringSystemProperty(ConstantValues.jemRepositoryEnabled, String.valueOf(jemEnableOriginValue));
        }
    }
}