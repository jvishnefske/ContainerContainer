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

package org.artifactory.aql.result;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlPermissionProvider;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.AqlQueryContext;
import org.artifactory.aql.model.AqlRepoProvider;
import org.artifactory.aql.util.AqlUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.RepoPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.sql.ResultSet;

import static java.util.Objects.nonNull;
import static org.artifactory.event.EventHintType.AsyncThread;
import static org.artifactory.util.evidence.EvidenceConfigurationUtil.isEvidenceAQLFilterInDatabase;
import static org.artifactory.util.evidence.EvidenceConfigurationUtil.isEvidencePath;

/**
 * @author Gidi Shabat
 */
public abstract class AqlRestResult implements Closeable {
    public static final String QUERY_PREFIX = "\n{\n\"results\" : [ ";
    protected static final String NUMBER_OF_ROWS = "<NUMBER_OF_ROWS>";
    protected static final String QUERY_POSTFIX = " ],\n\"range\" : " + NUMBER_OF_ROWS + "\n}\n";
    protected static final ObjectMapper mapper = createObjectMapper();
    protected static final JsonFactory factory = mapper.getFactory();

    private static final Logger log = LoggerFactory.getLogger(AqlRestResult.class);
    private static final String INTERNAL_METADATA_PREFIX = ".jfrog/";

    protected AqlPermissionProvider permissionProvider;
    protected AqlRepoProvider repoProvider;

    @Getter
    private final AqlQueryContext aqlQueryContext;

    AqlRestResult(AqlPermissionProvider permissionProvider, AqlRepoProvider repoProvider) {
        this.permissionProvider = permissionProvider;
        this.repoProvider = repoProvider;
        this.aqlQueryContext = null;
    }

    AqlRestResult(AqlPermissionProvider permissionProvider, AqlRepoProvider repoProvider,
            AqlQueryContext aqlQueryContext) {
        this.permissionProvider = permissionProvider;
        this.repoProvider = repoProvider;
        this.aqlQueryContext = aqlQueryContext;
    }

    protected boolean canRead(AqlDomainEnum domain, ResultSet resultSet) {
        if (AqlDomainEnum.builds == domain) {
            return canReadBuilds(resultSet);
        }
        return canReadItems(domain, resultSet);
    }

    private boolean canReadBuilds(ResultSet resultSet) {
        if (permissionProvider.isAdmin()) {
            return true;
        }
        try {
            String buildName = resultSet.getString(AqlPhysicalFieldEnum.buildName.name());
            String buildNumber = resultSet.getString(AqlPhysicalFieldEnum.buildNumber.name());
            String buildRepo = resultSet.getString(AqlPhysicalFieldEnum.buildRepo.name());
            if (permissionProvider.canReadBuild(buildName, buildNumber, buildRepo)) {
                return true;
            }
        } catch (Exception e) {
            logBuildsMinimalFieldError(e);
        }
        return false;
    }

    private boolean canReadItems(AqlDomainEnum domain, ResultSet resultSet) {
        RepoPath repoPath = null;
        if (AqlDomainEnum.items == domain) {
            try {
                String itemRepo = resultSet.getString(AqlPhysicalFieldEnum.itemRepo.name());
                String itemPath = resultSet.getString(AqlPhysicalFieldEnum.itemPath.name());
                String itemName = resultSet.getString(AqlPhysicalFieldEnum.itemName.name());
                repoPath = AqlUtils.fromAql(itemRepo, itemPath, itemName);
            } catch (Exception e) {
                logItemsMinimalFieldError(e);
            }
        }
        if (nonNull(repoPath) && isHiddenEvidencePath(repoPath.getPath())) {
            return false;
        }
        if (permissionProvider.isAdmin()) {
            return true;
        }
        if (nonNull(repoPath) && !isReadableNonMetadata(repoPath)) {
            return false;
        }
        if (ConstantValues.aqlQueryApplyIncludeExcludePatterns.getBoolean() &&
                nonNull(repoPath) && !repoProvider.isRepoPathAccepted(repoPath)) {
            return false;
        }
        return nonNull(repoPath);
    }

    protected boolean canRead(AqlDomainEnum domain, String repo, String path, String name) {
        if (isHiddenEvidencePath(path)) {
            return false;
        }
        if (permissionProvider.isAdmin()) {
            return true;
        } else {
            if (AqlDomainEnum.items == domain) {
                try {
                    RepoPath repoPath = AqlUtils.fromAql(repo, path, name);
                    if (!isReadableNonMetadata(repoPath)) {
                        return false;
                    }
                    return !ConstantValues.aqlQueryApplyIncludeExcludePatterns.getBoolean() ||
                            repoProvider.isRepoPathAccepted(repoPath);
                } catch (Exception e) {
                    logItemsMinimalFieldError(e);
                }
            }
            return false;
        }
    }

    /**
     * Skip .evidence paths in-memory, when they are not skipped on the level of SQL select.
     * (which can be configured this way due to performance concerns).
     * <p>
     * Do not restrict .evidence inclusion in the case of the async thread, as this is a marker
     * of an internal process (e.g. it's necessary to include .evidence for the Federation full-sync).
     */
    private boolean isHiddenEvidencePath(String path) {
        return isEvidencePath(path)
                && !isEvidenceAQLFilterInDatabase()
                && !EventLogHintManager.contains(AsyncThread);
    }

    /**
     * Check if the repo path is readable and not internal metadata
     *
     * @param repoPath the repo path to check
     * @return true if the repo path is readable and not internal metadata
     */
    private boolean isReadableNonMetadata(RepoPath repoPath) {
        return !repoPath.getPath().startsWith(INTERNAL_METADATA_PREFIX) && permissionProvider.canRead(repoPath);
    }

    public abstract byte[] read();

    public abstract byte[] readCompact();

    public abstract long getRowsCount();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper newMapper = new ObjectMapper();
        newMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        newMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        newMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        return newMapper;
    }

    private void logBuildsMinimalFieldError(Exception e) {
        log.debug("AQL build domain minimal field expectation error: name, number and repo", e);
    }

    private void logItemsMinimalFieldError(Exception e) {
        log.debug("AQL items domain minimal field expectation error: repo, path and name", e);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder(value = {"start", "end", "total"}, alphabetic = true)
    protected static class Range {

        @JsonProperty("start_pos")
        protected Long start;
        @JsonProperty("end_pos")
        protected Long end;
        @JsonProperty("total")
        protected Long total;
        @JsonProperty("limit")
        protected Long limited;
        @JsonProperty("notification")
        protected String notification;

        public Range(long start, long end, long limited) {
            this.start = start;
            this.end = end;
            this.limited = Long.MAX_VALUE == limited ? null : limited;
        }

        private void logAqlHardLimitIfReached(long end) {
            int hardLimit = ConstantValues.aqlSearchQueryMaxLimit.getInt();
            if (0 <= hardLimit && end == hardLimit) {
                String msg = "AQL query reached the search hard limit, results are trimmed";
                log.warn(msg);
                if (ConstantValues.aqlSearchNotifyReachHardLimit.getBoolean()) {
                    this.notification = msg + ". Please address configurable limit.";
                }
            }
        }

        public Range(long start, long end, long total, long limited) {
            logAqlHardLimitIfReached(end);
            this.start = start;
            this.end = end;
            this.total = total;
            this.limited = Long.MAX_VALUE == limited ? null : limited;
        }
    }
}
