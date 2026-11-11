package org.artifactory.ui.rest.service.admin.configuration.trashcan;


import lombok.RequiredArgsConstructor;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.cleanup.CleanupPolicyAddon;
import org.artifactory.rest.common.model.BaseModel;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.springframework.stereotype.Component;

@Component
public class DisableTrashcanStrategyService implements RestService<Boolean> {

    private final AddonsManager addonsManager;

    public DisableTrashcanStrategyService(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Override
    public void execute(ArtifactoryRestRequest<Boolean> request, RestResponse response) {
        boolean activePolicyWithEnabledTrashcanPresent = addonsManager.addonByType(CleanupPolicyAddon.class)
                .isActivePolicyWithEnabledTrashcanPresent();
        Action action = activePolicyWithEnabledTrashcanPresent ? Action.ALERT : Action.ALLOW;
        Response model = new Response(action);
        response.iModel(model);
    }

    @RequiredArgsConstructor
    static class Response extends BaseModel {
        public final Action action;
    }

    enum Action {
        ALERT,
        ALLOW
    }
}
