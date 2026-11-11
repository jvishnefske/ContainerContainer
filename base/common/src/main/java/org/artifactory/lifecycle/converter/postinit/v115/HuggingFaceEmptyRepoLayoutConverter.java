package org.artifactory.lifecycle.converter.postinit.v115;

import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.huggingfaceml.HuggingFaceLayoutConverter;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.aql.AqlService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.converter.postinit.PostInitConverter;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalConfig;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.util.RepoPathUtils;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.common.ExceptionLogUtils;

import java.util.List;

import static org.artifactory.util.HuggingFaceMLPackageTypeConfigMapper.IS_ML_REPO_LAYOUT;

/**
 * @author Volodymyr Shulga
 * The purpose of this converter is to change layout for all FrogML empty repositories to the FrogML layout.
 */
@Slf4j
public class HuggingFaceEmptyRepoLayoutConverter extends PostInitConverter {
    private RepositoryService repositoryService;
    private AqlService aqlService;
    private MeterRegistry meterRegistry;
    private SecurityService securityService;

    public HuggingFaceEmptyRepoLayoutConverter(ArtifactoryServiceVersion until) {
        super(until, until);
    }

    /**
     * Any version after 7.67.0 should run this converter. HuggingFace repository type
     * was introduced in 7.67.0 according to RTDEV-33855
     */
    @Override
    public boolean isInterested(CompoundVersionDetails source, CompoundVersionDetails target) {
        return source.getVersion().afterOrEqual(ArtifactoryServiceVersionProvider.v7670.get())
                && source.getVersion().before(ArtifactoryServiceVersionProvider.v71110.get());
    }

    /**
     * Do the following:
     * 1. Get all HF local and remote repositories (without federated repos)
     * 2. Check if the repository is empty
     * 3. Run the layout migration if repository is empty
     */
    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        log.info("Start converting HuggingFace empty repos layout");
        repositoryService = ContextHelper.get().beanForType(RepositoryService.class);
        repositoryService = ContextHelper.get().beanForType(RepositoryService.class);
        aqlService = ContextHelper.get().beanForType(AqlService.class);
        securityService = ContextHelper.get().beanForType(SecurityService.class);
        meterRegistry = ContextHelper.get().beanForType(MeterRegistry.class);

        doConvert();
        log.info("Done converting HuggingFace empty repos layout");
    }

    @VisibleForTesting
    HuggingFaceLayoutConverter getHuggingFaceLayoutConverter() {
        return new HuggingFaceLayoutConverter(repositoryService, aqlService, securityService,
                meterRegistry, ConstantValues.huggingFaceMLConvertLayoutDryRun.getBoolean());
    }

    private void doConvert() {
        HuggingFaceLayoutConverter converter = getHuggingFaceLayoutConverter();
        getEmptyLocalAndRemoteHuggingFaceRepositories().forEach(
                repoKey -> {
                    try {
                        log.info("Converting layout for repository {}", repoKey);
                        converter.convert(repoKey);
                        log.info("Layout for repository {} converted", repoKey);
                    } catch (Exception e) {
                        ExceptionLogUtils.debugOrWarn(log, "Failed to convert layout for repository " + repoKey, e);
                    }
                }
        );
    }

    /**
     * Get all empty local and remote HuggingFace repositories. Ignores federated repositories.
     *
     * @return List of empty local and remote HuggingFace repositories keys
     */
    private List<String> getEmptyLocalAndRemoteHuggingFaceRepositories() {
        return repositoryService.getLocalFederatedAndCachedRepoConfigs().stream()
                .filter(config -> RepoType.HuggingFaceML == config.getPackageType())
                .filter(config -> !config.isFederated())
                .filter(config -> !Boolean.parseBoolean(config.getPackageTypeConfig().get(IS_ML_REPO_LAYOUT)))
                .filter(this::isEmpty)
                .map(this::getNonCacheRepoKey)
                .toList();
    }

    private boolean isEmpty(LocalConfig repo) {
        return !repositoryService.hasChildren(RepoPathFactory.create(repo.getKey()));
    }

    private String getNonCacheRepoKey(LocalConfig repo) {
        return RepoPathUtils.removeDashCache(repo.getKey());
    }

    @Override
    public void assertConversionPrecondition(ArtifactoryHome home, CompoundVersionDetails fromVersion,
            CompoundVersionDetails toVersion) throws ConverterPreconditionException {
        // not used
    }
}
