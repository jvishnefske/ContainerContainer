package org.artifactory.ui.rest.service.artifacts.search.packagesearch;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.artifactory.common.ConstantValues.forcePluginsRepositoryEnabled;
import static org.artifactory.entitlement.EntitlementType.PACKAGES_IDE_EXTENSION;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPackageSearchOptionsServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    ArtifactoryRestRequest<?> request;
    GetPackageSearchOptionsService getPackageSearchOptionsService;

    @BeforeEach
    void setup() {
        getPackageSearchOptionsService = new GetPackageSearchOptionsService();
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        AddonsManager addonsManager = mock(AddonsManager.class);
        lenient().when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        ArtifactoryContextThreadBinder.bind(context);
        lenient().when(addonsManager.isEntitled(PACKAGES_IDE_EXTENSION)).thenReturn(true);
    }

    @AfterEach
    void cleanup() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testGetAvailablePackages_excludeJem(boolean toggle) {
        setStringSystemProperty(forcePluginsRepositoryEnabled, toggle ? "true" : "false");
        when(request.getPathParamByKey("type")).thenReturn("availablePackages");
        ArtifactoryRestResponse<?> response = new ArtifactoryRestResponse<>();
        getPackageSearchOptionsService.execute(request, response);
        Assertions.assertFalse(response.getiModelList().isEmpty());
        Assertions.assertFalse(response.getiModelList().stream()
                .anyMatch(model -> StringUtils.containsIgnoreCase(model.toString(), "Jem")));
    }
}