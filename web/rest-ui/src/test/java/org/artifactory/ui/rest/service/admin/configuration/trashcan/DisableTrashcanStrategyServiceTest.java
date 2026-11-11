package org.artifactory.ui.rest.service.admin.configuration.trashcan;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.cleanup.CleanupPolicyAddon;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DisableTrashcanStrategyServiceTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void execute(boolean value) {
        CleanupPolicyAddon addon = mock(CleanupPolicyAddon.class);
        when(addon.isActivePolicyWithEnabledTrashcanPresent()).thenReturn(value);
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(CleanupPolicyAddon.class)).thenReturn(addon);

        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse<>();

        new DisableTrashcanStrategyService(addonsManager).execute(request, response);
        DisableTrashcanStrategyService.Response model =
                (DisableTrashcanStrategyService.Response) response.getIModel();

        assertEquals(value, model.action.equals(DisableTrashcanStrategyService.Action.ALERT));
    }

}