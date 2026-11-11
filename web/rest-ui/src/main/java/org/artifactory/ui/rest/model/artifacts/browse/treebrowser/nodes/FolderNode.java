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

package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes;

import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.fs.FolderInfo;
import org.artifactory.fs.ItemInfo;

import java.util.List;

/**
 * @author Chen Keinan
 */
public class FolderNode extends BaseNode {

    private String type = "folder";
    private boolean compacted;

    public FolderNode(FolderInfo folderInfo, String text, String repoType) {
        super(folderInfo.getRepoPath());
        setLocal(true);
        setHasChild(true);
        setRepoType(repoType);
        setText(text);
    }

    // TODO: [by sy] this can be improved for large folders
    public FolderNode fetchNextChild() {
        RepositoryService repoService = ContextHelper.get().beanForType(RepositoryService.class);
        List<ItemInfo> items = repoService.getChildren(repoPath);
        if (items.size() != 1) {
            return null;
        }
        // Single folder child
        ItemInfo singleItem = items.get(0);
        if (!singleItem.isFolder()) {
            return null;
        }
        return new FolderNode((FolderInfo) singleItem, singleItem.getName(), getRepoType());
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isCompacted() {
        return compacted;
    }

    public void setCompacted(boolean compacted) {
        this.compacted = compacted;
    }

    @Override
    public boolean isFolder() {
        return true;
    }
}