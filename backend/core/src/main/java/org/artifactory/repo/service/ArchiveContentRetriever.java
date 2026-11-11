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

package org.artifactory.repo.service;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.ArchiveFileContent;
import org.artifactory.common.ConstantValues;
import org.artifactory.mime.NamingUtils;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.util.ZipUtils;
import org.jfrog.client.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * Retrieves a text content from an archive.
 *
 * @author Yossi Shaul
 */
public class ArchiveContentRetriever {
    private static final Logger log = LoggerFactory.getLogger(ArchiveContentRetriever.class);

    public ArchiveFileContent getArchiveFileContent(LocalRepo<?, ?> repo, RepoPath archivePath, String archiveEntryPath)
            throws IOException {
        return getArchiveFileContentInternal(repo, archivePath, archiveEntryPath);
    }


    public ArchiveFileContent getGenericArchiveFileContent(LocalRepo<?, ?> repo, RepoPath archivePath,
            String archiveEntryPath)
            throws IOException {
        validateParameters(repo, archivePath, archiveEntryPath);
        return getArchiveFileContentInternal(repo, archivePath, archiveEntryPath);
    }

    protected ArchiveFileContent getArchiveFileContentInternal(LocalRepo<?, ?> repo, RepoPath archivePath,
            String archiveEntryPath) throws IOException {
        if (repo == null) {
            return ArchiveFileContent.contentNotFound("Repository '" + archivePath.getRepoKey() + "' not found");
        }
        SearchListResult searchListResult = buildSearchList(archivePath, archiveEntryPath);
        if (searchListResult.failureReason != null) {
            return ArchiveFileContent.contentNotFound(searchListResult.failureReason);
        }

        ContentResult contentResult = processSearchList(repo, searchListResult.searchList,
                searchListResult.sourceEntryPath);

        if (contentResult.content != null) {
            return new ArchiveFileContent(contentResult.content,
                    InternalRepoPathFactory.create(repo.getKey(), contentResult.sourceJarPath),
                    contentResult.sourceEntryPath);
        } else {
            return ArchiveFileContent.contentNotFound(contentResult.failureReason);
        }
    }

    protected void validateParameters(LocalRepo<?, ?> repo, RepoPath archivePath, String archiveEntryPath) {
        if (repo == null) {
            throw new IllegalArgumentException("Repository cannot be null");
        }
        if (archivePath == null) {
            throw new IllegalArgumentException("Archive path cannot be null");
        }
        if (archiveEntryPath == null) {
            throw new IllegalArgumentException("Archive entry path cannot be null");
        }
    }

    protected SearchListResult buildSearchList(RepoPath archivePath, String archiveEntryPath) {
        SearchListResult result = new SearchListResult();
        if (archiveEntryPath.endsWith(".class")) {
            result.sourceEntryPath = NamingUtils.javaSourceNameFromClassName(archiveEntryPath);
            String sourceJarName = PathUtils.stripExtension(archivePath.getName()) + "-sources." +
                    PathUtils.getExtension(archivePath.getName());
            String sourcesJarPath = archivePath.getParent().getPath() + "/" + sourceJarName;
            result.searchList = Lists.<String>newArrayList(sourcesJarPath, archivePath.getPath());
        } else if (isTextFile(archiveEntryPath)) {
            result.searchList = Lists.<String>newArrayList(archivePath.getPath());
            result.sourceEntryPath = archiveEntryPath;
        } else {
            result.failureReason = "View source for " + archiveEntryPath + " is not supported";
        }
        return result;
    }

    protected ContentResult processSearchList(LocalRepo<?, ?> repo, List<String> searchList, String sourceEntryPath)
            throws IOException {
        ContentResult result = new ContentResult();
        for (String sourcesJarPath : searchList) {
            if (result.content != null) {
                break;
            }
            log.debug("Looking for {} source in {}", sourceEntryPath, sourcesJarPath);
            ContentResult fileResult = processSourceFile(repo, sourcesJarPath, sourceEntryPath);
            if (fileResult.content != null) {
                result.content = fileResult.content;
                result.sourceJarPath = sourcesJarPath;
                result.sourceEntryPath = fileResult.sourceEntryPath;
            } else {
                result.failureReason = fileResult.failureReason;
            }
        }
        return result;
    }

    protected ContentResult processSourceFile(LocalRepo<?, ?> repo, String sourcesJarPath, String sourceEntryPath)
            throws IOException {
        ContentResult result = new ContentResult();
        VfsFile<?> sourceFile = repo.getImmutableFile(new RepoPathImpl(repo.getKey(), sourcesJarPath));
        if (sourceFile == null) {
            result.failureReason = "Source jar not found.";
            return result;
        }

        RepoPath sourceRepoPath = InternalRepoPathFactory.create(repo.getKey(), sourcesJarPath);
        if (!ContextHelper.get().getAuthorizationService().canRead(sourceRepoPath)) {
            result.failureReason = "No read permissions for the source jar.";
            return result;
        }

        return readArchiveContent(sourceFile, sourceEntryPath);
    }

    @SuppressWarnings("unchecked")
    protected ContentResult readArchiveContent(VfsFile<?> sourceFile, String sourceEntryPath) throws IOException {
        ContentResult result = new ContentResult();
        List<String> alternativeExtensions = getAlternativeExtensions(sourceEntryPath);
        ArchiveInputStream<? extends ArchiveEntry> jis = null;
        try {
            jis = ZipUtils.getArchiveInputStream(sourceFile);
            ArchiveEntry zipEntry = ZipUtils.locateArchiveEntry(jis, sourceEntryPath, alternativeExtensions);
            if (zipEntry == null) {
                result.failureReason = "Source file not found.";
                return result;
            }

            int maxAllowedSize = 1024 * 1024;
            if (zipEntry.getSize() > maxAllowedSize) {
                result.failureReason = String.format(
                        "Source file is too big to render: file size: %s, max size: %s.",
                        zipEntry.getSize(), maxAllowedSize);
                return result;
            }

            result.content = IOUtils.toString(jis, StandardCharsets.UTF_8);
            result.sourceEntryPath = zipEntry.getName();
        } finally {
            IOUtils.closeQuietly(jis);
        }
        return result;
    }


    protected List<String> getAlternativeExtensions(String sourceEntryPath) {
        if ("java".equalsIgnoreCase(PathUtils.getExtension(sourceEntryPath))) {
            return Lists.<String>newArrayList("groovy", "fx");
        }
        return Lists.<String>newArrayList();
    }

    protected static class SearchListResult {
        List<String> searchList;
        String sourceEntryPath;
        String failureReason;
    }

    protected static class ContentResult {
        String content;
        String sourceJarPath;
        String sourceEntryPath;
        String failureReason;
    }


    private boolean isTextFile(String fileName) {
        return NamingUtils.isViewable(fileName) || isLicenseFile(fileName);
    }

    private boolean isLicenseFile(String fileName) {
        String licenseFileNames = ConstantValues.archiveLicenseFileNames.getString();
        Set<String> possibleLicenseFileNames = Sets.newHashSet(
                Iterables.transform(Sets.newHashSet(StringUtils.split(licenseFileNames, ",")),
                        new Function<String, String>() {
                            @Override
                            public String apply(@Nullable String input) {
                                return StringUtils.isBlank(input) ? input : StringUtils.trim(input);
                            }
                        }
                )
        );
        return possibleLicenseFileNames.contains(PathUtils.getFileName(fileName));
    }
}
