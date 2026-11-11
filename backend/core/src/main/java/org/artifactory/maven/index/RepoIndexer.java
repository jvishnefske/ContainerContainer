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
/*
 * Additional contributors:
 *    JFrog Ltd.
 */

package org.artifactory.maven.index;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.FSDirectory;
import org.apache.maven.index.ArtifactContext;
import org.apache.maven.index.ArtifactScanningListener;
import org.apache.maven.index.DefaultIndexer;
import org.apache.maven.index.DefaultIndexerEngine;
import org.apache.maven.index.DefaultQueryCreator;
import org.apache.maven.index.DefaultScannerListener;
import org.apache.maven.index.DefaultSearchEngine;
import org.apache.maven.index.IndexerEngine;
import org.apache.maven.index.QueryCreator;
import org.apache.maven.index.ScanningRequest;
import org.apache.maven.index.ScanningResult;
import org.apache.maven.index.SearchEngine;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.incremental.DefaultIncrementalHandler;
import org.apache.maven.index.packer.DefaultIndexPacker;
import org.apache.maven.index.packer.IndexPacker;
import org.apache.maven.index.packer.IndexPackingRequest;
import org.apache.maven.index.updater.DefaultIndexUpdater;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.fs.RepoResource;
import org.artifactory.io.TempFileStreamHandle;
import org.artifactory.maven.index.creator.VfsJarFileContentsIndexCreator;
import org.artifactory.maven.index.creator.VfsMavenPluginArtifactInfoIndexCreator;
import org.artifactory.maven.index.creator.VfsMinimalArtifactInfoIndexCreator;
import org.artifactory.mime.MavenNaming;
import org.artifactory.repo.StoringRepo;
import org.artifactory.request.NullRequestContext;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.schedule.TaskInterruptedException;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.storage.fs.tree.ItemTree;
import org.artifactory.storage.fs.tree.file.JavaIOFileAdapter;
import org.artifactory.util.Files;
import org.jfrog.common.ArgUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author yoavl
 * @author yossis
 * @author Nadav Yogev
 * @author Michael Akushsky
 */
@Slf4j
public class RepoIndexer extends DefaultIndexer implements ArtifactScanningListener {

    private final StoringRepo repo;
    private IndexingContext context;
    private final IndexPacker packer;
    private final ArtifactoryContentScanner scanner;
    private final IndexerEngine indexerEngine;

    private static final String CREATE_INDEX_DIR_MESSAGE = "Creating index directory named %s for repository %s";
    private static final String REMOVE_INDEX_DIR_MESSAGE = "Removing index directory named %s for repository %s";

    public RepoIndexer(StoringRepo repo) {
        this(new DefaultSearchEngine(), new DefaultIndexerEngine(), new DefaultQueryCreator(), repo);
    }

    private RepoIndexer(SearchEngine searchEngine, IndexerEngine indexerEngine, QueryCreator queryCreator,
            StoringRepo repo) {
        super(searchEngine, indexerEngine, queryCreator);
        this.indexerEngine = indexerEngine;

        this.repo = ArgUtils.requireNonNull(repo, "Repo cannot be null");
        packer = new DefaultIndexPacker(new DefaultIncrementalHandler());
        scanner = new ArtifactoryContentScanner(new ArtifactoryArtifactContextProducer());
    }

    @Override
    public void scanningStarted(IndexingContext ctx) {
        log.debug("Scanning started for repo: '{}'", repo.getKey());
    }

    @Override
    public void scanningFinished(IndexingContext ctx, ScanningResult result) {
        log.debug("Scanning finished for repo: '{}'", repo.getKey());
    }

    @Override
    public void artifactError(ArtifactContext ac, Exception e) {
        if (ac != null) {
            log.debug("Error indexing artifact: '{}'", ac.getArtifactInfo().getUinfo(), e);
        } else {
            log.debug("Error indexing artifact", e);
        }
    }

    @Override
    public void artifactDiscovered(ArtifactContext ac) {
        if (log.isTraceEnabled()) {
            log.trace("Artifact discovered: '{}'", ac.getArtifactInfo().getUinfo());
        }
        if (TaskUtils.pauseOrBreak()) {
            throw new TaskInterruptedException();
        }
        //Be nice with other threads
        Thread.yield();
    }

    @SuppressWarnings({"UnusedDeclaration"})
    RepoIndex index(Date fireTime) {
        //Use a file based dir with a temp file to conserve memory
        ArtifactoryHome artifactoryHome = ContextHelper.get().getArtifactoryHome();
        File dir = Files.createRandomDir(artifactoryHome.getTempWorkDir(), "artifactory.index." + repo.getKey());
        try {
            log.debug(String.format(CREATE_INDEX_DIR_MESSAGE, dir.getAbsolutePath(), repo.getKey()));
            createContext(dir);
            return createIndex(true);
        } catch (Exception e) {
            throw new RuntimeException("Indexing failed.", e);
        } finally {
            //Remove the temp index dir and files
            removeTempIndexFiles(dir);
        }
    }


    RepoIndex createIndex(boolean scan) {
        IndexSearcher searcher = null;
        try {
            context.updateTimestamp();

            if (scan) {
                scanRepo();
            }
            File tempWorkDir = createTempWorkDir();
            searcher = context.acquireIndexSearcher();
            IndexPackingRequest request = newIndexPackingRequest(tempWorkDir, searcher);
            //Pack - will create the index files inside the folder
            packer.packIndex(request);
            //Return the handle to the zip file (will be removed when the handle is closed)
            TempFileStreamHandle zipIndexHandle = getTempFileStreamHandle(tempWorkDir, MavenNaming.NEXUS_INDEX_GZ,
                    "index file");
            TempFileStreamHandle propertiesHandle = getTempFileStreamHandle(tempWorkDir,
                    MavenNaming.NEXUS_INDEX_PROPERTIES, "properties file");
            return new RepoIndex(zipIndexHandle, propertiesHandle, tempWorkDir);
        } catch (Exception e) {
            throw new RuntimeException("Index creation failed.", e);
        } finally {
            if (searcher != null) {
                try {
                    log.debug("Releasing searcher for repo: '{}'", repo.getKey());
                    context.releaseIndexSearcher(searcher);
                } catch (IOException e) {
                    log.warn("Unable to release searcher", e);
                }
            }
        }
    }

    @NonNull
    private static TempFileStreamHandle getTempFileStreamHandle(File outputFolder, String filename, String type)
            throws FileNotFoundException {
        File propertiesFile = new File(outputFolder, filename);
        if (!propertiesFile.exists()) {
            throw new RuntimeException(String.format("Temp %s '%s' does not exist.", type,
                    propertiesFile.getAbsolutePath()));
        }
        return new TempFileStreamHandle(propertiesFile);
    }

    private void scanRepo() {
        //Update the dir content by scanning the repo
        scanner.scan(new ScanningRequest(context,
                new DefaultScannerListener(context, indexerEngine, true, this), null));
    }

    void mergeInto(StoringRepo localRepo, Map<StoringRepo, FSDirectory> extractedRepoIndexes)
            throws RepoRejectException, IOException {
        File indexUnzippedDir = null;
        try {
            indexUnzippedDir = createTempDir();
            try (FSDirectory repoToMergeIndexDir = getIndexDir(localRepo, extractedRepoIndexes, indexUnzippedDir)) {
                if (repoToMergeIndexDir == null) {
                    //No local index exists
                    return;
                }
                mergeRepo(localRepo, repoToMergeIndexDir);
            }
        } finally {
            removeDirectory(indexUnzippedDir);
        }
    }

    private static void removeDirectory(File indexUnzippedDir) {
        if (indexUnzippedDir != null) {
            try {
                log.debug(String.format(REMOVE_INDEX_DIR_MESSAGE, indexUnzippedDir.getAbsolutePath(), ""));
                logIndexDirFiles(indexUnzippedDir);
                FileUtils.deleteDirectory(indexUnzippedDir);
            } catch (IOException e) {
                log.warn("Unable to remove temp directory {}", indexUnzippedDir, e);
            }
        }
    }

    @NonNull
    private File createTempDir() {
        ArtifactoryHome artifactoryHome = ContextHelper.get().getArtifactoryHome();
        File indexUnzippedDir = Files.createRandomDir(artifactoryHome.getTempWorkDir(),
                "artifactory.merged-index." + repo.getKey());
        indexUnzippedDir.deleteOnExit();
        log.debug(String.format(CREATE_INDEX_DIR_MESSAGE, indexUnzippedDir.getAbsolutePath(), repo.getKey()));
        return indexUnzippedDir;
    }

    @NonNull
    private File createTempWorkDir() {
        ArtifactoryHome artifactoryHome = ContextHelper.get().getArtifactoryHome();
        File d = Files.createRandomDir(artifactoryHome.getTempWorkDir(),
                "artifactory.index." + repo.getKey());
        log.debug(String.format(CREATE_INDEX_DIR_MESSAGE, d.getAbsolutePath(), repo.getKey()));
        return d;
    }

    /**
     * Merge the provided index into the repo-specific temp index dir
     */
    private void mergeRepo(StoringRepo localRepo, FSDirectory repoToMergeIndexDir) throws IOException {
        try {
            log.debug("Merging local index of {} into {}.", localRepo, repo);
            context.merge(repoToMergeIndexDir);
        } catch (FileNotFoundException e) {
            //Merged-into directory is new - just copy instead of merging
            log.debug("Target index directory is new: merging is skipped.");
        }
    }

    StoringRepo getRepo() {
        return repo;
    }

    /**
     * Remove the temp index dir and files
     *
     * @param dir The temp index dir
     */
    void removeTempIndexFiles(File dir) {
        if (dir != null) {
            log.debug(String.format(REMOVE_INDEX_DIR_MESSAGE, context.getIndexDirectoryFile().getAbsolutePath(),
                    repo.getKey()));
            logIndexDirFiles(dir);
            closeContextAndDeleteFiles();
            // We have to delete the index dir separately,
            // because the nexus removal tool deletes the files, but leaves the dir.
            FileUtils.deleteQuietly(dir);
        }
    }

    private void closeContextAndDeleteFiles() {
        try {
            closeIndexingContext(context, true);
        } catch (Exception e) {
            log.warn("Could not remove temporary index context '{}'.", context, e);
        }
    }

    void createContext(File indexDir) throws IOException {
        String repoKey = repo.getKey();
        List<IndexCreator> indexCreators = List.of(
                new VfsMinimalArtifactInfoIndexCreator(),
                new VfsJarFileContentsIndexCreator(),
                new VfsMavenPluginArtifactInfoIndexCreator()
        );
        ItemTree itemTree = new ItemTree(repo.getRepoPath(""));
        JavaIOFileAdapter rootFile = new JavaIOFileAdapter(itemTree.getRootNode(), null, true);
        context = createIndexingContext(repoKey, repoKey, rootFile, indexDir, null, null, true, true, indexCreators);
        logIndexDirFiles(indexDir);
    }

    private static void logIndexDirFiles(File indexDir) {
        if (log.isDebugEnabled()) {
            File[] files = indexDir.listFiles();
            if (files != null) {
                log.debug("The files in the index dir are:");
                for (File file : files) {
                    log.debug("File name: {}, File path: {} ", file.getName(), file.getAbsolutePath());
                }
            }
        }
    }

    private IndexPackingRequest newIndexPackingRequest(File outputFolder, IndexSearcher searcher) {
        IndexPackingRequest request = new IndexPackingRequest(context, searcher.getIndexReader(), outputFolder);
        request.setCreateChecksumFiles(false);
        request.setCreateIncrementalChunks(false);
        //create new index format
        request.setFormats(List.of(IndexPackingRequest.IndexFormat.FORMAT_V1));
        return request;
    }

    private FSDirectory getIndexDir(StoringRepo repo, Map<StoringRepo, FSDirectory> extractedRepoIndexes,
            File indexUnzippedDir) throws RepoRejectException, IOException {
        //Check if we need to extract the local index first
        FSDirectory indexDir = extractedRepoIndexes.get(repo);
        if (indexDir == null) {
            //Extraction required
            NullRequestContext requestContext =
                    new NullRequestContext(repo.getRepoPath(MavenNaming.NEXUS_INDEX_GZ_PATH));
            RepoResource indexRes = repo.getInfo(requestContext);
            if (!indexRes.isFound()) {
                log.debug("Cannot find index resource for repository {}", repo);
                return null;
            }
            //Copy the index file
            try (ResourceStreamHandle handle = repo.getResourceStreamHandle(requestContext, indexRes)) {
                indexDir = FSDirectory.open(indexUnzippedDir.toPath());
                //Get the extracted lucene dir
                DefaultIndexUpdater.unpackIndexData(
                        new BufferedInputStream(handle.getInputStream()), 1, indexDir, context);
            }
            //Remember the extracted index
            extractedRepoIndexes.put(repo, indexDir);
        }
        return indexDir;
    }
}
