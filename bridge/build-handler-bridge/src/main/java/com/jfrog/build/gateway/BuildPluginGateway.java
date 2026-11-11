package com.jfrog.build.gateway;

import com.jfrog.build.acl.model.BuildPluginType;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.plugin.PluginAction;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.addon.plugin.build.AfterBuildSaveAction;
import org.artifactory.addon.plugin.build.BeforeBuildSaveAction;
import org.artifactory.build.Builds;
import org.artifactory.build.DetailedBuildRunImpl;
import org.jfrog.build.api.Build;

import static java.util.Objects.nonNull;

@RequiredArgsConstructor
public class BuildPluginGateway {

    private final Builds builds;
    private final AddonsManager addonsManager;

    public void runPluginInterceptor(
            @NonNull BuildPluginType type,
            @NonNull Build build,
            @NonNull String buildRepo) {
        DetailedBuildRunImpl buildRun = new DetailedBuildRunImpl(build, buildRepo);
        switch (type) {
            case BEFORE_CREATE -> runInterceptor(buildRun, BeforeBuildSaveAction.class);
            case AFTER_CREATE -> runInterceptor(buildRun, AfterBuildSaveAction.class);
        }
    }

    private void runInterceptor(
            @NonNull DetailedBuildRunImpl buildRun,
            @NonNull Class<? extends PluginAction> interceptor) {
        if (nonNull(builds)) {
            PluginsAddon pluginsAddon = addonsManager.addonByType(PluginsAddon.class);
            pluginsAddon.execPluginActions(interceptor, builds, buildRun);
        }
    }
}
