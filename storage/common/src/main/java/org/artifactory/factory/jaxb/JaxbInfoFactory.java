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

package org.artifactory.factory.jaxb;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.factory.JaxbAdapter;
import org.artifactory.factory.common.AbstractInfoFactory;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.FolderInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.MetadataEntryInfo;
import org.artifactory.fs.MutableFileInfo;
import org.artifactory.fs.MutableFolderInfo;
import org.artifactory.fs.MutableItemInfo;
import org.artifactory.fs.MutableStatsInfo;
import org.artifactory.fs.MutableWatcherInfo;
import org.artifactory.fs.MutableWatchersInfo;
import org.artifactory.fs.StatsInfo;
import org.artifactory.fs.WatchersInfo;
import org.artifactory.fs.ZipEntryInfo;
import org.artifactory.fs.ZipEntryResourceInfo;
import org.artifactory.md.MetadataInfo;
import org.artifactory.md.MutableMetadataInfo;
import org.artifactory.md.MutablePropertiesInfo;
import org.artifactory.md.PropertiesInfo;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.ArchiveEntryImpl;
import org.artifactory.model.fs.FileAdditionalInfo;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.model.fs.FolderAdditionalInfo;
import org.artifactory.model.fs.FolderInfoImpl;
import org.artifactory.model.fs.InternalFileInfo;
import org.artifactory.model.fs.InternalFolderInfo;
import org.artifactory.model.fs.MetadataEntry;
import org.artifactory.model.fs.MetadataInfoImpl;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.properties.PropertiesImplNullElement;
import org.artifactory.properties.PropertiesImplWrapper;
import org.artifactory.model.fs.StatsImpl;
import org.artifactory.model.fs.WatcherImpl;
import org.artifactory.model.fs.WatchersImpl;
import org.artifactory.model.fs.ZipEntriesTree;
import org.artifactory.model.fs.ZipEntryImpl;
import org.artifactory.model.fs.ZipEntryResourceImpl;
import org.artifactory.model.security.AceImpl;
import org.artifactory.model.security.BuildPermissionTargetImpl;
import org.artifactory.model.security.GroupImpl;
import org.artifactory.model.security.MutableBuildAclImpl;
import org.artifactory.model.security.MutableReleaseBundleAclImpl;
import org.artifactory.model.security.MutableRepoAclImpl;
import org.artifactory.model.security.ReleaseBundlePermissionTargetImpl;
import org.artifactory.model.security.RepoPermissionTargetImpl;
import org.artifactory.model.security.SecurityDataImpl;
import org.artifactory.model.security.UserGroupImpl;
import org.artifactory.model.security.UserImpl;
import org.artifactory.model.security.UserProperty;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.resource.MutableRepoResourceInfo;
import org.artifactory.resource.RepoResourceInfo;
import org.artifactory.security.AceInfo;
import org.artifactory.security.BuildAcl;
import org.artifactory.security.GroupInfo;
import org.artifactory.security.MutableAceInfo;
import org.artifactory.security.MutableBuildAcl;
import org.artifactory.security.MutableBuildPermissionTarget;
import org.artifactory.security.MutableGroupInfo;
import org.artifactory.security.MutableReleaseBundleAcl;
import org.artifactory.security.MutableReleaseBundlePermissionTarget;
import org.artifactory.security.MutableRepoAcl;
import org.artifactory.security.MutableRepoPermissionTarget;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.ReleaseBundleAcl;
import org.artifactory.security.ReleaseBundlePermissionTarget;
import org.artifactory.security.RepoAcl;
import org.artifactory.security.RepoPermissionTarget;
import org.artifactory.security.SecurityInfo;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.util.Tree;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.zip.ZipEntry;

/**
 * Date: 8/1/11
 * Time: 10:01 PM
 *
 * @author Fred Simon
 */
@SuppressWarnings({"UnusedDeclaration"})
public class JaxbInfoFactory extends AbstractInfoFactory {

    private JaxbAdapter securityJaxbAdapter;
    private JaxbAdapter fileSystemJaxbAdapter;

    @Override
    public RepoPath createRepoPathFromId(String repoPathId) {
        return InternalRepoPathFactory.createRepoPath(repoPathId);
    }

    @Override
    public RepoPath createRepoPath(String repoKey, String path) {
        return new RepoPathImpl(repoKey, path);
    }

    @Override
    public RepoPath createRepoPath(RepoPath parent, String relPath) {
        return new RepoPathImpl(parent, relPath);
    }

    @Override
    public RepoPath createRepoPath(String repoKey, String path, boolean folder) {
        return new RepoPathImpl(repoKey, path, folder);
    }

    @Override
    public MutableRepoResourceInfo copyRepoResource(RepoResourceInfo repoResourceInfo) {
        if (repoResourceInfo == null) {
            return null;
        }
        if (repoResourceInfo instanceof InternalFileInfo) {
            return new FileInfoImpl((InternalFileInfo) repoResourceInfo);
        } else if (repoResourceInfo instanceof MetadataInfo) {
            return new MetadataInfoImpl((MetadataInfo) repoResourceInfo);
        } else if (repoResourceInfo instanceof ZipEntryResourceInfo) {
            throw new IllegalArgumentException(
                    "Cannot copy " + repoResourceInfo + " since zip entries cannot be modified!");
        } else {
            throw new IllegalArgumentException(
                    "Cannot copy " + repoResourceInfo + " of class " + repoResourceInfo.getClass().getName());
        }
    }

    @Override
    public MutableItemInfo copyItemInfo(ItemInfo itemInfo) {
        if (itemInfo == null) {
            return null;
        }
        if (itemInfo instanceof InternalFileInfo) {
            return new FileInfoImpl((InternalFileInfo) itemInfo);
        } else if (itemInfo instanceof InternalFolderInfo) {
            return new FolderInfoImpl((InternalFolderInfo) itemInfo);
        } else if (itemInfo instanceof ZipEntryResourceInfo) {
            throw new IllegalArgumentException(
                    "Cannot copy " + itemInfo + " since zip entries cannot be modified!");
        } else if (itemInfo instanceof MutableItemInfo) {
            return (MutableItemInfo) itemInfo;
        } else {
            throw new IllegalArgumentException(
                    "Cannot copy " + itemInfo + " of class " + itemInfo.getClass().getName());
        }
    }

    @Override
    public MutableFileInfo createFileInfo(RepoPath repoPath) {
        return new FileInfoImpl(repoPath);
    }

    @Override
    public MutableFileInfo createFileInfo(RepoPath repoPath, long id) {
        return new FileInfoImpl(repoPath, id);
    }

    @Override
    public MutableFileInfo copyFileInfo(FileInfo fileInfo) {
        return new FileInfoImpl((InternalFileInfo) fileInfo);
    }

    @Override
    public MutableFolderInfo createFolderInfo(RepoPath repoPath) {
        return new FolderInfoImpl(repoPath);
    }

    @Override
    public MutableFolderInfo createFolderInfo(RepoPath repoPath, long id) {
        return new FolderInfoImpl(repoPath, id);
    }

    @Override
    public MutableFolderInfo copyFolderInfo(FolderInfo folderInfo) {
        return new FolderInfoImpl((InternalFolderInfo) folderInfo);
    }

    @Override
    public MutablePropertiesInfo createProperties() {
        return new PropertiesImpl();
    }

    @Override
    public MutablePropertiesInfo copyProperties(PropertiesInfo copy) {
        return new PropertiesImpl(copy);
    }

    @Override
    public MutableRepoPermissionTarget createRepoPermissionTarget() {
        return new RepoPermissionTargetImpl();
    }

    @Override
    public MutableBuildPermissionTarget createBuildPermissionTarget() {
        return new BuildPermissionTargetImpl();
    }

    @Override
    public MutableReleaseBundlePermissionTarget createReleaseBundlePermissionTarget() {
        return new ReleaseBundlePermissionTargetImpl();
    }

    @Override
    public MutableRepoPermissionTarget copyRepoPermissionTarget(RepoPermissionTarget copy) {
        return new RepoPermissionTargetImpl(copy);
    }

    @Override
    public MutableReleaseBundlePermissionTarget copyReleaseBundlePermissionTarget(ReleaseBundlePermissionTarget copy) {
        return new ReleaseBundlePermissionTargetImpl(copy);
    }

    @Override
    public MutableUserInfo createUser() {
        return new UserImpl();
    }

    @Override
    public MutableUserInfo copyUser(UserInfo copy) {
        return new UserImpl(copy);
    }

    @Override
    public UserGroupInfo createUserGroup(String groupName) {
        return new UserGroupImpl(groupName);
    }

    @Override
    public UserGroupInfo createUserGroup(String groupName, String realm) {
        return new UserGroupImpl(groupName, realm);
    }

    @Override
    public MutableGroupInfo createGroup() {
        return new GroupImpl();
    }

    @Override
    public MutableGroupInfo copyGroup(GroupInfo copy) {
        return new GroupImpl(copy);
    }

    @Override
    public MutableRepoAcl createRepoAcl() {
        return new MutableRepoAclImpl();
    }

    @Override
    public MutableRepoAcl copyRepoAcl(RepoAcl copy) {
        return new MutableRepoAclImpl(copy);
    }

    @Override
    public MutableBuildAcl copyBuildAcl(BuildAcl copy) {
        return new MutableBuildAclImpl(copy);
    }

    @Override
    public MutableReleaseBundleAcl copyReleaseBundleAcl(ReleaseBundleAcl copy) {
        return new MutableReleaseBundleAclImpl(copy);
    }

    @Override
    public MutableAceInfo createAce() {
        return new AceImpl();
    }

    @Override
    public MutableAceInfo copyAce(AceInfo copy) {
        return new AceImpl(copy);
    }

    @Override
    public SecurityInfo createSecurityInfo(List<UserInfo> users, List<GroupInfo> groups, List<RepoAcl> repoAcls,
            @Nullable List<BuildAcl> buildAcls, List<ReleaseBundleAcl> releaseBundleAcls) {
        return new SecurityDataImpl(users, groups, repoAcls, buildAcls, releaseBundleAcls);
    }

    @Override
    public JaxbAdapter getSecurityJaxbAdapter() {
        if (securityJaxbAdapter == null) {
            securityJaxbAdapter = JaxbAdapterFactory.create(SecurityDataImpl.class,
                    RepoPermissionTargetImpl.class,
                    BuildPermissionTargetImpl.class,
                    MutableRepoAclImpl.class,
                    MutableBuildAclImpl.class,
                    AceImpl.class,
                    UserImpl.class,
                    UserProperty.class,
                    UserGroupImpl.class,
                    GroupImpl.class
            );
        }
        return securityJaxbAdapter;
    }

    @Override
    public JaxbAdapter getFileSystemJaxbAdapter() {
        if (fileSystemJaxbAdapter == null) {
            fileSystemJaxbAdapter = JaxbAdapterFactory.create(FileInfoImpl.class,
                    FileAdditionalInfo.class,
                    FolderInfoImpl.class,
                    FolderAdditionalInfo.class,
                    StatsImpl.class,
                    PropertiesImplWrapper.class,
                    PropertiesImplNullElement.class,
                    WatchersImpl.class,
                    WatcherImpl.class);
        }
        return fileSystemJaxbAdapter;
    }

    @Override
    public MutableStatsInfo createStats() {
        return new StatsImpl();
    }

    @Override
    public MutableStatsInfo copyStats(StatsInfo copy) {
        return new StatsImpl(copy);
    }

    @Override
    public MutableWatchersInfo createWatchers() {
        return new WatchersImpl();
    }

    @Override
    public MutableWatcherInfo createWatcher(String watcherUsername, long watchCreationDate) {
        return new WatcherImpl(watcherUsername, watchCreationDate);
    }

    @Override
    public MetadataEntryInfo createMetadataEntry(String metadataName, String xmlContent) {
        return new MetadataEntry(metadataName, xmlContent);
    }

    @Override
    public MutableWatchersInfo copyWatchers(WatchersInfo copy) {
        return new WatchersImpl(copy);
    }

    @Override
    public Tree<ZipEntryInfo> createZipEntriesTree() {
        return new ZipEntriesTree();
    }

    @Override
    public ZipEntryInfo createZipEntry(ZipEntry... zipEntries) {
        return new ZipEntryImpl(zipEntries);
    }

    @Override
    public ZipEntryInfo createArchiveEntry(ArchiveEntry... archiveEntries) {
        return new ArchiveEntryImpl(archiveEntries);
    }

    @Override
    public ZipEntryResourceInfo createZipEntryResource(FileInfo info, ZipEntryInfo zipEntryInfo, long actualSize,
            ChecksumsInfo checksumsInfo) {
        return new ZipEntryResourceImpl(info, zipEntryInfo, actualSize, checksumsInfo);
    }

    @Override
    public MutableMetadataInfo createMetadata(RepoPath repoPath) {
        return new MetadataInfoImpl(repoPath);
    }
}
