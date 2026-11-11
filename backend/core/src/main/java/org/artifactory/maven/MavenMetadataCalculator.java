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

package org.artifactory.maven;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jfrog.ph.acl.metrics.PackageIndexMetrics;
import com.jfrog.ph.acl.metrics.PackageIndexMetrics.Trigger;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Snapshot;
import org.apache.maven.artifact.repository.metadata.SnapshotVersion;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.maven.MavenArtifactInfo;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.api.module.ModuleInfoUtils;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.result.AqlLazyResult;
import org.artifactory.aql.result.rows.AqlLazyObjectResultStreamer;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.repo.PackageType;
import org.artifactory.common.repo.RepoType;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.SnapshotVersionBehaviorDescriptor;
import org.artifactory.fs.ItemInfo;
import org.artifactory.maven.snapshot.BuildNumberSnapshotComparator;
import org.artifactory.maven.snapshot.SnapshotComparator;
import org.artifactory.maven.versioning.MavenMetadataVersionComparator;
import org.artifactory.maven.versioning.VersionNameMavenMetadataVersionComparator;
import org.artifactory.mime.MavenNaming;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.db.fs.entity.NodePath;
import org.artifactory.storage.fs.tree.ItemNode;
import org.artifactory.storage.fs.tree.ItemTree;
import org.artifactory.util.RepoLayoutUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.createWithEmptyResults;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.created;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.name;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.path;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.repo;
import static org.artifactory.aql.api.internal.AqlBase.and;

/**
 * Calculates maven metadata recursively for folder in a local non-cache repository. Plugins metadata is calculated for
 * the whole repository.
 *
 * @author Yossi Shaul
 */
@Slf4j
public class MavenMetadataCalculator extends AbstractMetadataCalculator {

    private final RepoPath baseFolder;
    private final boolean recursive;
    @Nullable
    private final PackageType packageType;
    private final Trigger trigger;
    @Nullable
    private final PackageIndexMetrics packageIndexMetrics;

    /**
     * Test/legacy constructor that does not record any indexing metrics. Production code paths should
     * use {@link #MavenMetadataCalculator(RepoPath, boolean, PackageType, Trigger, PackageIndexMetrics)}
     * so per-folder duration / version-count / index-size metrics are emitted.
     */
    MavenMetadataCalculator(RepoPath baseFolder, boolean recursive) {
        this(baseFolder, recursive, null, Trigger.INDEX, null);
    }

    /**
     * Creates new instance of maven metadata calculator.
     *
     * @param baseFolder          folder to calculate metadata for
     * @param recursive           true if the calculator should recursively calculate maven metadata for all sub folders
     * @param packageType         underlying repository package type (Maven / Ivy / Gradle / SBT / P2). Used as the
     *                            {@code packageType} tag on emitted metrics. May be {@code null} when metrics are not
     *                            being recorded.
     * @param trigger             whether this calculation was triggered by an upload event
     *                            ({@link Trigger#INDEX}) or by a manual reindex request ({@link Trigger#REINDEX})
     * @param packageIndexMetrics shared metrics recorder, or {@code null} to disable metric emission
     */
    MavenMetadataCalculator(RepoPath baseFolder, boolean recursive, @Nullable PackageType packageType, Trigger trigger,
            @Nullable PackageIndexMetrics packageIndexMetrics) {
        this.baseFolder = baseFolder;
        this.recursive = recursive;
        this.packageType = packageType;
        this.trigger = trigger;
        this.packageIndexMetrics = packageIndexMetrics;
    }

    private static MavenMetadataVersionComparator createVersionComparator() {
        String comparatorFqn = ConstantValues.mvnMetadataVersionsComparator.getString();
        if (StringUtils.isBlank(comparatorFqn)) {
            // return the default comparator
            return VersionNameMavenMetadataVersionComparator.get();
        }

        try {
            Class<?> comparatorClass = Class.forName(comparatorFqn);
            return (MavenMetadataVersionComparator) comparatorClass.newInstance();
        } catch (Exception e) {
            log.warn("Failed to create custom maven metadata version comparator '{}': {}", comparatorFqn,
                    e.getMessage());
            return VersionNameMavenMetadataVersionComparator.get();
        }
    }

    public static SnapshotComparator createSnapshotComparator() {
        SnapshotComparator comparator = BuildNumberSnapshotComparator.get();
        // Try to load custom comparator
        String comparatorFqn = ConstantValues.mvnMetadataSnapshotComparator.getString();
        if (!StringUtils.isBlank(comparatorFqn)) {
            try {
                Class<?> comparatorClass = Class.forName(comparatorFqn);
                Method get = comparatorClass.getMethod("get");
                comparator = (SnapshotComparator) get.invoke(null);
                log.debug("Using custom snapshot comparator '{}' to calculate the latest snapshot", comparatorFqn);
            } catch (NoSuchMethodException e1) {
                log.warn(
                        "Failed to create custom maven metadata snapshot comparator, the comparator should contain" +
                                " static get method to avoid unnecessary object creation '{}': {}", comparatorFqn,
                        e1.getMessage());
            } catch (Exception e) {
                log.warn("Failed to create custom maven metadata snapshot comparator '{}': {}", comparatorFqn,
                        e.getMessage());
            }
        }
        return comparator;
    }

    /**
     * Starts calculation of the maven metadata from the base repo path
     *
     * @return Status of the metadata calculation
     */
    public BasicStatusHolder calculate() {
        long start = System.nanoTime();
        log.debug("Started {} maven metadata calculation on '{}'",
                (recursive ? "recursive" : "non recursive"), baseFolder);

        ItemTree itemTree = new ItemTree(baseFolder, item -> {
            if (item.isFolder()) {
                return true;
            }
            String path = item.getRepoPath().getPath();
            return MavenNaming.isPom(path) || MavenNaming.isUniqueSnapshot(path);
        });
        ItemNode rootNode = itemTree.getRootNode();
        if (rootNode != null) {
            calculateAndSet(rootNode);
            long duration = System.nanoTime() - start;
            log.debug("Finished {} maven metadata calculation on '{}' in {} ms",
                    (recursive ? "recursive" : "non recursive"), baseFolder, TimeUnit.NANOSECONDS.toMillis(duration));
        } else {
            log.debug("Root path for metadata calculation not found: '{}'", baseFolder);
            removeMetadataIfExist(baseFolder);
        }
        return status;
    }

    private void calculateAndSet(ItemNode treeNode) {
        long startNanos = System.nanoTime();
        ItemInfo itemInfo = treeNode.getItemInfo();
        if (!itemInfo.isFolder()) {
            // Nothing to do here for non folder tree node
            return;
        }

        RepoPath repoPath = itemInfo.getRepoPath();
        boolean success = false;
        FolderMetadataResult result = FolderMetadataResult.EMPTY;
        try {
            String nodePath = repoPath.getPath();
            if (MavenNaming.isSnapshot(nodePath)) {
                // if this folder contains snapshots create snapshots maven.metadata
                log.trace("Detected snapshots container: {}", nodePath);
                result = createSnapshotsMetadata(repoPath, treeNode);
            } else {
                result = calculateVersionsMetadata(repoPath, treeNode);
            }

            if (!result.containsMetadataInfo()) {
                // note: this will also remove plugins metadata. not sure it should
                removeMetadataIfExist(repoPath);
            }
            success = true;
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            log.trace("Maven metadata calculation total time is:{}", durationMs);
            recordIndexingMetrics(durationMs, success, result);
        }

        // Recursive call to calculate and set if recursive calc is on. Done outside the timed block so each child
        // folder records its own per-folder metric independently of the parent's duration.
        if (success && recursive) {
            List<ItemNode> children = treeNode.getChildren();
            if (children != null) {
                children.forEach(this::calculateAndSet);
            }
        }
    }

    /**
     * Builds maven-metadata for a folder that contains child version subfolders.
     * RTDEV-74034: behind {@code mvn.metadata.streamingDedup.enabled} (default TRUE), the streamer folds
     * rows into a per-version max map so the full result set is never materialized. The flag is a kill
     * switch back to the legacy List-materializing path.
     */
    private FolderMetadataResult calculateVersionsMetadata(RepoPath repoPath, ItemNode treeNode) {
        if (ConstantValues.mvnMetadataStreamingDedupEnabled.getBoolean()) {
            Map<String, MavenMetaDataInfo> distinctVersions = getSubFoldersContainingPoms(treeNode);
            return createVersionsMetadata(repoPath, distinctVersions.values());
        }
        List<MavenMetaDataInfo> poms = getSubFoldersContainingPomsLegacy(treeNode);
        return createVersionsMetadataLegacy(repoPath, poms);
    }

    /**
     * Records per-folder indexing metrics for one {@code calculateAndSet()} invocation. Failures emit only
     * the duration timer (with {@code result=failure}); successes additionally publish version count and
     * generated-index byte size when a {@code maven-metadata.xml} was written.
     *
     * Skipped entirely when the calculator was constructed via the legacy constructor that doesn't supply
     * a {@link PackageIndexMetrics} or {@link PackageType}, so callers that don't care about metrics
     * (e.g. unit tests) are unaffected.
     */
    private void recordIndexingMetrics(long durationMs, boolean success, FolderMetadataResult result) {
        if (packageIndexMetrics == null || packageType == null) {
            return;
        }
        try {
            Long versionCount = success && result.versionCount() >= 0 ? (long) result.versionCount() : null;
            Long sizeBytes = success && result.sizeBytes() > 0 ? result.sizeBytes() : null;
            packageIndexMetrics.recordAll(packageType, RepoType.LOCAL, trigger, durationMs, success,
                    versionCount, sizeBytes);
        } catch (RuntimeException e) {
            log.warn("Failed to record Maven metadata indexing metrics for {} (trigger={})", packageType, trigger, e);
        }
    }

    /**
     * Outcome of processing a single folder in {@link #calculateAndSet(ItemNode)}.
     *
     * @param containsMetadataInfo whether a {@code maven-metadata.xml} should exist for this folder (drives the
     *                             {@code removeMetadataIfExist} fallback in the caller)
     * @param versionCount         number of distinct versions written into the metadata, or {@code -1} when not
     *                             applicable (e.g. snapshot folders, where this concept does not map cleanly)
     * @param sizeBytes            size in bytes of the generated {@code maven-metadata.xml}, or {@code 0} when no
     *                             file was written
     */
    private record FolderMetadataResult(boolean containsMetadataInfo, int versionCount, long sizeBytes) {
        static final FolderMetadataResult EMPTY = new FolderMetadataResult(false, -1, 0);
    }

    private FolderMetadataResult createSnapshotsMetadata(RepoPath repoPath, ItemNode treeNode) {
        if (!folderContainsPoms(treeNode)) {
            return FolderMetadataResult.EMPTY;
        }
        List<ItemNode> folderItems = treeNode.getChildren();
        Iterable<ItemNode> poms = folderItems.stream()
                .filter(input -> (input != null) && MavenNaming.isPom(input.getItemInfo().getName()))
                .toList();

        RepoPath firstPom = poms.iterator().next().getRepoPath();
        MavenArtifactInfo artifactInfo = MavenArtifactInfo.fromRepoPath(firstPom);
        if (!artifactInfo.isValid()) {
            // Folder is a snapshot container with poms but the first pom isn't a valid maven artifact; no metadata
            // file is produced, but we should not delete an existing one (preserves prior behaviour).
            return new FolderMetadataResult(true, -1, 0);
        }
        Metadata metadata = new Metadata();
        metadata.setGroupId(artifactInfo.getGroupId());
        metadata.setModelVersion(artifactInfo.getModelVersion());
        metadata.setArtifactId(artifactInfo.getArtifactId());
        String baseVersion = StringUtils.substringBefore(artifactInfo.getVersion(), "-");
        metadata.setVersion(baseVersion + MavenNaming.SNAPSHOT_SUFFIX);
        Versioning versioning = new Versioning();
        metadata.setVersioning(versioning);
        versioning.setLastUpdatedTimestamp(new Date());
        Snapshot snapshot = new Snapshot();
        versioning.setSnapshot(snapshot);

        LocalRepoDescriptor localRepoDescriptor =
                getRepositoryService().localFederatedOrCachedRepoDescriptorByKey(repoPath.getRepoKey());
        SnapshotVersionBehaviorDescriptor snapshotBehavior = localRepoDescriptor.getSnapshotVersionBehavior();
        String latestUniquePom = getLatestUniqueSnapshotPomName(poms);
        if (snapshotBehavior.equals(SnapshotVersionBehaviorDescriptor.NONUNIQUE) ||
                (snapshotBehavior.equals(SnapshotVersionBehaviorDescriptor.DEPLOYER) && latestUniquePom == null)) {
            snapshot.setBuildNumber(1);
        } else if (snapshotBehavior.equals(SnapshotVersionBehaviorDescriptor.UNIQUE)) {
            // take the latest unique snapshot file file
            if (latestUniquePom != null) {
                snapshot.setBuildNumber(MavenNaming.getUniqueSnapshotVersionBuildNumber(latestUniquePom));
                snapshot.setTimestamp(MavenNaming.getUniqueSnapshotVersionTimestamp(latestUniquePom));
            }

            if (ConstantValues.mvnMetadataVersion3Enabled.getBoolean()) {
                List<SnapshotVersion> snapshotVersions = Lists.newArrayList(getFolderItemSnapshotVersions(folderItems));
                if (!snapshotVersions.isEmpty()) {
                    versioning.setSnapshotVersions(snapshotVersions);
                }
            }
        }
        long sizeBytes = saveMetadata(repoPath, metadata);
        return new FolderMetadataResult(true, -1, sizeBytes);
    }

    private Collection<SnapshotVersion> getFolderItemSnapshotVersions(Collection<ItemNode> folderItems) {
        List<SnapshotVersion> snapshotVersionsToReturn = Lists.newArrayList();

        Map<SnapshotVersionType, ModuleInfo> latestSnapshotVersions = Maps.newHashMap();

        for (ItemNode folderItem : folderItems) {
            String folderItemPath = folderItem.getItemInfo().getRelPath();
            if (MavenNaming.isUniqueSnapshot(folderItemPath)) {
                ModuleInfo folderItemModuleInfo;
                if (MavenNaming.isPom(folderItemPath)) {
                    folderItemModuleInfo = ModuleInfoUtils.moduleInfoFromDescriptorPath(folderItemPath,
                            RepoLayoutUtils.MAVEN_2_DEFAULT);
                } else {
                    folderItemModuleInfo = ModuleInfoUtils.moduleInfoFromArtifactPath(folderItemPath,
                            RepoLayoutUtils.MAVEN_2_DEFAULT);
                }
                if (!folderItemModuleInfo.isValid() || !folderItemModuleInfo.isIntegration()) {
                    continue;
                }
                SnapshotVersionType folderItemSnapshotVersionType = new SnapshotVersionType(
                        folderItemModuleInfo.getExt(), folderItemModuleInfo.getClassifier());
                if (latestSnapshotVersions.containsKey(folderItemSnapshotVersionType)) {
                    SnapshotComparator snapshotComparator = createSnapshotComparator();
                    ModuleInfo latestSnapshotVersion = latestSnapshotVersions.get(folderItemSnapshotVersionType);
                    if (snapshotComparator.compare(folderItemModuleInfo, latestSnapshotVersion) > 0) {
                        latestSnapshotVersions.put(folderItemSnapshotVersionType, folderItemModuleInfo);
                    }
                } else {
                    latestSnapshotVersions.put(folderItemSnapshotVersionType, folderItemModuleInfo);
                }
            }
        }

        for (ModuleInfo latestSnapshotVersion : latestSnapshotVersions.values()) {
            SnapshotVersion snapshotVersion = new SnapshotVersion();
            snapshotVersion.setClassifier(latestSnapshotVersion.getClassifier());
            snapshotVersion.setExtension(latestSnapshotVersion.getExt());

            String fileItegRev = latestSnapshotVersion.getFileIntegrationRevision();
            snapshotVersion.setVersion(latestSnapshotVersion.getBaseRevision() + "-" + fileItegRev);
            snapshotVersion.setUpdated(StringUtils.remove(StringUtils.substringBefore(fileItegRev, "-"), '.'));
            snapshotVersionsToReturn.add(snapshotVersion);
        }

        return snapshotVersionsToReturn;
    }

    /**
     * Builds the {@code maven-metadata.xml} for a folder of version subfolders. {@code distinctVersions}
     * must already be deduplicated (one row per version) — the streamer fold in
     * {@link #getSubFoldersContainingPoms(ItemNode)} and the in-method dedup in
     * {@link #createVersionsMetadataLegacy(RepoPath, List)} both feed this method that contract.
     * If the input is empty (folder has no child version subfolders), returns
     * {@link FolderMetadataResult#EMPTY} so the caller will remove any stale {@code maven-metadata.xml}.
     */
    private FolderMetadataResult createVersionsMetadata(RepoPath repoPath,
            Collection<MavenMetaDataInfo> distinctVersions) {
        if (distinctVersions.isEmpty()) {
            log.trace("Detected versions container: {} without child versions", repoPath.toPath());
            return FolderMetadataResult.EMPTY;
        }
        log.trace("Detected versions container: {} with {} child versions",
                repoPath.toPath(), distinctVersions.size());

        // get artifact info from the first pom
        RepoPath samplePomRepoPath = distinctVersions.iterator().next().getRepoPath();
        MavenArtifactInfo artifactInfo = MavenArtifactInfo.fromRepoPath(samplePomRepoPath);
        if (!artifactInfo.isValid()) {
            // Folder has child versions but the sample pom is invalid; do not write a metadata file, do not
            // remove an existing one either (matches the prior {@code containsMetadataInfo = true} behaviour).
            return new FolderMetadataResult(true, -1, 0);
        }
        Metadata metadata = new Metadata();
        metadata.setGroupId(artifactInfo.getGroupId());
        metadata.setArtifactId(artifactInfo.getArtifactId());
        metadata.setVersion(artifactInfo.getVersion());
        metadata.setModelVersion(artifactInfo.getModelVersion());
        Versioning versioning = new Versioning();
        metadata.setVersioning(versioning);
        versioning.setLastUpdatedTimestamp(new Date());

        // Input is already deduplicated by version (the streamer-fold in getSubFoldersContainingPoms
        // collapses same-version rows on the fly). All that's left is to order by the version comparator.
        List<MavenMetaDataInfo> orderedVersions = new ArrayList<>(distinctVersions);
        orderedVersions.sort(new MyComparator<>());

        // latest is simply the last (be it snapshot or release version)
        String latestVersion = orderedVersions.get(orderedVersions.size() - 1).getVersion();
        versioning.setLatest(latestVersion);

        // Set version as latest
        metadata.setVersion(latestVersion);

        // add the versions to the versioning section
        for (MavenMetaDataInfo sortedVersion : orderedVersions) {
            versioning.addVersion(sortedVersion.getVersion());
        }

        // release is the latest non snapshot version
        for (MavenMetaDataInfo sortedVersion : orderedVersions) {
            String versionNodeName = sortedVersion.getVersion();
            if (!MavenNaming.isSnapshot(versionNodeName)) {
                versioning.setRelease(versionNodeName);
            }
        }

        long sizeBytes = saveMetadata(repoPath, metadata);
        return new FolderMetadataResult(true, orderedVersions.size(), sizeBytes);
    }

    /**
     * Picks the latest unique-snapshot pom by the active {@link SnapshotComparator}, in a single
     * O(n) pass — we only need the maximum, not the full ordering.
     *
     * <p>The previous implementation filtered, then full-sorted, then read
     * {@code list.get(size - 1)} — wasting both the sort work AND re-running
     * {@code MavenNaming.matchUniqueSnapshotVersion} via
     * {@link BuildNumberSnapshotComparator#compare(ItemNode, ItemNode)} on every comparison.
     * On the default comparator (production default, no {@code mvn.metadataSnapshotComparatorFqn}
     * override), we now parse each file name exactly once via {@link MavenNaming#tryParseUniqueSnapshot}
     * and compare primitive build numbers. On equal build numbers the last-encountered pom wins,
     * matching the prior stable-sort semantics.
     */
    @VisibleForTesting
    String getLatestUniqueSnapshotPomName(Iterable<ItemNode> poms) {
        SnapshotComparator comparator = createSnapshotComparator();
        if (comparator instanceof BuildNumberSnapshotComparator) {
            return latestByParsedBuildNumber(poms);
        }
        return latestByComparator(poms, comparator);
    }

    /**
     * Fast path for the default comparator: parse each file name exactly once via
     * {@link MavenNaming#tryParseUniqueSnapshot} and pick the maximum build number — the snapshot
     * regex is invoked at most N times instead of {@code ~2·N·log₂(N)}.
     */
    private static String latestByParsedBuildNumber(Iterable<ItemNode> poms) {
        ItemNode best = null;
        int bestBuildNumber = Integer.MIN_VALUE;
        for (ItemNode pom : poms) {
            MavenNaming.UniqueSnapshotParts parts =
                    pom == null ? null : MavenNaming.tryParseUniqueSnapshot(pom.getName());
            if (parts != null && parts.buildNumber() >= bestBuildNumber) {
                bestBuildNumber = parts.buildNumber();
                best = pom;
            }
        }
        return best == null ? null : best.getName();
    }

    /**
     * Custom-comparator path: keep the pluggable ordering, but still avoid the full sort — a
     * single O(n) max-by-comparator pass is enough to drop the latest snapshot pom name.
     */
    private static String latestByComparator(Iterable<ItemNode> poms, SnapshotComparator comparator) {
        ItemNode best = null;
        for (ItemNode pom : poms) {
            if (pom != null && MavenNaming.isUniqueSnapshotFileName(pom.getName())
                    && (best == null || comparator.compare(pom, best) >= 0)) {
                best = pom;
            }
        }
        return best == null ? null : best.getName();
    }

    @VisibleForTesting
    Map<String, MavenMetaDataInfo> getSubFoldersContainingPoms(ItemNode treeNode) {
        // version metadata is only applicable for folders that contain other folders
        // (child folder is checked to prevent the costly query below)
        if (!treeNode.isFolder()) {
            log.trace("Item is not a folder or has no child folders: {}", treeNode.getRepoPath());
            return new HashMap<>();
        }
        NodePath nodePath = NodePath.fromRepoPath(treeNode.getRepoPath());
        log.trace("Processing folder: {}", nodePath);
        AqlApiItem query = createWithEmptyResults().distinct(false).filter(
                and(
                        repo().equal(nodePath.getRepo()),
                        path().matches(nodePath.getPathName() + "/*"),
                        name().matches("*.pom"),
                        AqlApiItem.depth().equals(nodePath.getDepth() + 2)
                )
        ).include(path(), name(), created());

        // Fold rows directly into a per-version max map so the full result set is never materialized.
        // On large customers this query can return hundreds of thousands of rows (RTDEV-74034); the
        // downstream consumer keeps only one entry per version anyway, so we apply the reduction here
        // while the lazy JDBC cursor is still streaming.
        MavenMetadataVersionComparator versionComparator = createVersionComparator();
        AqlService aqlService = ContextHelper.get().beanForType(AqlService.class);
        AqlLazyResult<? extends AqlRowResult> result = null;
        try {
            long start = System.currentTimeMillis();
            result = aqlService.executeQueryLazy(query);
            AqlLazyObjectResultStreamer<MavenMetaDataInfo> streamer = new AqlLazyObjectResultStreamer<>(result,
                    MavenMetaDataInfo.class);
            Map<String, MavenMetaDataInfo> distinctVersions =
                    collectDistinctVersionsFromStreamer(streamer, nodePath.getRepo(), versionComparator);
            long end = System.currentTimeMillis();
            log.trace("Maven metadata calculation query time is:{}", (end - start));
            return distinctVersions;
        } finally {
            closeResultSet(result);
        }
    }

    /**
     * Drives the lazy AQL streamer and folds each emitted row into a per-version max map. Extracted
     * from {@link #getSubFoldersContainingPoms(ItemNode)} as a test seam so the dedup branches can
     * be exercised without standing up the full AQL row-population pipeline.
     */
    @VisibleForTesting
    Map<String, MavenMetaDataInfo> collectDistinctVersionsFromStreamer(
            AqlLazyObjectResultStreamer<MavenMetaDataInfo> streamer,
            String repoKey,
            MavenMetadataVersionComparator versionComparator) {
        Map<String, MavenMetaDataInfo> distinctVersions = new HashMap<>();
        MavenMetaDataInfo row;
        while ((row = streamer.getRow()) != null) {
            row.setRepo(repoKey);
            String version = row.getVersion();
            MavenMetaDataInfo current = distinctVersions.get(version);
            if (current == null || versionComparator.compare(row, current) > 0) {
                distinctVersions.put(version, row);
            }
        }
        return distinctVersions;
    }

    // --- Legacy path retained behind mvn.metadata.streamingDedup.enabled=false (RTDEV-74034). ---
    // Remove getSubFoldersContainingPomsLegacy, createVersionsMetadataLegacy, the call-site branch
    // in calculateVersionsMetadata, and the ConstantValues entry together once the flag is fully
    // ramped.

    @VisibleForTesting
    List<MavenMetaDataInfo> getSubFoldersContainingPomsLegacy(ItemNode treeNode) {
        if (!treeNode.isFolder()) {
            log.trace("Item is not a folder or has no child folders: {}", treeNode.getRepoPath());
            return Lists.newArrayList();
        }
        NodePath nodePath = NodePath.fromRepoPath(treeNode.getRepoPath());
        log.trace("Processing folder: {}", nodePath);
        AqlApiItem query = createWithEmptyResults().distinct(false).filter(
                and(
                        repo().equal(nodePath.getRepo()),
                        path().matches(nodePath.getPathName() + "/*"),
                        name().matches("*.pom"),
                        AqlApiItem.depth().equals(nodePath.getDepth() + 2)
                )
        ).include(path(), name(), created());

        AqlService aqlService = ContextHelper.get().beanForType(AqlService.class);
        AqlLazyResult<? extends AqlRowResult> result = null;
        try {
            long start = System.currentTimeMillis();
            result = aqlService.executeQueryLazy(query);
            AqlLazyObjectResultStreamer<MavenMetaDataInfo> streamer = new AqlLazyObjectResultStreamer<>(result,
                    MavenMetaDataInfo.class);
            List<MavenMetaDataInfo> items = collectPomsFromStreamer(streamer, nodePath.getRepo());
            long end = System.currentTimeMillis();
            log.trace("Maven metadata calculation query time is:{}", (end - start));
            return items;
        } finally {
            closeResultSet(result);
        }
    }

    /**
     * Drives the lazy AQL streamer and collects every emitted row into a flat list. Extracted from
     * {@link #getSubFoldersContainingPomsLegacy(ItemNode)} as a test seam for symmetry with
     * {@link #collectDistinctVersionsFromStreamer(AqlLazyObjectResultStreamer, String,
     * MavenMetadataVersionComparator)}.
     */
    @VisibleForTesting
    List<MavenMetaDataInfo> collectPomsFromStreamer(
            AqlLazyObjectResultStreamer<MavenMetaDataInfo> streamer,
            String repoKey) {
        List<MavenMetaDataInfo> items = Lists.newArrayList();
        MavenMetaDataInfo row;
        while ((row = streamer.getRow()) != null) {
            row.setRepo(repoKey);
            items.add(row);
        }
        return items;
    }

    /**
     * Legacy version-folder metadata path: dedup the raw row list in-memory, then delegate to the
     * shared {@link #createVersionsMetadata(RepoPath, Collection)} for the actual XML construction.
     * Only the dedup site differs between flag branches; the downstream Metadata build is shared.
     */
    private FolderMetadataResult createVersionsMetadataLegacy(RepoPath repoPath,
            List<MavenMetaDataInfo> mavenPathInfos) {
        MyComparator<MavenMetaDataInfo> comparator = new MyComparator<>();
        Map<String, MavenMetaDataInfo> distinctVersions = new HashMap<>();
        mavenPathInfos.forEach(row -> {
            String version = row.getVersion();
            MavenMetaDataInfo last = distinctVersions.get(version);
            if (last == null || comparator.compare(row, last) > 0) {
                distinctVersions.put(version, row);
            }
        });
        return createVersionsMetadata(repoPath, distinctVersions.values());
    }

    private void closeResultSet(AqlLazyResult<? extends AqlRowResult> result) {
        if (result != null) {
            try {
                result.getResultSet().close();
            } catch (SQLException e) {
                log.error("Failed to close result set", e);
            }
        }
    }

    private boolean folderContainsPoms(ItemNode treeNode) {
        if (!treeNode.isFolder()) {
            return false;
        }

        List<ItemNode> children = treeNode.getChildren();
        for (ItemNode child : children) {
            if (!child.isFolder() && MavenNaming.isPom(child.getName())) {
                return true;
            }
        }

        return false;
    }

    @VisibleForTesting
    void removeMetadataIfExist(RepoPath repoPath) {
        try {
            List<RepoPathImpl> mavenMetadataPaths = List.of(new RepoPathImpl(repoPath, MavenNaming.MAVEN_METADATA_NAME),
                    new RepoPathImpl(repoPath, MavenNaming.MAVEN_METADATA_NAME_SHA512));
            mavenMetadataPaths.forEach(mavenMetadataPath -> removeSingularMetadataIfExist(mavenMetadataPath, repoPath));
        } catch (Exception e) {
            status.error("Error while removing maven metadata from " + repoPath + ".", e, log);
        }
    }

    private void removeSingularMetadataIfExist(RepoPathImpl mavenMetadataPath, RepoPath repoPath) {
        if (getRepositoryService().exists(mavenMetadataPath)) {
            log.debug("metadata {} exists, checking if it should be deleted", mavenMetadataPath);
            String metadataStr = getRepositoryService().getStringContent(mavenMetadataPath);
            boolean delete = shouldDeleteMetadata(repoPath, metadataStr);
            if (delete) {
                log.debug("Deleting {}", mavenMetadataPath);
                getRepositoryService().undeploy(mavenMetadataPath, false, false);
            }
        }
    }

    private boolean shouldDeleteMetadata(RepoPath repoPath, String metadataStr) {
        try {
            Metadata metadata = MavenModelUtils.toMavenMetadata(metadataStr);
            if (isSnapshotMetadataForNonSnapshotPath(repoPath, metadata)) {
                // RTFACT-6242 - don't delete user deployed maven-metadata (maven 2 bug)
                return false;
            }
        } catch (IOException e) {
            // ignore -> delete
        }
        return true;
    }

    private boolean isSnapshotMetadataForNonSnapshotPath(RepoPath repoPath, Metadata metadata) {
        return metadata != null && isSnapshotMavenMetadata(metadata) && !MavenNaming.isSnapshot(repoPath.getPath());
    }

    private boolean isSnapshotMavenMetadata(Metadata metadata) {
        Versioning versioning = metadata.getVersioning();
        if (versioning == null) {
            return false;
        }
        List<SnapshotVersion> snapshots = versioning.getSnapshotVersions();
        return snapshots != null && !snapshots.isEmpty();
    }

    private static class SnapshotVersionType {

        private final String extension;
        private final String classifier;

        private SnapshotVersionType(String extension, String classifier) {
            this.extension = extension;
            this.classifier = classifier;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof SnapshotVersionType that)) {
                return false;
            }

            return classifier != null ? classifier.equals(that.classifier) : that.classifier == null &&
                    (Objects.equals(extension, that.extension));

        }

        @Override
        public int hashCode() {
            int result = extension != null ? extension.hashCode() : 0;
            result = 31 * result + (classifier != null ? classifier.hashCode() : 0);
            return result;
        }
    }

    private class MyComparator<T extends MavenMetaDataInfo> implements Comparator<T> {

        private final MavenMetadataVersionComparator comparator;

        MyComparator() {
            comparator = createVersionComparator();
        }

        @Override
        public int compare(T o1, T o2) {
            return comparator.compare(o1, o2);
        }
    }
}
