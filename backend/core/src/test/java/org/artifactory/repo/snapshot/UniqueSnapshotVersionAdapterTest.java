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

package org.artifactory.repo.snapshot;

import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Snapshot;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.spring.InternalArtifactoryContext;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.artifactory.mime.MavenNaming.MAVEN_METADATA_NAME;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;


/**
 * Unit tests for {@link UniqueSnapshotVersionAdapter}.<p/> Only the easy to unit test are here, the rest are in the
 * integration tests.
 *
 * @author Yossi Shaul
 */
@Test
public class UniqueSnapshotVersionAdapterTest extends BaseSnapshotAdapterTest<UniqueSnapshotVersionAdapter> {

    private static final String SNAPSHOT_PATH = "groupPart1/groupPart2/artifactId/2.5-SNAPSHOT/";

    public UniqueSnapshotVersionAdapterTest() {
        super(new UniqueSnapshotVersionAdapter());
    }

    public void alreadyUniqueArtifact() {
        String path = SNAPSHOT_PATH + "artifactId-2.5-20071014.090200-4.jar";
        adapt(path, path, "Unique snapshots shouldn't be touched");
    }

    public void alreadyUniqueArtifactWithClassifier() {
        String path = SNAPSHOT_PATH + "artifactId-2.5-20071014.090200-4-classifier.jar";

        adapt(path, path, "Unique snapshots shouldn't be touched");
    }

    public void alreadyUniqueChecksumArtifact() {
        String path = SNAPSHOT_PATH + "artifactId-2.5-20071014.090200-4.jar.sha1";

        adapt(path, path);
    }

    public void artifactWithReleaseVersion() {
        String path = SNAPSHOT_PATH + "artifactId-1.4.ivy";

        adapt(path, path, "Files with release version should not be affected");
    }

    public void artifactWithNoMavenStructure() {
        String path = SNAPSHOT_PATH + "blabla.xml";

        adapt(path, path, "Non-maven structured files with release version should not be affected");
    }

    public void testArtifactWithSnapshotInTheClassifier() throws Exception {
        String path = SNAPSHOT_PATH + "artifactId-2.5-SNAPSHOT-lib-2.3-SNAPSHOT-jdk5.jar";
        RepoPath repoPath = RepoPathFactory.create("local", path);
        RepositoryService repositoryServiceMock = mock(RepositoryService.class);
        when(repositoryServiceMock.exists(repoPath.getParent())).thenReturn(false);
        when(repositoryServiceMock.exists(new RepoPathImpl(repoPath.getParent(), MAVEN_METADATA_NAME)))
                .thenReturn(false);
        InternalArtifactoryContext contextMock = mock(InternalArtifactoryContext.class);
        when(contextMock.getRepositoryService()).thenReturn(repositoryServiceMock);
        ArtifactoryContextThreadBinder.bind(contextMock);
        String adjustedPath = adjust(path);
        verify(repositoryServiceMock,times(1)).exists(repoPath.getParent());
        verify(repositoryServiceMock,times(1)).exists(new RepoPathImpl(repoPath.getParent(), MAVEN_METADATA_NAME));
        Pattern pattern = Pattern.compile(
                "groupPart1/groupPart2/artifactId/2\\.5\\-SNAPSHOT/artifactId\\-2\\.5\\-(\\d{8}\\.\\d{6}\\-\\d{1})\\" +
                        "-lib\\-2\\.3\\-SNAPSHOT\\-jdk5\\.jar");
        Matcher matcher = pattern.matcher(adjustedPath);
        Assert.assertTrue(matcher.matches(), "Unexpected transformed path");
    }

    @Test
    public void testGetBuildNumberMetadataNull() {
        UniqueSnapshotVersionAdapter adapter = new UniqueSnapshotVersionAdapter();

        assertEquals(0, adapter.getBuildNumberFromSnapshot(null));
    }

    @Test
    public void testGetBuildNumberVersioningNull() {
        UniqueSnapshotVersionAdapter adapter = new UniqueSnapshotVersionAdapter();
        Metadata mockMetadata = mock(Metadata.class);
        when(mockMetadata.getVersioning()).thenReturn(null);

        assertEquals(0, adapter.getBuildNumberFromSnapshot(mockMetadata));
    }

    @Test
    public void testGetBuildNumberSnapshotNull() {
        UniqueSnapshotVersionAdapter adapter = new UniqueSnapshotVersionAdapter();
        Metadata mockMetadata = mock(Metadata.class);
        Versioning mockVersioning = mock(Versioning.class);
        when(mockMetadata.getVersioning()).thenReturn(mockVersioning);
        when(mockVersioning.getSnapshot()).thenReturn(null);

        assertEquals(0, adapter.getBuildNumberFromSnapshot(mockMetadata));
    }

    @Test
    public void testGetBuildNumberSuccess() {
        UniqueSnapshotVersionAdapter adapter = new UniqueSnapshotVersionAdapter();
        Metadata mockMetadata = mock(Metadata.class);
        Versioning mockVersioning = mock(Versioning.class);
        Snapshot mockSnapshot = mock(Snapshot.class);
        when(mockMetadata.getVersioning()).thenReturn(mockVersioning);
        when(mockVersioning.getSnapshot()).thenReturn(mockSnapshot);
        when(mockSnapshot.getBuildNumber()).thenReturn(123);

        assertEquals(123, adapter.getBuildNumberFromSnapshot(mockMetadata));
    }

    @AfterMethod
    public void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }
}