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

package org.artifactory.ui.rest.model.admin.services.filesystem;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.artifactory.rest.common.model.BaseModel;

import java.io.File;

/**
 * @author Chen Keinan
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class FileSystemItem extends BaseModel {
    private String fileSystemItemName;
    private boolean isFolder;

    public FileSystemItem(@NonNull File file) {
        fileSystemItemName = file.getName();
        isFolder = file.isDirectory();
    }

    /**
     * used to sort files by type and name.
     *
     * if used in a comparator the output will be folders first sorted by name, then files sorted buy names
     * example:
     * -   Folder1
     * -   Folder2
     * -   Zfolder1
     * -   Afile
     * -   Afile2
     * -   Zfile
     */
    public static int compareByTypeAndName(@NonNull FileSystemItem fileA, @NonNull FileSystemItem fileB) {
        if (fileA.isFolder() && !fileB.isFolder()) {
            return -1;
        } else if (!fileA.isFolder() && fileB.isFolder()) {
            return 1;
        } else {
            return fileA.getFileSystemItemName().compareTo(fileB.getFileSystemItemName());
        }
    }
}
