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

package org.artifactory.common.storage.db.properties;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Date: 7/10/13 3:17 PM
 *
 * @author freds
 */
@Test
public class DbVersionInfoTest {
    public void basicDbProperties() {
        DbVersionInfo test = new DbVersionInfo(1L, "3.0.1-test", 2, 3L, "3.0.2-test");
        assertEquals(test.getInstallationDate(), 1L);
        assertEquals(test.getArtifactoryVersion(), "3.0.1-test");
        assertEquals(test.getArtifactoryRevision(), 2);
        assertEquals(test.getArtifactoryRelease(), 3L);
        assertEquals(test.getArtifactoryProductVersion(), "3.0.2-test");
    }

    public void maxNullDbProperties() {
        DbVersionInfo test = new DbVersionInfo(2L, "2-t", 0, 0L, "3-t");
        assertEquals(test.getInstallationDate(), 2L);
        assertEquals(test.getArtifactoryVersion(), "2-t");
        assertEquals(test.getArtifactoryRevision(), 0);
        assertEquals(test.getArtifactoryRelease(), 0L);
        assertEquals(test.getArtifactoryProductVersion(), "3-t");
    }

    public void maxNegDbProperties() {
        DbVersionInfo test = new DbVersionInfo(3L, "3-t", -2, -3L, "4-t");
        assertEquals(test.getInstallationDate(), 3L);
        assertEquals(test.getArtifactoryVersion(), "3-t");
        assertEquals(test.getArtifactoryRevision(), -2);
        assertEquals(test.getArtifactoryRelease(), -3L);
        assertEquals(test.getArtifactoryProductVersion(), "4-t");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = ".*Installation date.*cannot.*zero.*")
    public void nullInstallDateDbProperties() {
        new DbVersionInfo(0L, "3.0.1-test", 2, 3L, "3.0.2-test");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = ".*Installation date.*cannot.*negative.*")
    public void negInstallDateDbProperties() {
        new DbVersionInfo(-1L, "3.0.1-test", 2, 3L, "3.0.2-test");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = ".*version.*cannot.*null.*")
    public void nullArtVersionDbProperties() {
        new DbVersionInfo(1L, null, 2, 3L, "3.0.2-test");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = ".*version.*cannot.*empty.*")
    public void emptyArtVersionDbProperties() {
        new DbVersionInfo(1L, " ", 2, 3L, "3.0.2-test");
    }

    @Test
    public void sameVersion() {
        DbVersionInfo version1 = new DbVersionInfo(1L, "version", 2,
                3L, "3.0.2-test");
        DbVersionInfo version2 = new DbVersionInfo(2L, "version", 2,
                3L, "3.0.2-test");
        assertTrue(version1.sameVersion(version2));
    }

    public void sameVersionFalse() {
        DbVersionInfo version1 = new DbVersionInfo(1L, "version1", 2,
                3L, "3.0.2-test");
        DbVersionInfo version2 = new DbVersionInfo(2L, "version2", 2,
                3L, "3.0.2-test");
        assertFalse(version1.sameVersion(version2));
    }
}
