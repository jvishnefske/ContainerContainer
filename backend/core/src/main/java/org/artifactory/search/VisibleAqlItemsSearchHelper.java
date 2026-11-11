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

package org.artifactory.search;

import org.artifactory.api.search.SearchControls;
import org.artifactory.api.security.SecurityService;
import org.artifactory.aql.AqlConverts;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.api.internal.AqlBase;
import org.artifactory.aql.result.AqlLazyResult;
import org.artifactory.aql.result.rows.AqlItem;
import org.artifactory.common.ConstantValues;
import org.artifactory.fs.ItemInfo;
import org.artifactory.mime.NamingUtils;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.storage.spring.StorageContextHelper;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.artifactory.aql.api.internal.AqlBase.or;

/**
 * @author Saffi Hartal
 * Search for AqlItem stream filtered to visible real items (not checksums)
 */
public class VisibleAqlItemsSearchHelper {

    public final InternalRepositoryService repoService;
    public final Predicate<AqlItem> isVisibleNotChecksumLink = this::isVisibleAndNotChecksumLink;
    private final SecurityService securityService;

    public VisibleAqlItemsSearchHelper(@Nonnull SecurityService securityService,
            @Nonnull InternalRepositoryService repoService) {
        this.repoService = repoService;
        this.securityService = securityService;
    }

    /**
     * stats conversion
     */
    public Stream<ItemInfo> visibleItemInfo(Stream<AqlItem> stream) {
        return stream.filter(isVisibleNotChecksumLink).map(AqlConverts.toItemInfo);
    }

    /**
     * Indicates whether the given result repo path is valid or not.<br> A repo path will stated as valid if the given
     * origin local repo is not null, the path is readable (permission and repo-configuration-wise) and if it is not a
     * checksum result (returned for lookup by checksum).
     *
     * @param item AqlItem has  Repopath to validate
     * @return True if the repo path is valid
     */
    private boolean isVisibleAndNotChecksumLink(AqlItem item) {
        RepoPath repoPath = AqlConverts.toRepoPath.apply(item);
        LocalRepo repo = repoService.localFederatedOrCachedRepositoryByKey(repoPath.getRepoKey());
        return repo != null && isVisibleNotChecksumLink(repoPath);
    }

    /**
     * Search Acceptable item info ( visible repo path - ignore checksum results - they are duplicates)
     *
     * @param query          AqlBase.AndClause<AqlApiItem>
     * @param limit          Maximum number of results
     * @param sortByUpdated  Whether to sort results by modified timestamp (most recent first)
     * @param onFinish       Consumer<Exception>
     */
    public Stream<AqlItem> searchVisibleArtifactsAsStream(AqlBase.AndClause<AqlApiItem> query,
            Integer limit,
            boolean sortByUpdated,
            Consumer<Exception> onFinish) {
        if (isQueryLimitedByUserType()) {
            limit = ConstantValues.searchUserQueryLimit.getInt();
        }
        return searchAsStream(query, limit, sortByUpdated, onFinish).filter(isVisibleNotChecksumLink);
    }

    private Stream<AqlItem> searchAsStream(AqlBase.AndClause<AqlApiItem> query, Integer limit,
            boolean sortByUpdated, Consumer<Exception> onFinish) {
        return lazyQuery(query, limit, sortByUpdated).asStream(onFinish);
    }

    /**
     * @return a clause which will filter results based on {@link SearchControls#getSelectedRepoForSearch()}
     * The artifact should exist in at least one of the repositories therefore the relation between the repos is OR
     */
    public static AqlBase.OrClause<AqlApiItem> getSelectedReposForSearchClause(SearchControls controls) {
        List<String> selectedRepoForSearch = controls.getSelectedRepoForSearch();
        AqlBase.OrClause<AqlApiItem> reposToSearchClause = or();
        if (selectedRepoForSearch != null) {
            for (String repoKey : selectedRepoForSearch) {
                reposToSearchClause.append(
                        AqlApiItem.repo().equal(repoKey)
                );
            }
        }
        return reposToSearchClause;
    }


    private AqlLazyResult<AqlItem> lazyQuery(AqlBase.AndClause<AqlApiItem> query,
                                             Integer limit,
                                             boolean sortByUpdated) {
        AqlApiItem aqlQuery = AqlApiItem.create().filter(query);
        AqlService aqlService = StorageContextHelper.get().beanForType(AqlService.class);
        if (limit != null) {
            aqlQuery.limit(limit);
        }
        if (sortByUpdated) {
            aqlQuery.addSortElement(AqlApiItem.updated()).desc();
        }
        return aqlService.executeQueryLazy(aqlQuery);
    }

    /**
     * Indicates whether the given result repo path is valid or not.<br> A repo path will stated as valid if the given
     * origin local repo is not null, the path is readable (permission and repo-configuration-wise) and if it is not a
     * checksum file.
     *
     * @param repoPath Repo path to validate
     * @return True if the repo path is valid
     */
    private boolean isVisibleNotChecksumLink(RepoPath repoPath) {
        return repoService.isRepoPathVisible(repoPath) && (!NamingUtils.isChecksum(repoPath.getPath()));
    }

    private boolean isQueryLimitedByUserType() {
        return ConstantValues.searchLimitAnonymousUserOnly.getBoolean() ?
                securityService.isAnonymous() :
                !securityService.isAdmin();
    }
}
