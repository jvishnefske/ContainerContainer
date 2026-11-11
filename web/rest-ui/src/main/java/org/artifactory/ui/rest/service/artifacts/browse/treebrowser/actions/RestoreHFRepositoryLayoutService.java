package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions;

import com.google.common.annotations.VisibleForTesting;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.huggingfaceml.HuggingFaceLayoutRestore;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.common.model.restore.RestoreLayoutRepo;

/**
 * @author shahafg
 */
@Slf4j
public class RestoreHFRepositoryLayoutService extends HFBaseLayoutService<RestoreLayoutRepo> {

    private static final String HF_RESTORE_GUARD = "hf_restore_layout_guard";

    public RestoreHFRepositoryLayoutService(RepositoryService repositoryService,
            AddonsManager addonsManager, SecurityService securityService) {
        super(repositoryService, securityService, addonsManager);
    }

    @Override
    protected String extractRepoKey(@NonNull RestoreLayoutRepo model) {
        return model.repoKey();
    }

    @Override
    protected String performMigrationOrRestore(String repoKey) throws Exception {
        HuggingFaceLayoutRestore huggingFaceLayoutRestore = createHuggingFaceLayoutRestore();
        return huggingFaceLayoutRestore.restore(repoKey);
    }

    @Override
    protected String getOperationName() {
        return "restore";
    }

    @Override
    protected String getGuardName() {
        return HF_RESTORE_GUARD;
    }

    @VisibleForTesting
    HuggingFaceLayoutRestore createHuggingFaceLayoutRestore() {
        return new HuggingFaceLayoutRestore(repositoryService, securityService, false);
    }
}
