/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.repo;

import aQute.libg.glob.AntGlob;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.api.repo.exception.BlackedOutException;
import org.artifactory.api.repo.exception.IncludeExcludeException;
import org.artifactory.api.repo.exception.SnapshotPolicyException;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.descriptor.repo.RealRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.RepoResource;
import org.artifactory.mime.MavenNaming;
import org.artifactory.mime.NamingUtils;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.RepoRequests;
import org.artifactory.resource.BlockedRepoResource;
import org.artifactory.resource.UnfoundRepoResource;
import org.artifactory.security.AccessLogger;
import org.artifactory.util.CollectionUtils;
import org.artifactory.util.GlobalExcludes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import static org.artifactory.resource.UnfoundRepoResourceReason.Reason.INCLUDE_EXCLUDE_PATTERN;

/**
 * @author Fred Simon
 */
public abstract class RealRepoBase<T extends RealRepoDescriptor,
        S extends RealRepoConfig> extends RepoBase<T, S> implements RealRepo<T, S> {
    private static final Logger log = LoggerFactory.getLogger(RealRepoBase.class);

    private static final String DOUBLE_STAR_PREFIX = "**/";
    private static final String DOUBLE_STAR_REGEX_PREFIX = "(?:.*[\\\\/]|)";

    public RealRepoBase(T descriptor, S repoConfig, InternalRepositoryService repositoryService,
            CentralConfigService configService) {
        super(descriptor, repoConfig, repositoryService, configService);
        excludes.addAll(GlobalExcludes.getGlobalExcludes());
    }

    @Override
    public boolean isHandleReleases() {
        return getRepoConfig().getRepoTypeConfig().isHandleReleases();
    }

    @Override
    public boolean isHandleSnapshots() {
        return getRepoConfig().getRepoTypeConfig().isHandleSnapshots();
    }

    @Override
    public boolean isBlackedOut() {
        return getRepoConfig().getRepoTypeConfig().isBlackedOut();
    }

    @Override
    public int getMaxUniqueSnapshots() {
        return getRepoConfig().getRepoTypeConfig().getMaxUniqueSnapshots();
    }

    @Override
    public boolean handlesReleaseSnapshot(String path) {
        if (NamingUtils.isMetadata(path) || NamingUtils.isChecksum(path) || NamingUtils.isSystem(path)) {
            return true;
        }
        ModuleInfo moduleInfo = getItemModuleInfo(path);
        if (!moduleInfo.isValid()) {
            log.debug("{} is not a valid module info -  '{}': not enforcing snapshot/release policy.", this, path);
            return true;
        }
        boolean snapshot = moduleInfo.isIntegration() || StringUtils.contains(path, "[INTEGRATION]");
        if (snapshot && !isHandleSnapshots()) {
            log.debug("{} rejected '{}': not handling snapshots.", this, path);
            return false;
        } else if (!snapshot && !isHandleReleases()) {
            log.debug("{} rejected '{}': not handling releases.", this, path);
            return false;
        }
        return true;
    }

    @Override
    public boolean isLocal() {
        return getRepoConfig().isLocal();
    }

    @Override
    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    public BasicStatusHolder assertValidPath(RepoPath repoPath, boolean downloadRequest) {
        String path = repoPath.getPath();
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        statusHolder.setActivateLogging(log.isDebugEnabled());
        if (isBlackedOut()) {
            BlackedOutException exception = new BlackedOutException(this.getRepoConfig(), getRepoPath(path));
            statusHolder.error(exception.getMessage(), exception.getErrorCode(), exception, log);
        } else if (!handlesReleaseSnapshot(path)) {
            SnapshotPolicyException exception = new SnapshotPolicyException(this.getRepoConfig(), getRepoPath(path));
            statusHolder.error(exception.getMessage(), exception.getErrorCode(), exception, log);
        } else if (!accepts(repoPath)) {
            IncludeExcludeException exception = new IncludeExcludeException(
                    downloadRequest ? HttpStatus.SC_NOT_FOUND : HttpStatus.SC_CONFLICT, this.getRepoConfig(),
                    getRepoPath(path));
            statusHolder.error(exception.getMessage(), exception.getErrorCode(), exception, log);
        }
        return statusHolder;
    }

    protected void assertReadPermissions(RepoPath repoPath, MutableStatusHolder status) {
        AuthorizationService authService = getAuthorizationService();
        boolean canRead = authService.canRead(repoPath);
        if (!canRead) {
            status.error("Download request for repo:path '" + repoPath + "' is forbidden for user: '" +
                            getSecurityService().currentUsername() + "'.",
                    HttpStatus.SC_FORBIDDEN, log);
            AccessLogger.downloadDenied(repoPath);
        }
    }

    protected boolean shouldCheckIsAllowedBeforeDownload(RepoPath repoPath) {
        if (!getDescriptor().getType().equals(RepoType.Maven) ||
                !ConstantValues.enableMavenNonPreemptiveAuthentication.getBoolean()) {
            return excludeNonUniqueSnapshotFromCheck(repoPath);
        }
        return false;
    }

    protected boolean shouldCheckIsAllowedAfterDownload(RepoPath repoPath, boolean isFound) {
        if (getDescriptor().getType().equals(RepoType.Maven) &&
                ConstantValues.enableMavenNonPreemptiveAuthentication.getBoolean() && isFound) {
            return excludeNonUniqueSnapshotFromCheck(repoPath);
        }
        return false;
    }

    /**
     * Non-unique snapshot jar does not exist, so we don't need to check it,
     * but let's check that the parent folder is not empty - and it will be resolved to something
     *
     * @param repoPath - the path to check
     * @return true if the path should not be excluded from the check
     */
    private boolean excludeNonUniqueSnapshotFromCheck(RepoPath repoPath) {
        if (MavenNaming.isNonUniqueSnapshot(repoPath.getPath())) {
            // Do nothing for remote repo, only for local
            if (getRepositoryService().remoteRepositoryByKey(repoPath.getRepoKey()) != null) {
                return true;
            }
            // Check if the parent folder is not empty
            return !getRepositoryService().hasChildren(repoPath);
        }

        return true;
    }

    @Nullable
    protected RepoResource checkResourceStatusBeforeDownload(InternalRequestContext context, RepoPath repoPath) {
        StatusHolder statusHolder;

        if (shouldCheckIsAllowedBeforeDownload(repoPath)) {
            statusHolder = checkDownloadIsAllowed(repoPath);
            if (statusHolder.isError()) {
                return createUnfoundRepoResource(repoPath, statusHolder);
            }
        }

        statusHolder = checkDownloadIsNotBlocked(context, repoPath);
        if (statusHolder.isError()) {
            RepoRequests.logToContext("Download denied (%s) - returning blocked resource", statusHolder.getStatusMsg());
            return new BlockedRepoResource(repoPath, statusHolder.getStatusMsg(), BlockedRepoResource.BlockedType.XRAY);
        }
        return null;
    }

    @NonNull
    protected RepoResource checkResourceStatusAfterDownload(InternalRequestContext context, RepoResource res,
            RepoPath repoPath) {
        checkAndMarkExpirableResource(res, context.getRequest().getRepoPath());
        if (shouldCheckIsAllowedAfterDownload(repoPath, res.isFound())) {
            StatusHolder statusHolder = checkDownloadIsAllowed(repoPath);
            if (statusHolder.isError()) {
                return createUnfoundRepoResource(repoPath, statusHolder);
            }
        }
        return res;
    }

    @NonNull
    static UnfoundRepoResource createUnfoundRepoResource(RepoPath repoPath, StatusHolder statusHolder) {
        RepoRequests.logToContext("Download denied (%s) - returning un-found resource",
                                  statusHolder.getStatusMsg());
        if (statusHolder.getException() instanceof IncludeExcludeException) {
            return new UnfoundRepoResource(repoPath, statusHolder.getStatusMsg(), statusHolder.getStatusCode(),
                                           INCLUDE_EXCLUDE_PATTERN);
        }
        return new UnfoundRepoResource(repoPath, statusHolder.getStatusMsg(), statusHolder.getStatusCode());
    }

    protected Pattern createPattern(Collection<String> excludes) {
        return ConstantValues.doubleStarOptimizationForExcludes.getBoolean() ?
                createOptimizedPattern(excludes) :
                createStandardPattern(excludes);
    }

    private Pattern createStandardPattern(Collection<String> excludes) {
        if (CollectionUtils.notNullOrEmpty(excludes)) {
            List<String> patterns = new ArrayList<>();
            excludes.forEach(exclude -> patterns.add(AntGlob.toPattern(exclude).toString()));
            String combinedPattern = String.join("|", patterns);
            combinedPattern = "(?:" + combinedPattern + ")";
            log.debug("Creating pattern: {} from excludes: {}", combinedPattern, excludes);
            return Pattern.compile(combinedPattern);
        }
        return null;
    }

    /**
     * Same as {@link #createStandardPattern(Collection)} but factors out the shared {@code (?:.*[\\/]|)} prefix that
     * {@link AntGlob} emits for every exclude starting with "double-star".
     * <p>
     * The default {@code createStandardPattern} produces an alternation where that greedy, backtracking prefix is
     * repeated
     * in front of every "double-star" branch (e.g. {@code (?:.*[\\/]|)A|(?:.*[\\/]|)B|...}). Since the pattern is
     * matched with {@code Matcher#matches()} (full match), {@code P(A|B)} is equivalent to {@code PA|PB}, so the prefix
     * can be emitted once around a single alternation of the remainders: {@code (?:.*[\\/]|)(?:A|B|...)}. This avoids
     * re-running the {@code .*[\\/]} backtracking once per branch.
     * <p>
     * Excludes that do NOT start with "double-star" are converted exactly as in
     * {@link #createStandardPattern(Collection)}.
     */
    private Pattern createOptimizedPattern(Collection<String> excludes) {
        if (CollectionUtils.notNullOrEmpty(excludes)) {
            List<String> prefixedRemainders = new ArrayList<>();
            List<String> otherPatterns = new ArrayList<>();
            for (String exclude : excludes) {
                String trimmed = StringUtils.trim(exclude);
                if (trimmed != null && trimmed.startsWith(DOUBLE_STAR_PREFIX)) {
                    String remainder = trimmed.substring(DOUBLE_STAR_PREFIX.length());
                    prefixedRemainders.add(AntGlob.toPattern(remainder).toString());
                } else {
                    otherPatterns.add(AntGlob.toPattern(exclude).toString());
                }
            }
            List<String> alternatives = new ArrayList<>();
            if (!prefixedRemainders.isEmpty()) {
                alternatives.add(DOUBLE_STAR_REGEX_PREFIX + "(?:" + String.join("|", prefixedRemainders) + ")");
            }
            alternatives.addAll(otherPatterns);
            String combinedPattern = "(?:" + String.join("|", alternatives) + ")";
            log.debug("Creating optimized pattern: {} from excludes: {}", combinedPattern, excludes);
            return Pattern.compile(combinedPattern);
        }
        return null;
    }

}