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

package org.artifactory.storage.fs.tree.file;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.model.fs.FolderInfoImpl;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.storage.fs.tree.FileNode;
import org.artifactory.storage.fs.tree.FolderNode;
import org.testng.annotations.Test;

import java.io.File;
import java.io.InputStream;
import java.util.Map;

import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.REPOSITORY_KEY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Units tests for the {@link org.artifactory.storage.fs.tree.file.JavaIOFileAdapter}.
 *
 * @author Yossi Shaul
 */
@Test
public class JavaIOFileAdapterTest {

    public void fileInfoAdapter() {
        FileInfoImpl fileInfo = new FileInfoImpl(new RepoPathImpl("repoX", "test/file.txt"));
        JavaIOFileAdapter fileAdapter = new JavaIOFileAdapter(new FileNode(fileInfo));

        assertFalse(fileAdapter.isDirectory());
        assertTrue(fileAdapter.isFile());
        assertEquals(fileAdapter.getRepoPath(), fileInfo.getRepoPath());
        assertEquals(fileAdapter.getFileInfo(), fileInfo);
        assertEquals(fileAdapter.getAbsolutePath(), new File("repoX/test/file.txt").getPath());
        assertFalse(fileAdapter.canWrite());
    }

    public void folderInfoAdapter() {
        FolderInfoImpl folderInfo = new FolderInfoImpl(new RepoPathImpl("repoX", "test/folder/"));
        JavaIOFileAdapter folderAdapter = new JavaIOFileAdapter(new FolderNode(folderInfo, null));

        assertTrue(folderAdapter.isDirectory());
        assertFalse(folderAdapter.isFile());
        assertEquals(folderAdapter.getRepoPath(), folderInfo.getRepoPath());
        assertEquals(folderAdapter.getInfo(), folderInfo);
        assertEquals(folderAdapter.getAbsolutePath(), new File("repoX/test/folder").getPath());
    }

    public void testSkipCache() {
        String sha1 = "dcab88fc2a043c2479a6de676a2f8179e9ea2167";

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryBinaryService binaryService = mock(ArtifactoryBinaryService.class);
        InputStream mockStream = mock(InputStream.class);
        Map<String, String> expectedHeaders = Map.of(REPOSITORY_KEY.getHeaderName(), "repoX");
        when(binaryService.getBinary(sha1, expectedHeaders)).thenReturn(mockStream);
        when(context.beanForType(ArtifactoryBinaryService.class)).thenReturn(binaryService);
        try {
            ArtifactoryContextThreadBinder.bind(context);

            FileInfoImpl fileInfo = new FileInfoImpl(new RepoPathImpl("repoX", "test/file.txt"));
            fileInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha1, sha1, sha1));
            JavaIOFileAdapter fileAdapter = new JavaIOFileAdapter(new FileNode(fileInfo));

            InputStream actualStream = fileAdapter.getStream();
            assertEquals(actualStream, mockStream);
            verify(binaryService, times(1)).getBinary(sha1, expectedHeaders);

        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }
}
