package org.artifactory.storage.fs.tree;

import org.artifactory.fs.FileInfo;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

/**
 * @author Amir Avidan
 */
class FileNodeTest {

    @Test
    void testGetCachedChildrenNodes() {
        FileInfo fileInfo = mock(FileInfo.class);
        FileNode fileNode = new FileNode(fileInfo);
        assertEquals(Collections.emptyList(), fileNode.getCachedChildrenNodes());
    }

    @Test
    void testisUsedGetChildren() {
        FileInfo fileInfo = mock(FileInfo.class);
        FileNode fileNode = new FileNode(fileInfo);
        assertFalse(fileNode.isUsedGetChildren());
    }
}