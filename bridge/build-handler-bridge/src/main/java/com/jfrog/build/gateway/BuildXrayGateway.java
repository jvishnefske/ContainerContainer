package com.jfrog.build.gateway;

import com.jfrog.build.acl.model.BuildXrayEvent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.xray.XrayAddon;

@RequiredArgsConstructor
public class BuildXrayGateway {

    private final AddonsManager addonsManager;

    public void runXrayInterceptor(
            @NonNull BuildXrayEvent type,
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull Long buildStarted,
            @NonNull String buildRepo) {
        XrayAddon xrayAddon = addonsManager.addonByType(XrayAddon.class);
        switch (type) {
            case CREATE_BUILD -> xrayAddon.callAddBuildInterceptor(buildName, buildNumber, buildStarted, buildRepo);
            case DELETE_BUILD -> xrayAddon.callDeleteBuildInterceptor(buildName, buildNumber, buildStarted, buildRepo);
        }
    }
}
