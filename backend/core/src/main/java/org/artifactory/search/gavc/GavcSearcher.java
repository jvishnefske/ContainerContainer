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

package org.artifactory.search.gavc;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.api.module.ModuleInfoUtils;
import org.artifactory.api.search.ItemSearchResults;
import org.artifactory.api.search.gavc.GavcSearchControls;
import org.artifactory.api.search.gavc.GavcSearchResult;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.repo.HttpRepo;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.remote.browse.RemoteItem;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.request.RemoteRequestException;
import org.artifactory.sapi.search.VfsBoolType;
import org.artifactory.sapi.search.VfsComparatorType;
import org.artifactory.sapi.search.VfsDateFieldName;
import org.artifactory.sapi.search.VfsQuery;
import org.artifactory.sapi.search.VfsQueryResult;
import org.artifactory.sapi.search.VfsQueryResultType;
import org.artifactory.sapi.search.VfsQueryRow;
import org.artifactory.search.SearcherBase;
import org.artifactory.util.CollectionUtils;
import org.jfrog.client.util.PathUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.artifactory.maven.MavenModelUtils.escapeGroupPath;
import static org.artifactory.sapi.search.VfsBoolType.OR;
import static org.artifactory.sapi.search.VfsComparatorType.EQUAL;
import static org.artifactory.sapi.search.VfsComparatorType.LOWER_THAN;
import static org.artifactory.sapi.search.VfsComparatorType.LOWER_THAN_EQUAL;
import static org.artifactory.sapi.search.VfsComparatorType.NONE;

/**
 * Holds the GAVC search logic
 *
 * @author Noam Tenne
 * @author Natalie Kirillov
 */

@Slf4j
@NoArgsConstructor
@AllArgsConstructor
public class GavcSearcher extends SearcherBase<GavcSearchControls, GavcSearchResult> {
    private static final String FILE_EXT_RE = ".*\\.\\w+$";
    private static final String EXCLUDED_FILES = ".*(\\.sha1|\\.sha256|\\.md5|\\.asc|maven-metadata.xml)$";

    boolean useStrictTimestampComparator;

    @Override
    public ItemSearchResults<GavcSearchResult> doSearch(GavcSearchControls controls) {
        // regular gavc flow
        List<GavcSearchResult> localResults = doLocalSearch(controls, null, null);

        // verify we have any remote/virtual repos to search
        List<String> repos = controls.getSelectedRepoForSearch();
        repos = CollectionUtils.isNullOrEmpty(repos) ? List.of() : repos;

        // specific gavc flow for remote and virtual repositories
        Collection<GavcSearchResult> results;
        if (controls.isSpecific()) {
            results = new LinkedHashSet<>(localResults);
            results.addAll(doSpecificSearch(controls, repos));
        } else {
            results = Lists.newArrayList();
            results.addAll(localResults);
        }

        return new ItemSearchResults<>(Lists.newArrayList(results), results.size());
    }

    @Override
    public long doCount(GavcSearchControls controls) {
        //count not supporting specific gavc flow for remote and virtual repositories
        if (controls.isSpecific()) {
            String errMessage =
                    "count not supporting specific gavc flow for remote and virtual repositories, specific should be " +
                            "false";
            log.error(errMessage);
            throw new IllegalArgumentException(errMessage);
        }

        VfsQuery query;
        if (controls.getSortDirection() != GavcSearchControls.Direction.NONE) {
            log.debug("Sorting is not supported for count queries. Ignoring sorting.");
            GavcSearchControls newControls = new GavcSearchControls(controls);
            newControls.setSortDirection(GavcSearchControls.Direction.NONE);
            query = getBaseQuery(newControls, null, null);
        } else {
            query = getBaseQuery(controls, null, null);
        }

        return query.count();
    }

    @Override
    protected long getLimit(GavcSearchControls controls) {
        if (controls.getLimitOverride() != null) {
            return controls.getLimitOverride();
        }
        return super.getLimit(controls);
    }

    @SuppressWarnings("rawtypes")
    private List<GavcSearchResult> doLocalSearch(GavcSearchControls controls, List<String> localRepoKeys,
            @Nullable String virtualRepoKey) {
        VfsQuery query = getBaseQuery(controls, localRepoKeys, virtualRepoKey);

        long limit = getLimit(controls);
        VfsQueryResult queryResult = query.execute(limit);
        List<GavcSearchResult> results = Lists.newArrayList();
        for (VfsQueryRow row : queryResult.getAllRows()) {
            if (results.size() >= limit) {
                break;
            }

            ItemInfo item = row.getItem();
            String repoKey = item.getRepoKey();
            LocalRepo localRepo = getRepoService().localFederatedOrCachedRepositoryByKey(repoKey);
            if (localRepo == null || !isResultAcceptable(item.getRepoPath(), localRepo)) {
                continue;
            }

            ModuleInfo moduleInfo = localRepo.getItemModuleInfo(item.getRelPath());
            if (StringUtils.isNotEmpty(virtualRepoKey)) {
                String filePath = String.join("/", virtualRepoKey, item.getRelPath());
                item = new FileInfoImpl(RepoPathFactory.create(filePath));
            }

            if (moduleInfo.isValid()) {
                results.add(new GavcSearchResult(item, moduleInfo));
            }
        }

        return results;
    }

    @VisibleForTesting
    VfsQuery getBaseQuery(GavcSearchControls controls, List<String> localRepoKeys,
            @Nullable String virtualRepoKey) {
        //Validate and escape all input values
        String groupInput = escapeGroupPath(controls.getGroupId());

        if (StringUtils.isNotEmpty(virtualRepoKey)) {
            controls.setSelectedRepoForSearch(localRepoKeys);
        }

        //Build search path from inputted group
        VfsQuery query = createQuery(controls)
                .expectedResult(VfsQueryResultType.FILE)
                .addPathFilter(groupInput)
                .addPathFilters(controls.getArtifactId(), controls.getVersion());

        addNodeNameFilter(query, controls);
        addDatesFilter(query, controls);
        addSorting(query, controls, VfsDateFieldName.CREATED.getPropName());
        addCustomPropertyFilter(query, controls);
        addIncludedFolderPathsFilter(query, controls);
        return query;
    }


    /**
     * Adds a filter to restrict search results to specific folder paths.
     * When included folder paths are specified in the controls, this method creates a grouped filter
     * with OR conditions between the paths.
     * If the set only contains default wildcard values ("*" and/or "**") that match everything,
     * no filter is added.
     *
     * @param query    the VFS query to add the filter to
     * @param controls the search controls containing the included folder paths
     */
    private void addIncludedFolderPathsFilter(VfsQuery query, GavcSearchControls controls) {
        Set<String> includedFolderPaths = controls.getIncludedFolderPaths();
        if (CollectionUtils.isNullOrEmpty(includedFolderPaths) || containsOnlyDefaultWildcards(includedFolderPaths)) {
            return;
        }

        query.startGroup();
        for (String folderPath : includedFolderPaths) {
            VfsComparatorType comparatorType = folderPath.contains("*") ? VfsComparatorType.CONTAINS : EQUAL;
            query.prop("node_path")
                    .comp(comparatorType)
                    .val(folderPath)
                    .nextBool(OR);
        }
        query.endGroup();

    }

    private boolean containsOnlyDefaultWildcards(Set<String> paths) {
        return paths.stream().allMatch(p -> "*".equals(p) || "**".equals(p));
    }

    /**
     * Adds a property filter to the query based on the included property defined in the search controls.
     * <p>
     * When the property value contains a wildcard ({@code *}), the filter uses the {@code LIKE} SQL operator
     * so that patterns such as {@code 2026-03-*} correctly match values like {@code 2026-03-12}.
     * Without a wildcard, an exact {@code =} match is used instead.
     * </p>
     *
     * @param query    the VFS query to add the filter to
     * @param controls the search controls containing the included property key/value pair
     */
    private void addCustomPropertyFilter(VfsQuery query, GavcSearchControls controls) {
        if (controls.getIncludedProperty() != null) {
            String value = controls.getIncludedProperty().getValue();
            VfsComparatorType comparatorType =
                    value != null && value.contains("*") ? VfsComparatorType.CONTAINS : EQUAL;
            query.startGroup();
            query.prop(controls.getIncludedProperty().getKey())
                    .comp(comparatorType)
                    .val(value);
            query.endGroup();
        }
    }

    /**
     * Add sorting to the query
     *
     * @param query    - The query to add sorting
     * @param controls - Search credentials
     */
    @VisibleForTesting
    void addSorting(VfsQuery query, GavcSearchControls controls, String propertyName) {
        if (StringUtils.isBlank(propertyName)) {
            log.warn("Sorting property name is blank. Ignoring sorting.");
            return;
        }
        if (controls.getSortDirection() == GavcSearchControls.Direction.NONE) {
            log.debug("Sorting direction is NONE. Ignoring sorting.");
        } else if (controls.getSortDirection() == GavcSearchControls.Direction.ASC) {
            log.debug("Sorting ascending by property {}", propertyName);
            query.orderByAscending(propertyName);
        } else if (controls.getSortDirection() == GavcSearchControls.Direction.DESC) {
            log.debug("Sorting descending by property {}", propertyName);
            query.orderByDescending(propertyName);
        }
    }

    /**
     * We would like to search based on the node name provided by the GAVC controls.
     * <p>
     * We have a few cases to consider:
     * <ul>
     * <li> Searching with only a classifier: In this case, we will look for *-classifier*.
     * <li> Searching with only an extension: In this case, we will look for *.ext.
     * <li> Searching with both a classifier and an extension: In this case,
     *      we will look for *-classifier*.ext.
     * </ul>
     * Note: Having more than one extension, will create a search group with OR between them, e.g.
     * *-classifier*.ext1 or *-classifier*.ext2.
     * </p>
     *
     * @param query    - The query to add the node name
     * @param controls - Search credentials
     **/

    @VisibleForTesting
    VfsQuery addNodeNameFilter(VfsQuery query, GavcSearchControls controls) {
        String classifier = controls.getClassifier();
        List<String> extensions = controls.getExtensions();

        if (StringUtils.isNotBlank(classifier)) {
            String namePattern = "*-" + classifier + "*";
            if (CollectionUtils.isNullOrEmpty(extensions)) {
                query.startGroup().name(namePattern).endGroup();
                return query;
            }
            addExtensionFilters(query, namePattern, extensions);
        } else if (!CollectionUtils.isNullOrEmpty(extensions)) {
            addExtensionFilters(query, "*", extensions);
        }

        return query;
    }

    /**
     * Add extension filters to the query.
     * If it is the last filter, we will use the AND operator, otherwise, we will use the OR operator.
     */
    private void addExtensionFilters(VfsQuery query, String namePattern, List<String> extensions) {
        if (!extensions.isEmpty()) {
            query.startGroup();

            for (String ext : extensions) {
                query.name(namePattern + "." + ext).nextBool(OR);
            }

            query.endGroup();
        }
    }

    /**
     * We would like to search based on some dates provided by the GAVC controls.
     * While a specific date parameter is greater than zero,
     * we add a time condition to the query to ensure it is before or equal or strictly before this date.
     *
     * @param query    - The query to add time conditions
     * @param controls - Search credentials
     **/
    @VisibleForTesting
    void addDatesFilter(VfsQuery query, GavcSearchControls controls) {
        boolean startGroup = false;

        if (controls.getCreateBefore() > 0) {
            startGroup = isShouldUseGroupForDate(query, startGroup);
            addDateFilter(query, controls.getCreateBefore(), VfsDateFieldName.CREATED.getPropName());
        }

        if (controls.getLastModifiedBefore() > 0) {
            startGroup = isShouldUseGroupForDate(query, startGroup);
            addDateFilter(query, controls.getLastModifiedBefore(), VfsDateFieldName.LAST_MODIFIED.getPropName());
        }

        if (controls.getLastDownloadedBefore() > 0) {
            startGroup = isShouldUseGroupForDate(query, startGroup);
            addLastDownloadedDateFilter(query, controls);
        }

        if (startGroup) {
            query.endGroup();
        }
    }

    /**
     * Determines whether to start a group in the query based on specified conditions.
     * The group should be started only once if any of the specified conditions above are met.
     */
    private boolean isShouldUseGroupForDate(VfsQuery query, boolean startGroup) {
        if (!startGroup) {
            query.startGroup();
            startGroup = true;
        }
        return startGroup;
    }


    void addDateFilter(VfsQuery query, long timestamp, String propName) {
        if (timestamp > 0) {
            query.prop(propName)
                    .comp(useStrictTimestampComparator ? LOWER_THAN : LOWER_THAN_EQUAL)
                    .val(timestamp);
        }
    }

    /**
     * Adding last downloaded date query filter.
     * For artifacts that was not downloaded at all the date value will be NULL. Therefore, if the query wishes to
     * find all the Artifacts that was created before date X and last downloaded before date Y, we need to included
     * also the artifacts that was not downloaded at all (NULL) because they were created before date X.
     */
    private void addLastDownloadedDateFilter(VfsQuery query, GavcSearchControls controls) {
        VfsComparatorType comparatorType = useStrictTimestampComparator ? LOWER_THAN : LOWER_THAN_EQUAL;

        if (controls.getCreateBefore() <= 0) {
            addDownloadedAndRemoteDateFilters(query, controls, comparatorType);
        } else {
            addGroupedFiltersWithNoneComparator(query, comparatorType, controls);
        }
    }

    private void addDownloadedAndRemoteDateFilters(VfsQuery query, GavcSearchControls controls,
            VfsComparatorType comparatorType) {
        if (controls.isIncludeRemotes()) {
            setDownloadedAndRemoteDateFiltersForIncludedRemotes(query, controls, comparatorType);
        } else {
            query.prop(VfsDateFieldName.LAST_DOWNLOADED.getPropName())
                    .comp(comparatorType)
                    .val(controls.getLastDownloadedBefore())
                    .nextBool(OR).
                    startGroup()
                    .prop(VfsDateFieldName.LAST_DOWNLOADED.getPropName())
                    .comp(NONE)
                    .prop(VfsDateFieldName.CREATED.getPropName())
                    .comp(comparatorType)
                    .val(controls.getLastDownloadedBefore())
                    .endGroup();
        }
    }

    /**
     * This method adds a filter to the VfsQuery query based on the last downloaded date.
     * It considers both local and remote downloads.
     * <p>
     * The filter is constructed in such a way that it considers three scenarios:
     * 1. Both the local and remote download dates are less than the `lastDownloadedBefore`.
     * 2. The local download date is less than the `lastDownloadedBefore` date and the remote download date is null.
     * 3. The local download date is null and the remote download date is less than the `lastDownloadedBefore` date.
     * <p>
     * This filter ensures that the query will return artifacts that were last downloaded (either locally or
     * remotely) before the `lastDownloadedBefore` date.
     *
     * @param query          The query to add the last downloaded date filter
     * @param controls       The GAVC search parameters
     * @param comparatorType The comparator type to use in the filter
     */
    private void setDownloadedAndRemoteDateFiltersForIncludedRemotes(VfsQuery query, GavcSearchControls controls,
            VfsComparatorType comparatorType) {
        String lastDownloadedPropName = VfsDateFieldName.LAST_DOWNLOADED.getPropName();
        String lastRemoteDownloadedPropName = VfsDateFieldName.LAST_REMOTE_DOWNLOADED.getPropName();
        long lastDownloadedBefore = controls.getLastDownloadedBefore();
        // First group: match both LAST_DOWNLOADED and LAST_REMOTE_DOWNLOADED
        addDownloadedAndRemoteDateFilters(query, comparatorType, lastDownloadedPropName, lastDownloadedBefore,
                lastRemoteDownloadedPropName);
        // Second group: match LAST_DOWNLOADED and LAST_REMOTE_DOWNLOADED is NONE
        addNoneComparatorAndDateFilterGroup(query, comparatorType, lastRemoteDownloadedPropName, lastDownloadedPropName,
                lastDownloadedBefore, OR);
        // Third group: match LAST_DOWNLOADED is NONE and LAST_REMOTE_DOWNLOADED
        addNoneComparatorAndDateFilterGroup(query, comparatorType, lastDownloadedPropName, lastRemoteDownloadedPropName,
                lastDownloadedBefore, OR);
        // Fourth group: match LAST_DOWNLOADED is NONE and LAST_REMOTE_DOWNLOADED is NONE
        addNoneComparatorAndDateFilterGroup(query, comparatorType, lastDownloadedPropName,
                lastRemoteDownloadedPropName, lastDownloadedBefore);
    }

    private void addNoneComparatorAndDateFilterGroup(VfsQuery query, VfsComparatorType comparatorType,
            String firstDateFieldName, String secondDateFieldName, long lastDownloadedBefore) {
        query.startGroup()
                .prop(firstDateFieldName)
                .comp(NONE)
                .prop(secondDateFieldName)
                .comp(NONE)
                .prop(VfsDateFieldName.CREATED.getPropName())
                .comp(comparatorType)
                .val(lastDownloadedBefore)
                .endGroup();
    }

    private void addDownloadedAndRemoteDateFilters(VfsQuery query, VfsComparatorType comparatorType,
            String lastDownloadedPropName, long lastDownloadedBefore, String lastRemoteDownloadedPropName) {
        query.startGroup()
                .prop(lastDownloadedPropName)
                .comp(comparatorType)
                .val(lastDownloadedBefore)
                .prop(lastRemoteDownloadedPropName)
                .comp(comparatorType)
                .val(lastDownloadedBefore)
                .endGroup(VfsBoolType.OR);
    }

    private void addNoneComparatorAndDateFilterGroup(VfsQuery query, VfsComparatorType comparatorType,
            String noneDateFieldName, String dateFieldName, long lastDownloadedBefore,
            VfsBoolType vfsBoolType) {
        query.startGroup()
                .prop(noneDateFieldName)
                .comp(NONE)
                .prop(dateFieldName)
                .comp(comparatorType)
                .val(lastDownloadedBefore)
                .endGroup(vfsBoolType);
    }

    /**
     * Adds grouped filters to the provided VfsQuery. The filters are based on the last downloaded date.
     * The method adds two filters for each date field: one with the provided comparator and the value of
     * lastDownloadedBefore,
     * and another with the NONE comparator. These two filters are grouped together using the OR boolean operator.
     * This ensures that the query will return artifacts that were last downloaded before the `lastDownloadedBefore`
     * date
     * or artifacts that were not downloaded at all (NULL).
     *
     * @param query          The VfsQuery to which the filters are added.
     * @param comparatorType The comparator to use for the date comparison.
     * @param control        The GavcSearchControls object containing the timestamp to use for the date comparison.
     */
    private void addGroupedFiltersWithNoneComparator(VfsQuery query, VfsComparatorType comparatorType,
            GavcSearchControls control) {
        query.startGroup();
        addDateFilterWithNoneOrComparator(query, comparatorType, control.getLastDownloadedBefore(),
                VfsDateFieldName.LAST_DOWNLOADED.getPropName());
        query.endGroup();

        if (control.isIncludeRemotes()) {
            query.startGroup();
            addDateFilterWithNoneOrComparator(query, comparatorType, control.getLastDownloadedBefore(),
                    VfsDateFieldName.LAST_REMOTE_DOWNLOADED.getPropName());
            query.endGroup();
        }

    }

    private void addDateFilterWithNoneOrComparator(VfsQuery query, VfsComparatorType comparatorType,
            long lastDownloadedBefore, String downloadedPropName) {
        query.prop(downloadedPropName)
                .comp(NONE)
                .nextBool(OR)
                .prop(downloadedPropName)
                .comp(comparatorType)
                .val(lastDownloadedBefore);
    }

    /**
     * Perform a GAVC search on a remote repository
     *
     * @param controls       - Search credentials
     * @param repoKey        - The remote repository that's being queried
     * @param virtualRepoKey - The virtual repository key the remote is aggregated to (if it is)
     * @return A list containing all the GavcSearchResult objects
     */
    private Set<GavcSearchResult> doRemoteSearch(GavcSearchControls controls, String repoKey,
            @Nullable String virtualRepoKey) {
        Set<GavcSearchResult> results = new LinkedHashSet<>();
        if (!canSearchRemote(controls)) {
            return results;
        }

        HttpRepo repo = (HttpRepo) getRepoService().remoteRepositoryByKey(repoKey);
        String groupId = escapeGroupPath(controls.getGroupId());
        String artifactId = controls.getArtifactId();
        String version = controls.getVersion();
        String url = getRemoteSearchUrl(repo, groupId, artifactId, version);

        try {
            List<RemoteItem> items = repo.getChildUrls(url);

            // search for specific versions for group id + artifact combination
            if (StringUtils.isEmpty(version)) {
                for (RemoteItem item : items) {
                    String itemUrl = item.getUrl();

                    // skip items that don't indicate a version directory for artifact
                    if (itemUrl.matches(FILE_EXT_RE)) {
                        continue;
                    }

                    List<RemoteItem> versionItems = repo.getChildUrls(itemUrl);
                    results.addAll(aggregateResults(versionItems, repo, virtualRepoKey));
                }
            } else {
                results.addAll(aggregateResults(items, repo, virtualRepoKey));
            }
        } catch (RemoteRequestException e) {
            log.debug("Failed to fetch items for Maven remote repository: {}", repo.getUrl(), e);
        } catch (IOException e) {
            log.error("Failed to fetch items for Maven remote repository: {} - {}", repo.getUrl(), e.getMessage());
        }

        return results;
    }

    /**
     * Perform a GAVC search on a virtual repository
     *
     * @param controls - Search credentials
     * @param repo     - The virtual repository that's being queried
     * @return A list containing all the GavcSearchResult objects
     */
    private Set<GavcSearchResult> doVirtualSearch(GavcSearchControls controls, VirtualRepo repo) {
        List<String> remotes = repo.getResolvedRemoteRepos()
                .stream()
                .map(Repo::getKey)
                .toList();

        List<String> locals = repo.getResolvedLocalFederatedAndCachedRepos()
                .stream()
                .map(Repo::getKey)
                .toList();

        Set<GavcSearchResult> results = new LinkedHashSet<>(doLocalSearch(controls, locals, repo.getKey()));
        remotes.forEach(key -> results.addAll(doRemoteSearch(controls, key, repo.getKey())));

        return results;
    }

    /**
     * Perform an extended GAVC search on virtual and remote repositories
     *
     * @param controls - Search credentials
     * @param repos    - A list of all the repositories we want to search on
     * @return A list containing all the GavcSearchResult objects
     */
    private Set<GavcSearchResult> doSpecificSearch(GavcSearchControls controls, List<String> repos) {
        Set<GavcSearchResult> extendedResults = new LinkedHashSet<>();

        repos.forEach(repoKey -> {
            VirtualRepo virtualRepo = getRepoService().virtualRepositoryByKey(repoKey);
            if (virtualRepo != null) {
                extendedResults.addAll(doVirtualSearch(controls, virtualRepo));
                return;
            }

            if (getRepoService().remoteRepositoryByKey(repoKey) != null) {
                extendedResults.addAll(doRemoteSearch(controls, repoKey, null));
            }
        });

        return extendedResults;
    }

    /**
     * Aggregate the found results into a GavcSearchResult list
     *
     * @param items          - Current iteration found items
     * @param repo           - The remote repository being queried
     * @param virtualRepoKey - The virtual repository key, if a virtual repository is being queried
     * @return A list containing all the GavcSearchResult objects
     */
    private Set<GavcSearchResult> aggregateResults(List<RemoteItem> items, HttpRepo repo,
            @Nullable String virtualRepoKey) {
        Set<GavcSearchResult> results = new LinkedHashSet<>();
        String repoKey = StringUtils.isNotEmpty(virtualRepoKey) ? virtualRepoKey : repo.getKey();
        RepoLayout repoLayout = repo.getDescriptor().getRepoLayout();

        items.forEach(item -> {
            // filter out metadata and checksum files
            String url = item.getUrl();
            if (url.matches(EXCLUDED_FILES)) {
                return;
            }

            String relPath = url.replaceFirst(repo.getUrl(), "");
            String filePath = String.join("/", repoKey, relPath);
            FileInfo fileInfo = new FileInfoImpl(RepoPathFactory.create(filePath));
            ModuleInfo moduleInfo = ModuleInfoUtils.moduleInfoFromArtifactPath(relPath, repoLayout);
            results.add(new GavcSearchResult(fileInfo, moduleInfo));
        });

        return results;
    }

    /**
     * Verify there are remote repositories to run the query against
     * Verify group id parameter is present
     * Verify artifact id parameter is present
     *
     * @param controls - Contains the search parameters
     * @return A list of the remote repositories that are being queried if all validations have passed,
     * or an empty list otherwise
     */
    private boolean canSearchRemote(GavcSearchControls controls) {
        String groupId = controls.getGroupId();
        if (StringUtils.isEmpty(groupId)) {
            log.info("Group id parameter is mandatory for remote repository search");
            return false;
        }

        String artifactId = controls.getArtifactId();
        if (StringUtils.isEmpty(artifactId)) {
            log.info("Artifact id parameter is mandatory for remote repository search");
            return false;
        }

        return true;
    }

    /**
     * Constructs the search URL for the remote repository
     *
     * @param repo       - the remote repository
     * @param groupId    - group id for search
     * @param artifactId - artifact id for search
     * @param version    - version for search
     * @return The search URL for the remote repository
     */
    private String getRemoteSearchUrl(HttpRepo repo, String groupId, String artifactId, String version) {
        RepoLayout repoLayout = repo.getDescriptor().getRepoLayout();
        ModuleInfo moduleInfo = new ModuleInfo(groupId, artifactId, version, null, null, null, null, null, null);
        String baseItemPath = ModuleInfoUtils.constructBaseItemPath(moduleInfo, repoLayout);

        return String.join("/", PathUtils.trimTrailingSlashes(repo.getUrl()), baseItemPath);
    }
}