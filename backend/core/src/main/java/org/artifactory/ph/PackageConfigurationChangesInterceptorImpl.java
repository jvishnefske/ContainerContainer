package org.artifactory.ph;

import com.jfrog.ph.acl.listener.config.RepoConfigChangeType;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.repo.RepoInfo;
import org.artifactory.config.ConfigurationChangesInterceptor;
import com.jfrog.ph.acl.listener.config.PackageConfigChangedEventListener;
import com.jfrog.ph.acl.listener.config.PackageConfigChangedEventType;
import com.jfrog.ph.acl.listener.config.PackageRepoConfigListenerContext;
import com.jfrog.ph.acl.service.PackageRepoService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static com.jfrog.ph.acl.listener.config.PackageConfigChangedEventType.REPO_UPDATE;
import static org.artifactory.util.PackageTypeConfigFields.ENCRYPT_STATES;

@Slf4j
public class PackageConfigurationChangesInterceptorImpl implements ConfigurationChangesInterceptor,
        RepoConfigChangeInterceptor {

    private final PackageConfigChangedEventListener packageConfigChangedEventListener;

    private final PackageRepoService packageRepoService;

    public PackageConfigurationChangesInterceptorImpl(
            PackageConfigChangedEventListener packageConfigChangedEventListener,
            PackageRepoService packageRepoService) {
        this.packageConfigChangedEventListener = packageConfigChangedEventListener;
        this.packageRepoService = packageRepoService;
    }

    @Override
    public void onAfterSave(CentralConfigDescriptor newDescriptor, CentralConfigDescriptor oldDescriptor) {
        for (PackageConfigChangedEventType packageConfigChangedEventType : PackageConfigChangedEventType.values()) {
            try {
                PackageConfigChangeType packageConfigChangeType =
                        PackageConfigChangeType.valueOf(packageConfigChangedEventType.name());
                if (packageConfigChangeType.isChanged(newDescriptor, oldDescriptor)) {
                    packageConfigChangedEventListener.onGlobalConfigChange(packageConfigChangedEventType);
                }
            } catch (IllegalArgumentException e) {
                log.debug("Skipping on {} - not found", packageConfigChangedEventType.name());
            }
        }
    }

    @Override
    public void onAfterSave(@NonNull Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges) {
        for (Map.Entry<RepoDetailsType, List<RepoConfigChange>> packageConfigChangedEventType :
                repoConfigChanges.entrySet()) {
            List<RepoConfigChange> repoChangeList = packageConfigChangedEventType.getValue();
            for (RepoConfigChange repo : repoChangeList) {
                try {
                    if (repo.getConfigAction() != null) {
                        PackageConfigChangedEventType eventType = PackageConfigChangedEventType.getEventType(
                                repo.getConfigAction().name());
                        if (eventType != null) {
                            EnumSet<RepoConfigChangeType> configChangeTypes = getRepoConfigChangeTypes(repo, eventType);
                            RepoInfo repoInfo = packageRepoService.getRepoInfo(repo.getRepoKey());
                            PackageRepoConfigListenerContext context = new PackageRepoConfigListenerContext(
                                    repo.getRepoKey(), repoInfo, eventType, configChangeTypes);
                            packageConfigChangedEventListener.onRepoConfigChange(context);
                        }
                    }
                } catch (Exception e) {
                    log.info("Skipping on {} - not found, {}", repo.getRepoKey(), e.getMessage(), e);
                }
            }
        }
    }

    private static EnumSet<RepoConfigChangeType> getRepoConfigChangeTypes(RepoConfigChange repo,
            PackageConfigChangedEventType eventType) {
        EnumSet<RepoConfigChangeType> repoConfigChanges = EnumSet.noneOf(RepoConfigChangeType.class);
        if (isRemoteUrlChanged(repo, eventType)) {
            repoConfigChanges.add(RepoConfigChangeType.REMOTE_URL);
        }
        if (isTerraformEncryptStatesChanged(repo, eventType)) {
            repoConfigChanges.add(RepoConfigChangeType.TERRAFORM_BACKEND_ENCRYPT_STATE);
        }
        return repoConfigChanges;
    }

    private static boolean isRemoteUrlChanged(RepoConfigChange repo, PackageConfigChangedEventType eventType) {
        return REPO_UPDATE == eventType &&
                (repo.getOldRepoConfig() instanceof RemoteRepoConfig oldRemoteRepoConfig &&
                        repo.getNewRepoConfig() instanceof RemoteRepoConfig newRemoteRepoConfig &&
                        (!oldRemoteRepoConfig.getRepoTypeConfig().getUrl()
                                .equals(newRemoteRepoConfig.getRepoTypeConfig().getUrl())));
    }

    private static boolean isTerraformEncryptStatesChanged(RepoConfigChange repo,
            PackageConfigChangedEventType eventType) {
        RepoConfig oldRepoConfig = repo.getOldRepoConfig();
        RepoConfig newRepoConfig = repo.getNewRepoConfig();
        return REPO_UPDATE == eventType &&
                (oldRepoConfig != null && newRepoConfig != null &&
                        RepoType.TerraformBackend.equals(newRepoConfig.getPackageType()) &&
                        oldRepoConfig.getPackageTypeConfig().get(ENCRYPT_STATES.getKey()) != null &&
                        !oldRepoConfig.getPackageTypeConfig().get(ENCRYPT_STATES.getKey())
                                .equals(newRepoConfig.getPackageTypeConfig().get(ENCRYPT_STATES.getKey())));
    }
}
