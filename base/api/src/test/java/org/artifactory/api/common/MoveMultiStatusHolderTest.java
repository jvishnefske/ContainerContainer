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

package org.artifactory.api.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.artifactory.repo.RepoPath;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class MoveMultiStatusHolderTest {

    @Test
    void merge_moveMultiAccumulatesMovedArtifactAndFolderCountsAndMavenCandidates() {
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        logger.setLevel(Level.OFF);

        RepoPath candidateA = mock(RepoPath.class);
        RepoPath candidateB = mock(RepoPath.class);

        MoveMultiStatusHolder primary = new MoveMultiStatusHolder();
        primary.artifactsMoved(2);
        primary.folderMoved();
        primary.addToMavenMetadataCandidates(candidateA);
        primary.status("p1", logger);

        MoveMultiStatusHolder secondary = new MoveMultiStatusHolder();
        secondary.artifactsMoved(3);
        secondary.folderMoved();
        secondary.folderMoved();
        secondary.addToMavenMetadataCandidates(candidateB);
        secondary.status("s1", logger);

        primary.merge(secondary);

        assertEquals(5, primary.getMovedArtifactsCount());
        assertEquals(3, primary.getMovedFoldersCount());
        assertTrue(primary.getCandidatesForMavenMetadataCalculation().contains(candidateA));
        assertTrue(primary.getCandidatesForMavenMetadataCalculation().contains(candidateB));
    }

    @Test
    void merge_basicStatusHolder_doesNotAddMoveSpecificState() {
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        logger.setLevel(Level.OFF);

        MoveMultiStatusHolder primary = new MoveMultiStatusHolder();
        primary.artifactsMoved(4);
        primary.status("moved-only", logger);

        BasicStatusHolder secondary = new BasicStatusHolder();
        secondary.status("plain", logger);

        primary.merge(secondary);

        assertEquals(4, primary.getMovedArtifactsCount());
        assertEquals(0, primary.getMovedFoldersCount());
        assertTrue(primary.getCandidatesForMavenMetadataCalculation().isEmpty());
    }
}
