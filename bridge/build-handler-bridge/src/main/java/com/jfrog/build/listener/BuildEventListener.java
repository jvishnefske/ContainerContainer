package com.jfrog.build.listener;

import com.jfrog.build.acl.bridge.BuildBridgeFromArtifactory;
import com.jfrog.build.converter.BuildBackupConverter;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.repo.RepoConfigChangesReload;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.spring.Reloadable;
import org.artifactory.spring.ReloadableBean;
import org.artifactory.spring.RepoConfigChangeListener;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.common.config.diff.DataDiff;

import java.util.List;
import java.util.Objects;

import static java.util.Objects.nonNull;
import static org.artifactory.config.CentralConfigKey.backups;

@Slf4j
@RequiredArgsConstructor
@Reloadable(beanClass = BuildEventListener.class, initAfter = {InternalRepositoryService.class})
public class BuildEventListener implements ReloadableBean, RepoConfigChangeListener {

    private final BuildBridgeFromArtifactory bridgeFromArtifactory;

    private final InternalRepositoryService repositoryService;

    @Override
    public void init() {
        bridgeFromArtifactory.init();
    }

    @Override
    public void reload(
            @Nullable CentralConfigDescriptor oldDescriptor,
            @Nullable List<DataDiff<?>> configDiff) {
        bridgeFromArtifactory.reload();
        if (isConversionRequired(configDiff)) {
            convert(null, null);
        }
    }

    @Override
    public void onRepoConfigReload(
            @Nullable RepoConfigChangesReload repoConfigChangesReload) {
        bridgeFromArtifactory.onRepoConfigReload();
    }

    @Override
    public void convert(
            @Nullable CompoundVersionDetails source,
            @Nullable CompoundVersionDetails target) {
        if (ArtifactoryHome.get().getCreateBackupExcludedBuildNames().exists()) {
            log.debug("Found marker file, executing conversion");
            ArtifactoryContext context = ContextHelper.get();
            CentralConfigService centralConfigService = context.beanForType(CentralConfigService.class);
            SecurityService securityService = context.beanForType(SecurityService.class);
            new BuildBackupConverter(centralConfigService, repositoryService, securityService).convert();
        }
    }

    private boolean isConversionRequired(
            @Nullable List<DataDiff<?>> configDiff) {
        return nonNull(configDiff) && configDiff.stream()
                .filter(Objects::nonNull)
                .anyMatch(diff -> diff.getFieldName().contains(backups.getKey()));
    }
}
