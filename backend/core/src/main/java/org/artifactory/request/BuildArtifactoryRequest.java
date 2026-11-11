package org.artifactory.request;

import org.artifactory.repo.RepoPath;

import java.io.IOException;
import java.io.InputStream;

/**
 * BE EXTREMELY CAREFUL
 * It is a necessity of the build-info repo to buffer the entire content of a build json - whatever {@param httpRequest}
 * you initialize this class with will be read into memory in its entirety!
 *
 * @author Dan Feldman
 */
public interface BuildArtifactoryRequest extends ArtifactoryRequest {

    InputStream getInputStream() throws IOException;

    void replaceContent(byte[] newContent);

    void setRepoPath(RepoPath repoPath);
}
