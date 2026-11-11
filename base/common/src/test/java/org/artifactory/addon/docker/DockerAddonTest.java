package org.artifactory.addon.docker;

import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerAddonTest {

    @Test
    void isDockerTempUploadsFolder() {
        RepoPath uploadsFolder = RepoPathFactory.create("repo", DockerAddon.DOCKER_TEMP_UPLOADS_FOLDER + "/");
        RepoPath notUploadsFolder = RepoPathFactory.create("repo", "other/");
        RepoPath fileInUploads = RepoPathFactory.create("repo", DockerAddon.DOCKER_TEMP_UPLOADS_FOLDER);

        assertTrue(DockerAddon.isDockerTempUploadsFolder(uploadsFolder));
        assertFalse(DockerAddon.isDockerTempUploadsFolder(notUploadsFolder));
        assertFalse(DockerAddon.isDockerTempUploadsFolder(fileInUploads));
        assertFalse(DockerAddon.isDockerTempUploadsFolder(null));
    }

    @Test
    void isUnderDockerTempUploadsFolder() {
        RepoPath root = RepoPathFactory.create("repo", "");
        RepoPath uploadsFolder = RepoPathFactory.create("repo", DockerAddon.DOCKER_TEMP_UPLOADS_FOLDER + "/");
        RepoPath fileInUploads = RepoPathFactory.create("repo", DockerAddon.DOCKER_TEMP_UPLOADS_FOLDER + "/file.txt");
        RepoPath nestedFile =
                RepoPathFactory.create("repo", DockerAddon.DOCKER_TEMP_UPLOADS_FOLDER + "/nested/nested.txt");
        RepoPath fileNotInUploads = RepoPathFactory.create("repo", "other/file.txt");

        assertFalse(DockerAddon.isUnderDockerTempUploadsFolder(null));
        assertFalse(DockerAddon.isUnderDockerTempUploadsFolder(root));
        assertTrue(DockerAddon.isUnderDockerTempUploadsFolder(uploadsFolder));
        assertTrue(DockerAddon.isUnderDockerTempUploadsFolder(fileInUploads));
        assertTrue(DockerAddon.isUnderDockerTempUploadsFolder(nestedFile));
        assertFalse(DockerAddon.isUnderDockerTempUploadsFolder(fileNotInUploads));
    }
}