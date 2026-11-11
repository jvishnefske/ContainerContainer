package org.artifactory.repo.service.deploy;

import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.RepoPath;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.testng.Assert.assertEquals;

public class UIBuildArtifactoryRequestTest {

    @Test
    public void testReplaceContent() {
        RepoPath repoPath = new RepoPathImpl("a", "b");
        String initial = "initial";
        InputStream initialStream = new ByteArrayInputStream(initial.getBytes());
        UIBuildArtifactoryRequest request = new UIBuildArtifactoryRequest(repoPath, initialStream,
                initial.length(), 0, null, false);
        assertEquals(request.getContentLength(), initial.length());
        String replaced = "replaced";
        request.replaceContent(replaced.getBytes());
        assertEquals(request.getContentLength(), replaced.length());
    }

}