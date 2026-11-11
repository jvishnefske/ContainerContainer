package org.artifactory.addon.huggingfaceml;

import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.huggingfaceml.HuggingFaceLayoutConvertResult.HuggingFaceLayoutConvertResultBuilder;
import org.artifactory.addon.huggingfaceml.HuggingFaceLayoutConvertResult.HuggingFaceLayoutConvertStatus;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.model.AqlItemTypeEnum;
import org.artifactory.aql.result.rows.AqlItem;
import org.artifactory.aql.util.AqlUtils;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.model.RepoConfig;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;

import java.io.ByteArrayInputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicLong;

import static org.artifactory.addon.huggingfaceml.HuggingFacePathUtil.getFileType;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.repo;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.type;
import static org.artifactory.aql.api.internal.AqlBase.and;
import static org.artifactory.util.HuggingFaceMLPackageTypeConfigMapper.IS_ML_REPO_LAYOUT;

/**
 * @author Volodymyr Shulga
 */
@Slf4j
public class HuggingFaceLayoutConverter {
    static final String HF_ENTITY_IS_MIGRATED = "huggingfaceml.migrated.entity";
    static final String BACKUP_DIR = ".jfrog/old-layout";
    private static final String HF_ENTITY_IS_MIGRATED_VALUE = "true";
    private final RepositoryService repositoryService;
    private final AqlService aqlService;
    private final MeterRegistry meterRegistry;
    private final SecurityService securityService;
    private final boolean dryRun;

    public HuggingFaceLayoutConverter(RepositoryService repositoryService, AqlService aqlService,
            SecurityService securityService, MeterRegistry meterRegistry, boolean dryRun) {
        this.repositoryService = repositoryService;
        this.aqlService = aqlService;
        this.securityService = securityService;
        this.meterRegistry = meterRegistry;
        this.dryRun = dryRun;
    }

    /**
     * Convert paths from HF layout to ML layout.
     * Will be executed only for HF repositories that have isMLLayout set to false
     * which means the HF repo hasn't been converted to ML layout yet
     *
     * @param repoKey repository key to run conversion for. It should be HuggingFaceML type local/remote repo.
     */
    public String convert(@Nonnull String repoKey) {
        HuggingFaceLayoutConvertResult result;
        try {
            result = securityService.callAsSystem(() -> {
                log.info("Converting paths for repo {} with dryRun {}", repoKey, dryRun);
                HuggingFaceLayoutConvertResultBuilder resultBuilder;
                String validationResult = validateRepoForLayoutConvert(repoKey);
                if (validationResult != null) {
                    resultBuilder = HuggingFaceLayoutConvertResult.builder()
                            .repoKey(repoKey).status(HuggingFaceLayoutConvertStatus.FAILED).message(validationResult);
                } else {
                    resultBuilder = performConversion(repoKey);
                }
                return resultBuilder.build();
            });
        } catch (Exception e) {
            String message = String.format("Failed to convert paths for repo %s with dryRun %s", repoKey, dryRun);
            log.error(message, e);
            result = HuggingFaceLayoutConvertResult.builder()
                    .repoKey(repoKey)
                    .status(HuggingFaceLayoutConvertStatus.FAILED)
                    .message(message)
                    .build();
        }
        if (!dryRun) {
            reportConversionToGrafana(result);
        }
        return result.getMessage();
    }

    private HuggingFaceLayoutConvertResultBuilder performConversion(@Nonnull String repoKey) {
        String message;
        HuggingFaceLayoutConvertResultBuilder resultBuilder =  HuggingFaceLayoutConvertResult.builder()
                .repoKey(repoKey);
        long startTime = System.currentTimeMillis();
        AtomicLong convertedArtifactsCount = new AtomicLong();
        final RepoConfig repoConfig = repositoryService.getRepoConfigByKey(repoKey);
        String repoKeyForConversion =
                repoConfig.getRepoType() == RepoDetailsType.REMOTE ? repoKey + "-cache" : repoKey;
        if (!dryRun) {
            createBackupForRepoContent(repoKeyForConversion);
        }
        processRepoFiles(repoKeyForConversion, convertedArtifactsCount);
        updateLayoutToML(repoConfig);
        message = String.format("Conversion for repo %s %s", repoKey,
                dryRun ? "done in dry run mode" : "done");
        log.info(message);
        resultBuilder.duration(System.currentTimeMillis() - startTime);
        resultBuilder.convertedArtifactsCount(convertedArtifactsCount.get());
        resultBuilder.status(HuggingFaceLayoutConvertStatus.SUCCESS);
        resultBuilder.message(message);
        return resultBuilder;
    }

    /**
     * Backup all files from the repo to .jfrog/old-layout
     */
    void createBackupForRepoContent(String repoKeyForConversion) {
        // backup all files from the repo to .jfrog/old-layout
        log.info("Backing up files from repo {} to {}", repoKeyForConversion, BACKUP_DIR);
        RepoPath sourcePath = RepoPathFactory.create(repoKeyForConversion);
        if (repositoryService.hasChildren(sourcePath)) {
            RepoPath backupPath = RepoPathFactory.create(repoKeyForConversion, BACKUP_DIR);
            if (repoKeyForConversion.endsWith("-cache")) {
                repositoryService.copyToCache(sourcePath, backupPath, false, true, true, false, null, false);
            } else {
                repositoryService.copy(sourcePath, backupPath);
            }
        }
        log.info("Backup for repo {} done", repoKeyForConversion);
    }

    private void processRepoFiles(String repoKeyForConversion, AtomicLong counter) {
        AqlApiItem allRepoFiles = AqlApiItem.create()
                .filter(and(repo().equal(repoKeyForConversion),
                        type().equal(AqlItemTypeEnum.file.signature)));
        aqlService.executeQueryEager(allRepoFiles).getResults()
                .forEach(row -> {
                    if (convertPathToMLLayout(row)) {
                        counter.incrementAndGet();
                    }
                });
    }

    private void updateLayoutToML(RepoConfig repoConfig) {
        if (!dryRun) {
            // repoConfig is not null because we checked it in isValidRepoForLayoutConvert.
            repoConfig.getPackageTypeConfig().put(IS_ML_REPO_LAYOUT, "true");
            repositoryService.updateRepoConfigWithoutChecks(repoConfig);
        }
    }

    @VisibleForTesting
    void reportConversionToGrafana(HuggingFaceLayoutConvertResult result) {
        Counter.builder("huggingface.layout.converter.calls")
                .description("Total number of HF layout converter calls")
                .tag("repoKey", result.getRepoKey())
                .tag("status", result.getStatus().name())
                .register(meterRegistry)
                .increment();
        DistributionSummary.builder("huggingface.layout.converter.converted_artifacts_count")
                .description("Number of artifacts converted per call")
                .tag("repoKey", result.getRepoKey())
                .tag("status", result.getStatus().name())
                .register(meterRegistry)
                .record(result.getConvertedArtifactsCount());
        DistributionSummary.builder("huggingface.layout.converter.duration")
                .description("Duration of conversion per call")
                .tag("repoKey", result.getRepoKey())
                .tag("status", result.getStatus().name())
                .register(meterRegistry)
                .record(result.getDuration());
    }

    private boolean convertPathToMLLayout(AqlItem row) {
        RepoPath repoPathToFix = AqlUtils.fromAql(row);
        log.debug("convertPathToMLLayout path {}", repoPathToFix);
        final String fixedPath = HuggingFacePathUtil.convertPathToNewLayout(repoPathToFix.getPath());
        if (fixedPath == null) {
            log.info("convertPathToMLLayout. Path {} doesn't match old layout", repoPathToFix);
            return false;
        }
        MoveMultiStatusHolder moveStatus = null;
        if (dryRun) {
            log.debug("convertPathToMLLayout. Would move {} to {}", repoPathToFix, fixedPath);
        } else {
            log.info("convertPathToMLLayout. Moving {} to {}", repoPathToFix, fixedPath);

            Properties properties = null;
            // The property of migrated should be only on the leadFile
            if (getFileType(fixedPath) == FileType.LEAD_FILE) {
                properties = new PropertiesImpl();
                properties.put(HF_ENTITY_IS_MIGRATED, HF_ENTITY_IS_MIGRATED_VALUE);
            }
            moveStatus = repositoryService.move(repoPathToFix, RepoPathFactory.create(repoPathToFix.getRepoKey(),
                    fixedPath), false, true, true, true, true, properties, false);
        }
        log.debug("Processed path {}", repoPathToFix);
        return moveStatus != null && !moveStatus.hasErrors();
    }

    @VisibleForTesting
    String validateRepoForLayoutConvert(String repoKey) {
        final RepoConfig repoConfig = repositoryService.getRepoConfigByKey(repoKey);
        String error = null;
        if (repoConfig == null) {
            error = String.format("Repository %s descriptor not found", repoKey);
        } else if (!RepoType.HuggingFaceML.equals(repoConfig.getPackageType())) {
            error = String.format("Repository %s is not HuggingFaceML type", repoKey);
        } else if ("true".equalsIgnoreCase(repoConfig.getPackageTypeConfig().get(IS_ML_REPO_LAYOUT))) {
            error = String.format("Repository %s is already using new layout", repoKey);
        }
        if (error != null) {
            log.warn(error);
        }
        return error;
    }

    @Nonnull
    @VisibleForTesting
    @SuppressWarnings("java:S4790")// suppress warning about using weak hash algorithm
    static DigestInputStream createDigest(@Nonnull String content) throws NoSuchAlgorithmException {
        return new DigestInputStream(new ByteArrayInputStream(content.getBytes()), MessageDigest.getInstance("SHA-1"));
    }
}
