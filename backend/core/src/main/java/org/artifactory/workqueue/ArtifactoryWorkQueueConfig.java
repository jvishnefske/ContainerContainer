package org.artifactory.workqueue;

import jakarta.annotation.Nonnull;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.maven.MavenMetadataService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.async.AsyncExecutor;
import org.artifactory.common.ConstantValues;
import org.artifactory.concurrent.ArtifactoryRunnable;
import org.artifactory.mbean.MBeanRegistrationService;
import org.artifactory.security.SystemAuthenticationToken;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.storage.fs.session.StorageSession;
import org.artifactory.storage.fs.session.StorageSessionHolder;
import org.artifactory.storage.jobs.migration.sha256.Sha256MigrationJob;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static com.jfrog.build.acl.util.BuildConstants.BUILD_RETENTION_JOB_WQ_NAME;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Configuration
public class ArtifactoryWorkQueueConfig implements ContextCreationListener {

    private final WorkQueueConflictsGuardProvider defaultConflictsGuardProvider;

    public ArtifactoryWorkQueueConfig(@Nonnull AddonsManager addonsManager) {
        this.defaultConflictsGuardProvider =
                (key, info) -> addonsManager.addonByType(HaAddon.class).getConflictsGuard(key);
    }

    @Bean
    public WorkQueueSessionService workQueueSessionService() {
        return callback -> {
            StorageSession session = StorageSessionHolder.getSession();
            if (session != null) {
                session.getOrCreateResource(WorkQueueSessionResource.class).addCallback(callback);
                return true;
            } else {
                return false;
            }
        };
    }

    @Bean
    public AsyncWorkQueueService workQueueService(@Nonnull List<WorkQueueInfo> workQueueInfos,
            @Nonnull AsyncExecutor defaultExecutor,
            @Nonnull List<RemoveWorkQueuePredicate> removeWorkQueuePredicates,
            @Nonnull MBeanRegistrationService mBeanRegistrationService,
            @Nonnull WorkQueueSessionService workQueueSessionService,
            @Nonnull List<WorkQueueCustomizer<?>> workQueueCustomizers) {
        return new AsyncWorkQueueServiceImpl(workQueueSessionService, workQueueInfos, defaultExecutor,
                removeWorkQueuePredicates,
                mBeanRegistrationService,
                ConstantValues.workQueueMaxExecutionSec.getInt(),
                ConstantValues.workQueueSyncExecutionTimeoutSec.getInt(),
                ConstantValues.workQueueJobEnabled.getBoolean() ? ConstantValues.workQueueDoJobIntervalSecs.getInt() :
                        0, workQueueCustomizers, true);
    }

    @Override
    public void onContextCreated() {
        ArtifactoryContext context = ContextHelper.get();
        ContextHelper.get().beanForType(AsyncWorkQueueService.class).scheduleWorkQueueCleanupJob(
                delegate -> new ArtifactoryRunnable(delegate, context, new SystemAuthenticationToken()));
    }

    @Bean
    public WorkQueueInfo mavenMetadataWorkQueueInfo() {
        return new WorkQueueInfo(ConstantValues.mvnMetadataCalculationWorkers.getInt(),
                MavenMetadataService.MAVEN_METADATA_WQ_NAME, defaultConflictsGuardProvider);
    }

    @Bean
    public WorkQueueInfo mavenPluginsMetadataWorkQueueInfo() {
        return new WorkQueueInfo(ConstantValues.mvnMetadataPluginCalculationWorkers.getInt(),
                MavenMetadataService.MAVEN_PLUGIN_METADATA_WQ_NAME, defaultConflictsGuardProvider);
    }

    @Bean
    public WorkQueueInfo buildRetentionWorkQueueInfo() {
        return new WorkQueueInfo(ConstantValues.buildRetentionWorkers.getInt(),
                BUILD_RETENTION_JOB_WQ_NAME, defaultConflictsGuardProvider);
    }

    @Bean
    public WorkQueueInfo sha2MigrationWorkQueueInfo() {
        return new WorkQueueInfo(ConstantValues.sha2MigrationJobQueueWorkers.getInt(),
                Sha256MigrationJob.WORK_QUEUE_NAME, defaultConflictsGuardProvider);
    }

    @Bean
    public WorkQueueInfo deletePathItemWorkQueueInfo() {
        return new WorkQueueInfo(ConstantValues.asyncDeleteWorkers.getInt(),
                RepositoryService.ASYNC_DELETE_PATH_ITEM_WQ_NAME, defaultConflictsGuardProvider);
    }
}
