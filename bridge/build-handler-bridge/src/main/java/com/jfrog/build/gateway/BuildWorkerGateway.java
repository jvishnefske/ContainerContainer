package com.jfrog.build.gateway;

import com.jfrog.build.acl.model.BuildWorkerEventType;
import com.jfrog.build.acl.model.BuildWrapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.worker.WorkersAddon;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;

@RequiredArgsConstructor
public class BuildWorkerGateway {

    private final AddonsManager addonsManager;

    public void runWorkers(
            @NonNull BuildWrapper build,
            @NonNull BuildWorkerEventType type) {
        SupportedUnifiedEvents action = SupportedUnifiedEvents.valueOf(type.name());
        addonsManager.addonByType(WorkersAddon.class).run(action, build);
    }
}
