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

package org.artifactory.api.module;

import org.artifactory.api.module.regex.NamedPattern;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.RepoLayoutBuilder;
import org.artifactory.util.RepoLayoutUtils;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static org.artifactory.api.module.ModuleInfoUtils.constructArtifactPath;
import static org.artifactory.api.module.ModuleInfoUtils.moduleInfoFromArtifactPath;
import static org.artifactory.api.module.ModuleInfoUtils.moduleInfoFromDescriptorPath;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Noam Y. Tenne
 */
@Test
public class ModuleInfoUtilsTest {

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Unable to construct a path from a null module info object.")
    public void constructArtifactPathWithNullModuleInfo() {
        constructArtifactPath(null, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Unable to construct a path from an invalid module info object.")
    public void constructArtifactPathWithInvalidModuleInfo() {
        constructArtifactPath(new ModuleInfo(), null);
    }

    /*@Test(description = "RTFACT-7125")
    public void constructMavenSnapshotWithDots() {
        ModuleInfo module = new ModuleInfoBuilder().organization("org.jfrog").module("mod").baseRevision("1.0").ext
        ("jar")
                .fileIntegrationRevision("SNAPSHOT").folderIntegrationRevision("SNAPSHOT").build();
        String path = constructArtifactPath(module, RepoLayoutUtils.MAVEN_2_DEFAULT, true, true);
        Assert.assertEquals(path, "org.jfrog/mod/1.0-SNAPSHOT/mod-1.0-SNAPSHOT.jar");
        String path2 = constructArtifactPath(module, RepoLayoutUtils.MAVEN_2_DEFAULT);
        Assert.assertEquals(path2, "org/jfrog/mod/1.0-SNAPSHOT/mod-1.0-SNAPSHOT.jar");
        String path3 = constructArtifactPath(module, RepoLayoutUtils.MAVEN_2_DEFAULT, true);
        Assert.assertEquals(path3, "org/jfrog/mod/1.0-SNAPSHOT/mod-1.0-SNAPSHOT.jar");
    }*/

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Unable to construct a path from a null repository layout.")
    public void constructArtifactPathWithNullRepoLayout() {
        constructArtifactPath(new ModuleInfoBuilder().organization("org").module("mod").baseRevision("rev").build(),
                null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Cannot construct a module info object from a blank item path.")
    public void moduleInfoFromArtifactPathWithNullArtifactPath() {
        moduleInfoFromArtifactPath(null, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Cannot construct a module info object from a blank item path.")
    public void moduleInfoFromDescriptorPathWithNullArtifactPath() {
        moduleInfoFromDescriptorPath(null, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Cannot construct a module info object from a blank item path.")
    public void moduleInfoFromArtifactPathWithBlankItemPath() {
        moduleInfoFromArtifactPath("", null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Cannot construct a module info object from a blank item path.")
    public void moduleInfoFromDescriptorPathWithBlankItemPath() {
        moduleInfoFromDescriptorPath("", null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Cannot construct a module info object from a null repository layout.")
    public void moduleInfoFromArtifactPathWithNullRepoLayout() {
        moduleInfoFromArtifactPath("org/meow", null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Cannot construct a module info object from a null repository layout.")
    public void moduleInfoFromDescriptorPathWithNullRepoLayout() {
        moduleInfoFromDescriptorPath("org/meow", null);
    }

    /**
     * Each test creates a layout with a unique name via {@link #uniqueLayout()}, guaranteeing a
     * cold cache entry on the first call.
     */
    @Test
    public void compiledPatternIsCachedForSameLayout() {
        RepoLayout layout = uniqueLayout();
        try (MockedStatic<NamedPattern> mockedPattern =
                Mockito.mockStatic(NamedPattern.class, Mockito.CALLS_REAL_METHODS)) {
            moduleInfoFromArtifactPath("org/module/module-1.0.jar", layout);
            moduleInfoFromArtifactPath("org/other/other-2.0.jar", layout);

            mockedPattern.verify(() -> NamedPattern.compile(anyString()), times(1));
        }
    }

    @Test
    public void differentLayoutsProduceSeparateCacheEntries() {
        RepoLayout layoutA = uniqueLayout();
        RepoLayout layoutB = uniqueLayout();
        try (MockedStatic<NamedPattern> mockedPattern =
                Mockito.mockStatic(NamedPattern.class, Mockito.CALLS_REAL_METHODS)) {
            moduleInfoFromArtifactPath("org/module/module-1.0.jar", layoutA);
            moduleInfoFromArtifactPath("org/module/module-1.0.jar", layoutB);

            mockedPattern.verify(() -> NamedPattern.compile(anyString()), times(2));
        }
    }

    @Test
    public void supportVersionsTokensFlagProducesSeparateCacheEntries() {
        RepoLayout layout = uniqueLayout();
        try (MockedStatic<NamedPattern> mockedPattern =
                Mockito.mockStatic(NamedPattern.class, Mockito.CALLS_REAL_METHODS)) {
            moduleInfoFromArtifactPath("org/module/module-1.0.jar", layout, false);
            moduleInfoFromArtifactPath("org/module/module-1.0.jar", layout, true);

            mockedPattern.verify(() -> NamedPattern.compile(anyString()), times(2));
        }
    }

    @Test
    public void cachedResultIsCorrect() {
        RepoLayout layout = RepoLayoutUtils.MAVEN_2_DEFAULT;
        String path = "org/jfrog/module/1.0/module-1.0.jar";

        ModuleInfo first = moduleInfoFromArtifactPath(path, layout);
        ModuleInfo second = moduleInfoFromArtifactPath(path, layout);

        assertTrue(first.isValid());
        assertTrue(second.isValid());
        assertEquals(first.getOrganization(), second.getOrganization());
        assertEquals(first.getModule(), second.getModule());
        assertEquals(first.getBaseRevision(), second.getBaseRevision());
        assertEquals(first.getExt(), second.getExt());
    }

    @Test
    public void anyLayoutFieldChangeProducesNewCacheEntry() {
        RepoLayout original = uniqueLayout();
        RepoLayout renamed = new RepoLayout(original);
        renamed.setName("renamed-" + original.getName());
        try (MockedStatic<NamedPattern> mockedPattern =
                Mockito.mockStatic(NamedPattern.class, Mockito.CALLS_REAL_METHODS)) {
            moduleInfoFromArtifactPath("org/module/module-1.0.jar", original);
            moduleInfoFromArtifactPath("org/module/module-1.0.jar", renamed);

            mockedPattern.verify(() -> NamedPattern.compile(anyString()), times(2));
        }
    }

    /**
     * Returns a layout with a unique name so its {@link PatternCacheKey} is guaranteed to be
     * absent from the static cache at test start.
     */
    private static RepoLayout uniqueLayout() {
        return new RepoLayoutBuilder()
                .name("test-" + System.nanoTime())
                .artifactPathPattern("[orgPath]/[module]/[module]-[baseRev].[ext]")
                .distinctiveDescriptorPathPattern(false)
                .folderIntegrationRevisionRegExp(".*")
                .fileIntegrationRevisionRegExp(".*")
                .build();
    }
}
