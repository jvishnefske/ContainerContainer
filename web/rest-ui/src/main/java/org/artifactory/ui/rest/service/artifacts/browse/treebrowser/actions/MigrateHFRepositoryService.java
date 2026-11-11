package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions;

import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.huggingfaceml.HuggingFaceLayoutConverter;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.aql.AqlService;
import org.artifactory.rest.common.model.migrate.MigrateRepo;

/**
 * @author Volodymyr Shulga
 */
@Slf4j
public class MigrateHFRepositoryService extends HFBaseLayoutService<MigrateRepo> {
    private static final String HF_MIGRATE_GUARD = "hf_migrate_layout_guard";

    private final AqlService aqlService;
    private final MeterRegistry meterRegistry;

    public MigrateHFRepositoryService(RepositoryService repositoryService,
            AqlService aqlService, AddonsManager addonsManager, SecurityService securityService,
            MeterRegistry meterRegistry) {
        super(repositoryService, securityService, addonsManager);
        this.aqlService = aqlService;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected String extractRepoKey(@NonNull MigrateRepo model) {
        return model.repoKey();
    }

    @Override
    protected String performMigrationOrRestore(String repoKey) {
        HuggingFaceLayoutConverter huggingFaceLayoutConverter = createHuggingFaceLayoutConverter();
        return huggingFaceLayoutConverter.convert(repoKey);
    }

    @Override
    protected String getOperationName() {
        return "migration";
    }

    @Override
    protected String getGuardName() {
        return HF_MIGRATE_GUARD;
    }

    @VisibleForTesting
    HuggingFaceLayoutConverter createHuggingFaceLayoutConverter() {
        return new HuggingFaceLayoutConverter(repositoryService, aqlService, securityService,
                meterRegistry, false);
    }
}
