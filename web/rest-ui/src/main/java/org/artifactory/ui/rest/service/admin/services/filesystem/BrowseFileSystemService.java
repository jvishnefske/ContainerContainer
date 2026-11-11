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

package org.artifactory.ui.rest.service.admin.services.filesystem;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.PathValidatorUtil;
import org.artifactory.common.ConstantValues;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.services.filesystem.FileSystemBrowser;
import org.artifactory.ui.rest.model.admin.services.filesystem.FileSystemItem;
import org.artifactory.util.AolUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Chen Keinan
 */
@Component
public class BrowseFileSystemService implements RestService {
    private static final Logger log = LoggerFactory.getLogger(BrowseFileSystemService.class);

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        AolUtils.assertNotAol("BrowseFileSystem");
        boolean includeZipFiles = Boolean.parseBoolean(request.getQueryParamByKey("includeZip"));
        String selectedPath = request.getQueryParamByKey("path");
        String mode = request.getQueryParamByKey("mode");
        List<String> allowedPaths = resolveAllowlistForMode(mode);
        FileSystemBrowser fileSystem = getFileOrFoldersList(selectedPath, includeZipFiles, allowedPaths);
        response.iModel(fileSystem);
    }

    /**
     * Get files or folder list 1st level only by path
     *
     * @return list of file system item found
     */
    public FileSystemBrowser getFileOrFoldersList(String selectedPath, boolean includeZipFiles) {
        return getFileOrFoldersList(selectedPath, includeZipFiles, Collections.emptyList());
    }

    /**
     * Get files or folder list 1st level only by path, scoped to the {@code allowedPaths} if non-empty
     *
     * @return list of file system item found
     */
    public FileSystemBrowser getFileOrFoldersList(String selectedPath, boolean includeZipFiles,
            List<String> allowedPaths) {
        boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
        FileSystemBrowser fileSystemBrowser = new FileSystemBrowser();
        fileSystemBrowser.setWindows(isWin);
        boolean scoped = allowedPaths != null && !allowedPaths.isEmpty()
                && allowedPaths.stream().anyMatch(StringUtils::isNotBlank);
        if (scoped) {
            updateAllowedRoots(fileSystemBrowser, allowedPaths);
        } else {
            File[] fsRoots = File.listRoots();
            updateFileSystemRoots(fileSystemBrowser, fsRoots);
        }
        updateRootsData(selectedPath, fileSystemBrowser, includeZipFiles, scoped ? allowedPaths : null);
        return fileSystemBrowser;
    }

    /**
     * Resolves the allowlist for the requested mode ({@code import}, {@code export} or
     * {@code backup}). Returns an empty list (no scoping) when the mode is absent/unknown or
     * when no allowlist is configured.
     */
    private List<String> resolveAllowlistForMode(String mode) {
        if ("import".equalsIgnoreCase(mode)) {
            return PathValidatorUtil.resolveAllowedPaths(
                    PathValidatorUtil.YAML_KEY_IMPORT_ALLOWED_PATHS, ConstantValues.importAllowedPaths);
        }
        if ("export".equalsIgnoreCase(mode)) {
            return PathValidatorUtil.resolveAllowedPaths(
                    PathValidatorUtil.YAML_KEY_EXPORT_ALLOWED_PATHS, ConstantValues.exportAllowedPaths);
        }
        if ("backup".equalsIgnoreCase(mode)) {
            return PathValidatorUtil.resolveAllowedPaths(
                    PathValidatorUtil.YAML_KEY_BACKUP_ALLOWED_PATHS, ConstantValues.backupAllowedPaths);
        }
        return Collections.emptyList();
    }

    /**
     * get root file system and update files system browser model
     *
     * @param selectedPath      - selected path
     * @param fileSystemBrowser - file system browser model
     * @param allowedPaths      - when non-null, only children of paths inside this allowlist are listed
     */
    private void updateRootsData(String selectedPath, FileSystemBrowser fileSystemBrowser,
            boolean includeZipFiles, List<String> allowedPaths) {
        if (StringUtils.isBlank(selectedPath)) {
            List<String> roots = fileSystemBrowser.getRoots();
            if (roots != null && !roots.isEmpty()) {
                selectedPath = roots.getFirst();
            }
        }
        List<FileSystemItem> fileSystemItems = Lists.newArrayList();
        if (allowedPaths != null && !PathValidatorUtil.isInsideAllowedPaths(selectedPath, allowedPaths)) {
            log.debug("Path '{}' is outside the configured allowlist; returning empty listing.", selectedPath);
            fileSystemBrowser.setFileSystemItems(fileSystemItems);
            return;
        }
        File[] children = new File(selectedPath).listFiles();
        if (children != null) {
            try {
                Arrays.asList(children).forEach(
                        file -> addFolderAndZipFileToList(file, includeZipFiles, fileSystemItems));
            } catch (Exception e) {
                log.error("error with retrieving folder data , bad path name:" + selectedPath, e);
            }
        } else {
            log.debug("Cannot read directory content from: '{}'", selectedPath);
        }
        fileSystemBrowser.setFileSystemItems(sort(fileSystemItems));
    }

    private void updateAllowedRoots(FileSystemBrowser fileSystemBrowser, List<String> allowedPaths) {
        List<String> roots = new ArrayList<>();
        for (String raw : allowedPaths) {
            String trimmed = raw == null ? "" : raw.trim();
            if (!trimmed.isEmpty()) {
                roots.add(trimmed);
            }
        }
        Collections.sort(roots);
        fileSystemBrowser.setRoots(roots);
    }

    private void addFolderAndZipFileToList(File file, boolean includeZipFiles, List<FileSystemItem> fileSystemItems) {
        if (includeZipFiles) {
            if (file.isDirectory() || file.getName().endsWith(".zip")) {
                fileSystemItems.add(new FileSystemItem(file));
            }
        } else {
            if (file.isDirectory()) {
                fileSystemItems.add(new FileSystemItem(file));
            }
        }
    }

    private List<FileSystemItem> sort(List<FileSystemItem> fileSystemItems) {
        fileSystemItems.sort((o1, o2) -> {
            if (o1 == null) {
                if (o2 == null) { return 0; }
                return -1;
            }
            if (o2 == null) { return 1; }
            if (o1.isFolder() && !o2.isFolder()) { return -1; }
            if (!o1.isFolder() && o2.isFolder()) { return 1; }
            return o1.getFileSystemItemName().compareToIgnoreCase(o2.getFileSystemItemName());
        });
        return fileSystemItems;
    }

    /**
     * update file system roots
     *
     * @param fileSystemBrowser - file system browser model
     */
    private void updateFileSystemRoots(FileSystemBrowser fileSystemBrowser, File[] fsRoots) {
        List<String> roots = null;
        if (fsRoots != null && fsRoots.length > 0) {
            roots = new ArrayList<>();
            for (File file : fsRoots) {
                roots.add(file.getAbsolutePath());
            }
            Collections.sort(roots);
        }
        fileSystemBrowser.setRoots(roots);
    }
}
