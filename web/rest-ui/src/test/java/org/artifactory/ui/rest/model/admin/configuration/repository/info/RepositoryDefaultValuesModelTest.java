package org.artifactory.ui.rest.model.admin.configuration.repository.info;

import org.artifactory.addon.AddonsManager;
import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.artifactory.entitlement.EntitlementType.PACKAGES_IDE_EXTENSION;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class RepositoryDefaultValuesModelTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    ArtifactoryContext context;

    @BeforeEach
    void setup() {
        AolService aolService = mock(AolService.class);
        AddonsManager addonsManager = mock(AddonsManager.class);
        lenient().when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        lenient().when(context.beanForType(AolService.class)).thenReturn(aolService);
        lenient().when(aolService.isAol()).thenReturn(false);
        ArtifactoryContextThreadBinder.bind(context);
        lenient().when(addonsManager.isEntitled(PACKAGES_IDE_EXTENSION)).thenReturn(true);
    }

    @AfterEach
    void cleanup() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getDefaultModels(boolean toggle) {
        boolean jemEnableOriginValue =
                getOrCreateArtifactoryHomeStub().getArtifactoryProperties()
                        .getBooleanProperty(ConstantValues.jemRepositoryEnabled);
        try {
            setStringSystemProperty(ConstantValues.jemRepositoryEnabled, String.valueOf(toggle));
            setStringSystemProperty(ConstantValues.forcePluginsRepositoryEnabled, String.valueOf(toggle));
            RepositoryDefaultValuesModel repositoryDefaultValuesModel = new RepositoryDefaultValuesModel();
            Assertions.assertEquals(toggle, repositoryDefaultValuesModel.getDefaultModels().get("jem") != null);
        } finally {
            setStringSystemProperty(ConstantValues.jemRepositoryEnabled, String.valueOf(jemEnableOriginValue));
        }
    }

    @ParameterizedTest
    @MethodSource("defaultModelParams")
    void getDefaultModelParameterized(ConstantValues constantValue, String modelKey) {
        boolean beforeValue = getOrCreateArtifactoryHomeStub()
                .getArtifactoryProperties()
                .getBooleanProperty(constantValue);
        try {
            // Test with feature enabled
            setStringSystemProperty(constantValue, "true");
            RepositoryDefaultValuesModel enabledModel = new RepositoryDefaultValuesModel();
            Assertions.assertTrue(enabledModel.getDefaultModels().get(modelKey) != null,
                    modelKey + " should be present when feature is enabled");

            // Test with feature disabled
            setStringSystemProperty(constantValue, "false");
            RepositoryDefaultValuesModel disabledModel = new RepositoryDefaultValuesModel();
            Assertions.assertNull(disabledModel.getDefaultModels().get(modelKey),
                    modelKey + " should be absent when feature is disabled");
        } finally {
            setStringSystemProperty(constantValue, String.valueOf(beforeValue));
        }
    }

    private static Stream<Arguments> defaultModelParams() {
        return java.util.stream.Stream.of(
                Arguments.of(ConstantValues.aieditorExtensionsRepositoryEnabled, "aieditorextensions"),
                Arguments.of(ConstantValues.jetbrainsPluginsRepositoryEnabled, "jetbrainsplugins"),
                Arguments.of(ConstantValues.bazelModulesRepositoryEnabled, "bazelmodules")
        );
    }
}