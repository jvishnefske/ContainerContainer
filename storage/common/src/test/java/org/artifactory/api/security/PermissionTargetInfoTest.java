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

package org.artifactory.api.security;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.artifactory.model.security.RepoPermissionTargetImpl;
import org.artifactory.security.PermissionTarget;
import org.artifactory.security.RepoPermissionTarget;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * PermissionTargetInfo unit tests.
 *
 * @author Yossi Shaul
 */
@Test
public class PermissionTargetInfoTest {

    public void testDefaultConstructor() {
        RepoPermissionTarget pmi = new RepoPermissionTargetImpl();

        assertEquals(pmi.getName(), "");
        assertEquals(pmi.getRepoKeys(), Arrays.asList(PermissionTarget.ANY_REPO));
        assertEquals(pmi.getIncludesPattern(), PermissionTarget.ANY_PATH);
        assertEquals(pmi.getIncludes().size(), 1);
        assertEquals(pmi.getExcludesPattern(), "");
        assertEquals(pmi.getExcludes().size(), 0);
    }

    public void createWithNoIncluesExcludesPatterns() {
        RepoPermissionTarget pmi = new RepoPermissionTargetImpl("permissionName", Set.of("aRepo"));

        assertEquals(pmi.getName(), "permissionName");
        assertEquals(pmi.getRepoKeys(), Set.of("aRepo"));
        assertEquals(pmi.getIncludesPattern(), PermissionTarget.ANY_PATH);
        assertEquals(pmi.getIncludes().size(), 1);
        assertEquals(pmi.getExcludesPattern(), "");
        assertEquals(pmi.getExcludes().size(), 0);
    }

    public void createWithIncluesAndExcludesPatterns() {
        String includes = "**/*-sources.*,**/*-SNAPSHOT/**";
        String excludes = "**/secretjars/**";
        RepoPermissionTarget pmi = new RepoPermissionTargetImpl(
                "permissionName", Set.of("repoKey1", "repoKey2"), includes, excludes);

        assertEquals(pmi.getName(), "permissionName");
        assertEquals(pmi.getRepoKeys(), new LinkedHashSet<>(List.of("repoKey1", "repoKey2")));
        assertEquals(pmi.getIncludesPattern(), includes);
        assertEquals(pmi.getIncludes().size(), 2);
        assertEquals(pmi.getExcludesPattern(), excludes);
        assertEquals(pmi.getExcludes().size(), 1);
    }

    public void copyConstructor() {
        RepoPermissionTarget orig = new RepoPermissionTargetImpl(
                "permissionName", Set.of("repoKey1", "repoKey2"), "**/*-sources.*,**/*-SNAPSHOT/**",
                "**/secretjars/**");

        RepoPermissionTarget copy = new RepoPermissionTargetImpl(orig);
        assertEquals(copy.getName(), orig.getName());
        assertEquals(copy.getRepoKeys(), orig.getRepoKeys());
        assertEquals(copy.getExcludes(), orig.getExcludes());
        assertEquals(copy.getExcludesPattern(), orig.getExcludesPattern());
        assertEquals(copy.getIncludes(), orig.getIncludes());
        assertEquals(copy.getIncludesPattern(), orig.getIncludesPattern());
    }

    public void copyConstructorReflectionEquality() {
        RepoPermissionTarget orig = new RepoPermissionTargetImpl(
                "permissionName", Set.of("repoKey1", "repoKey2"), "**/*-sources.*,**/*-SNAPSHOT/**",
                "**/secretjars/**");
        PermissionTarget copy = new RepoPermissionTargetImpl(orig);

        assertTrue(EqualsBuilder.reflectionEquals(orig, copy), "Orig and copy differ");
    }
}
