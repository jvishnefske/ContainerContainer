package org.artifactory.ph;

import com.jfrog.ph.acl.exception.PackageForbiddenException;
import com.jfrog.ph.acl.service.PackageRepoIndexCalculatorService;
import com.jfrog.ph.acl.service.PackageRepoService;
import com.jfrog.ph.acl.service.PackageSpecificationAdapter;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.repo.PackageType;
import org.artifactory.ph.selector.IndexingSelector;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;

/**
 * The purpose of this class is to provide an option to trigger indexing operations
 */

@Slf4j
public class PackageIndexingSelectorImpl implements IndexingSelector {
    private final PackageRepoService packageRepoService;
    private final PackageSpecificationAdapter packageSpecification;
    private final AddonsManager addonsManager;
    private final PackageRepoIndexCalculatorService packageRepoIndexCalculatorService;
    private final AuthorizationService authorizationService;

    public PackageIndexingSelectorImpl(PackageRepoService packageRepoService,
            PackageSpecificationAdapter packageSpecification, AddonsManager addonsManager,
            PackageRepoIndexCalculatorService packageRepoIndexCalculatorService,
            AuthorizationService authorizationService) {
        this.packageRepoService = packageRepoService;
        this.packageSpecification = packageSpecification;
        this.addonsManager = addonsManager;
        this.packageRepoIndexCalculatorService = packageRepoIndexCalculatorService;
        this.authorizationService = authorizationService;

    }

    @Override
    public void reindex(String repoKey, @Nullable String path) throws Exception {
        RepoPath repoPath = RepoPathFactory.create(repoKey, path);
        if (!authorizationService.canManage(repoPath)) {
            String msg = "Skipping indexing request - user without manage permissions";
            log.debug(msg);
            throw new PackageForbiddenException(msg);
        }

        PackageType packageType = packageRepoService.getPackageType(repoKey);
        if (packageType == null) {
            log.warn("Skipping indexing request - package type not found for repo {}", repoKey);
            return;
        }
        boolean isNotEmptyPath = StringUtils.isNotBlank(path);

        if (packageSpecification.isSupportedPackage(packageType)) {
            if (isNotEmptyPath) {
                packageRepoIndexCalculatorService.reindexSpecificPath(packageType, repoKey, path);
                return;
            }
            packageRepoIndexCalculatorService.reindex(packageType, repoKey);

        }
    }
}
