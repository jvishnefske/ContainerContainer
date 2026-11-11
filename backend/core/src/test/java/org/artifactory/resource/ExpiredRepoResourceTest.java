package org.artifactory.resource;

import org.artifactory.fs.RepoResource;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.repo.RepoPathFactory;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author Alexei Vainshtein
 */
public class ExpiredRepoResourceTest {

    @Test
    public void testGetWrappedResource() {
        FileInfoImpl info = new FileInfoImpl(RepoPathFactory.create("repo"));
        RepoResource wrappedResource = new FileResource(info);
        ExpiredRepoResource expiredRepoResource = new ExpiredRepoResource(wrappedResource);
        assertEquals(expiredRepoResource.getWrappedResource(), wrappedResource);
    }

    @Test
    public void testGetOriginRemotePathDelegatesToWrappedResource() {
        FileInfoImpl info = new FileInfoImpl(RepoPathFactory.create("repo"));
        FileResource wrappedResource = new FileResource(info);
        String expectedOriginRemotePath = "upstream/repo/artifact-1.0.jar";
        wrappedResource.setOriginRemotePath(expectedOriginRemotePath);
        ExpiredRepoResource expiredRepoResource = new ExpiredRepoResource(wrappedResource);
        assertEquals(expiredRepoResource.getOriginRemotePath(), expectedOriginRemotePath);
    }
}