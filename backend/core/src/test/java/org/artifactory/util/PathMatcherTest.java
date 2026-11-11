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

package org.artifactory.util;

import aQute.libg.glob.AntGlob;
import com.google.common.collect.Lists;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.util.AntPathMatcher;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * PathMatcher unit tests.
 *
 * @author Yossi Shaul
 */
@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
public class PathMatcherTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private InternalArtifactoryContext context;
    @Mock
    private AutoCloseable autoCloseable;
    AntPathMatcher antPathMatcher = new AntPathMatcher();
    List<String> DEFAULT_GLOBAL_EXCLUDES = Lists.newArrayList(
            "**/*~",
            "**/#*#",
            "**/.#*",
            "**/%*%",
            "**/._*",
            "**/CVS",
            "**/CVS/**",
            "**/.cvsignore",
            "**/SCCS",
            "**/SCCS/**",
            "**/vssver.scc",
            "**/.svn",
            "**/.svn/**",
            "**/.DS_Store");

    @BeforeEach
    void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        when(context.getArtifactoryHome()).thenReturn(getOrCreateArtifactoryHomeStub());
        context.getArtifactoryHome().getArtifactoryProperties()
                .setProperty(ConstantValues.useEmptyListForDefaultGlobalExcludes.getPropertyName(), "true");
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test
    public void matchWithIncludeAll() {
        // File
        boolean matches1 = PathMatcher.matches("apath", Arrays.asList("**"), Arrays.asList(""), false);
        assertTrue(matches1);
        // Folder
        boolean matches2 = PathMatcher.matches("apath", Arrays.asList("**"), Arrays.asList(""), true);
        assertTrue(matches2);
    }

    @Test
    public void matchWithExcludeAll() {
        // File
        boolean matches1 = PathMatcher.matches("apath", Arrays.asList(""), Arrays.asList("**"), false);
        assertFalse(matches1);

        // Folder
        boolean matches2 = PathMatcher.matches("apath", Arrays.asList(""), Arrays.asList("**"), true);
        assertFalse(matches2);
    }

    @Test
    public void matchWithIncludesOnly() {
        // File
        List<String> includes = Arrays.asList("apath/*", "**/my/test/path", "public/in/**");
        List<String> excludes = Arrays.asList("");
        assertFalse(PathMatcher.matches("apath", includes, excludes, false));
        assertTrue(PathMatcher.matches("this/is/my/test/path", includes, excludes, false));
        assertFalse(PathMatcher.matches("this/is/my/test/path/andmore", includes, excludes, false));
        assertFalse(PathMatcher.matches("this/is/my/test/path/andmore", includes, excludes, false));
        assertTrue(PathMatcher.matches("public/in/the/public.jar", includes, excludes, false));
        assertFalse(PathMatcher.matches("public", includes, excludes, false));
        assertFalse(PathMatcher.matches("public/i", includes, excludes, false));
        assertFalse(PathMatcher.matches("public2", Arrays.asList("apath/*", "public/in/**"), excludes, false));
        //Folder
        assertTrue(PathMatcher.matches("apath", includes, excludes, true));
        assertTrue(PathMatcher.matches("this/is/my/test/path", includes, excludes, true));
        assertTrue(PathMatcher.matches("this/is/my/test/path/andmore", includes, excludes, true));
        assertTrue(PathMatcher.matches("this/is/my/test/path/andmore", includes, excludes, true));
        assertTrue(PathMatcher.matches("public/in/the/public.jar", includes, excludes, true));
        assertTrue(PathMatcher.matches("public", includes, excludes, true));
        assertTrue(PathMatcher.matches("public/i", includes, excludes, true));
        assertFalse(PathMatcher.matches("public2", Arrays.asList("apath/*", "public/in/**"), excludes, true));
    }

    @Test
    public void matchWithIncludesOnlyPartial() {
        // File
        List<String> excludes = Arrays.asList("");
        assertFalse(PathMatcher.matches("ap/x", Arrays.asList("*p/x/y"), excludes, false));
        assertFalse(PathMatcher.matches("ap/x", Arrays.asList("**/y/*"), excludes, false));
        assertFalse(PathMatcher.matches("ap/x", Arrays.asList("*/x/*"), excludes, false));
        assertFalse(PathMatcher.matches("ap/x", Arrays.asList("*/*/*"), excludes, false));
        assertFalse(PathMatcher.matches("ap/x", Arrays.asList("*/*/t"), excludes, false));
        assertFalse(PathMatcher.matches("ap/x", Arrays.asList("*/x/t"), excludes, false));
        assertTrue(PathMatcher.matches("ap/x", Arrays.asList("a?/x"), excludes, false));
        assertTrue(PathMatcher.matches("ap/x", Arrays.asList("?p*/?"), excludes, false));
        assertFalse(PathMatcher.matches("ap/x", Arrays.asList("?p*/??"), excludes, false));
        assertFalse(PathMatcher.matches("ap/x", Arrays.asList("aa?/x"), excludes, false));
        assertFalse(PathMatcher.matches("ap/x", Arrays.asList("*/d/*"), excludes, false));
        assertFalse(PathMatcher.matches("apath", Arrays.asList("apath/some/other/*"), excludes, false));
        assertFalse(PathMatcher.matches("apath/some", Arrays.asList("apath/some/other/*"), excludes, false));
        assertFalse(PathMatcher.matches("apath/some2", Arrays.asList("apath/some/other/*"), excludes, false));
        assertFalse(PathMatcher.matches("apath2", Arrays.asList("apath/some/other/*"), excludes, false));
        assertFalse(
                PathMatcher.matches("com", Arrays.asList("com/some/other/*", "com/acme/***", "com/toto/*"), excludes,
                        false));
        assertFalse(
                PathMatcher.matches("org", Arrays.asList("com/some/other/*", "com/acme/***", "com/toto/*"), excludes,
                        false));
        assertFalse(PathMatcher.matches("apath2", Arrays.asList("apath/*"), excludes, false));
        assertFalse(PathMatcher.matches("apath", Arrays.asList("apath2/*"), excludes, false));
        assertFalse(PathMatcher.matches("apath", Arrays.asList("apath2"), excludes, false));
        // Folder
        assertTrue(PathMatcher.matches("ap/x", Arrays.asList("*p/x/y"), excludes, true));
        assertTrue(PathMatcher.matches("ap/x", Arrays.asList("**/y/*"), excludes, true));
        assertTrue(PathMatcher.matches("ap/x", Arrays.asList("*/x/*"), excludes, true));
        assertTrue(PathMatcher.matches("ap/x", Arrays.asList("*/*/*"), excludes, true));
        assertTrue(PathMatcher.matches("ap/x", Arrays.asList("*/*/t"), excludes, true));
        assertTrue(PathMatcher.matches("ap/x", Arrays.asList("*/x/t"), excludes, true));
        assertTrue(PathMatcher.matches("ap/x", Arrays.asList("a?/x"), excludes, true));
        assertTrue(PathMatcher.matches("ap/x", Arrays.asList("?p*/?"), excludes, true));
        assertFalse(PathMatcher.matches("ap/x", Arrays.asList("?p*/??"), excludes, true));
        assertFalse(PathMatcher.matches("ap/x", Arrays.asList("aa?/x"), excludes, true));
        assertFalse(PathMatcher.matches("ap/x", Arrays.asList("*/d/*"), excludes, true));
        assertTrue(PathMatcher.matches("apath", Arrays.asList("apath/some/other/*"), excludes, true));
        assertTrue(PathMatcher.matches("apath/some", Arrays.asList("apath/some/other/*"), excludes, true));
        assertFalse(PathMatcher.matches("apath/some2", Arrays.asList("apath/some/other/*"), excludes, true));
        assertFalse(PathMatcher.matches("apath2", Arrays.asList("apath/some/other/*"), excludes, true));
        assertTrue(
                PathMatcher.matches("com", Arrays.asList("com/some/other/*", "com/acme/***", "com/toto/*"), excludes,
                        true));
        assertFalse(
                PathMatcher.matches("org", Arrays.asList("com/some/other/*", "com/acme/***", "com/toto/*"), excludes,
                        true));
        assertFalse(PathMatcher.matches("apath2", Arrays.asList("apath/*"), excludes, true));
        assertFalse(PathMatcher.matches("apath", Arrays.asList("apath2/*"), excludes, true));
        assertFalse(PathMatcher.matches("apath", Arrays.asList("apath2"), excludes, true));

    }

    @Test
    public void matchWithExcludesOnly() {
        // File
        List<String> includes = Collections.emptyList();
        getOrCreateArtifactoryHomeStub().getArtifactoryProperties()
                .setProperty(ConstantValues.globalExcludes.getPropertyName(), "apath,**/my/test/path,commons-*,main/*");
        context.getArtifactoryHome().getArtifactoryProperties()
                .setProperty(ConstantValues.useEmptyListForDefaultGlobalExcludes.getPropertyName(), "false");
        List<String> excludes = GlobalExcludes.getGlobalExcludes();
        assertTrue(PathMatcher.matches("apat", includes, excludes, false));
        assertFalse(PathMatcher.matches("apath", includes, excludes, false));
        assertTrue(PathMatcher.matches("apath2", includes, excludes, false));
        assertFalse(PathMatcher.matches("commons-codec", includes, excludes, false));
        assertTrue(PathMatcher.matches("main.123", includes, excludes, false));
        assertTrue(PathMatcher.matches("apath/deep", includes, excludes, false));
        assertFalse(PathMatcher.matches("this/is/my/test/path", includes, excludes, false));
        assertTrue(PathMatcher.matches("this/is/my/test/path/andmore", includes, excludes, false));
        // Folder
        assertTrue(PathMatcher.matches("apat", includes, excludes, true));
        assertFalse(PathMatcher.matches("apath", includes, excludes, true));
        assertTrue(PathMatcher.matches("apath2", includes, excludes, true));
        assertFalse(PathMatcher.matches("commons-codec", includes, excludes, true));
        assertTrue(PathMatcher.matches("main.123", includes, excludes, true));
        assertTrue(PathMatcher.matches("apath/deep", includes, excludes, true));
        assertFalse(PathMatcher.matches("this/is/my/test/path", includes, excludes, true));
        assertTrue(PathMatcher.matches("this/is/my/test/path/andmore", includes, excludes, true));

    }

    @Test
    public void matchExcludesAndIncludes() {
        // File
        List<String> includes = Arrays.asList("org/**", "com/**", "net/**");
        List<String> excludes = Arrays.asList("org/apache/**", "commons-*");
        assertTrue(PathMatcher.matches("org/codesulting", includes, excludes, false));
        assertTrue(PathMatcher.matches("com/test/123", includes, excludes, false));
        assertFalse(PathMatcher.matches("org/apache/bla", includes, excludes, false));
        assertFalse(PathMatcher.matches("commons-lang", includes, excludes, false));
        // Folder
        assertTrue(PathMatcher.matches("org/codesulting", includes, excludes, true));
        assertTrue(PathMatcher.matches("com/test/123", includes, excludes, true));
        assertFalse(PathMatcher.matches("org/apache/bla", includes, excludes, true));
        assertFalse(PathMatcher.matches("commons-lang", includes, excludes, true));
    }

    @Test
    public void excludesTakesPrecedenceOverIncludes() {
        // currently excludes alway takes precedence, we might want to change it in the future so the closer will win
        // File
        List<String> excludes = Arrays.asList("apath/**");
        List<String> includes = Arrays.asList("**", "apath/sub/1");
        assertFalse(PathMatcher.matches("apath", includes, excludes, false));
        assertFalse(PathMatcher.matches("apath/sub", includes, excludes, false));
        assertFalse(PathMatcher.matches("apath/sub/1", includes, excludes,
                false));  // even though it is explicitly included
        assertTrue(PathMatcher.matches("apath2", includes, excludes, false));
        //Folder
        assertFalse(PathMatcher.matches("apath", includes, excludes, true));
        assertFalse(PathMatcher.matches("apath/sub", includes, excludes, true));
        assertFalse(
                PathMatcher.matches("apath/sub/1", includes, excludes, true));  // even though it is explicitly included
        assertTrue(PathMatcher.matches("apath2", includes, excludes, true));
    }

    @Test
    public void subPathIncludes() {
        List<String> includes = Arrays.asList("apath1/sub1/**", "apath1/sub2/**", "apath2/sub1/**");
        //File
        assertFalse(PathMatcher.matches("apath", includes, Collections.emptyList(), false));
        assertFalse(PathMatcher.matches("apath1", includes, Collections.emptyList(), false));
        assertFalse(PathMatcher.matches("apath2", includes, Collections.emptyList(), false));
        assertFalse(PathMatcher.matches("apath1/sub", includes, Collections.emptyList(), false));
        assertTrue(PathMatcher.matches("apath1/sub1", includes, Collections.emptyList(), false));
        assertTrue(PathMatcher.matches("apath1/sub1/t", includes, Collections.emptyList(), false));
        assertTrue(PathMatcher.matches("apath1/sub1/toto/tutu", includes, Collections.emptyList(), false));
        // Folder
        assertFalse(PathMatcher.matches("apath", includes, Collections.emptyList(), true));
        assertTrue(PathMatcher.matches("apath1", includes, Collections.emptyList(), true));
        assertTrue(PathMatcher.matches("apath2", includes, Collections.emptyList(), true));
        assertFalse(PathMatcher.matches("apath1/sub", includes, Collections.emptyList(), true));
        assertTrue(PathMatcher.matches("apath1/sub1", includes, Collections.emptyList(), true));
        assertTrue(PathMatcher.matches("apath1/sub1/t", includes, Collections.emptyList(), true));
        assertTrue(PathMatcher.matches("apath1/sub1/toto/tutu", includes, Collections.emptyList(), true));
    }

    @Test
    public void sourcesIncludes() {
        List<String> includes = Arrays.asList("**/*-sources.jar*");
        // File
        assertFalse(PathMatcher.matches("apath/", includes, Collections.emptyList(), false));
        assertFalse(PathMatcher.matches("apath1/", includes, Collections.emptyList(), false));
        assertFalse(PathMatcher.matches("apath2/", includes, Collections.emptyList(), false));
        assertFalse(
                PathMatcher.matches("apath1/subfolder-longer-than-pattern/", includes, Collections.emptyList(), false));
        assertFalse(PathMatcher.matches("apath1/sub1/", includes, Collections.emptyList(), false));
        assertFalse(PathMatcher.matches("apath1/sub1/t/", includes, Collections.emptyList(), false));
        assertFalse(PathMatcher.matches("apath1/sub1/toto/tutu/", includes, Collections.emptyList(), false));
        assertTrue(PathMatcher.matches("apath1/toto-sources.jar", includes, Collections.emptyList(), false));
        assertTrue(PathMatcher.matches("apath1/sub/toto-sources.jar", includes, Collections.emptyList(), false));
        assertTrue(PathMatcher.matches("apath1/toto-sources.jar.md5", includes, Collections.emptyList(), false));
        assertTrue(PathMatcher.matches("apath1/sub/toto-sources.jar.md5", includes, Collections.emptyList(), false));
        assertFalse(PathMatcher.matches("apath1/toto-1.0.jar", includes, Collections.emptyList(), false));
        assertFalse(PathMatcher.matches("apath1/sub/toto-1.0.jar", includes, Collections.emptyList(), false));
        assertFalse(PathMatcher.matches("apath1/toto-1.0.jar.md5", includes, Collections.emptyList(), false));
        assertFalse(PathMatcher.matches("apath1/sub/toto-1.0.jar.md5", includes, Collections.emptyList(), false));
        // Folder
        assertTrue(PathMatcher.matches("apath/", includes, Collections.emptyList(), true));
        assertTrue(PathMatcher.matches("apath1/", includes, Collections.emptyList(), true));
        assertTrue(PathMatcher.matches("apath2/", includes, Collections.emptyList(), true));
        assertTrue(
                PathMatcher.matches("apath1/subfolder-longer-than-pattern/", includes, Collections.emptyList(), true));
        assertTrue(PathMatcher.matches("apath1/sub1/", includes, Collections.emptyList(), true));
        assertTrue(PathMatcher.matches("apath1/sub1/t/", includes, Collections.emptyList(), true));
        assertTrue(PathMatcher.matches("apath1/sub1/toto/tutu/", includes, Collections.emptyList(), true));
        assertTrue(PathMatcher.matches("apath1/toto-sources.jar", includes, Collections.emptyList(), true));
        assertTrue(PathMatcher.matches("apath1/sub/toto-sources.jar", includes, Collections.emptyList(), true));
        assertTrue(PathMatcher.matches("apath1/toto-sources.jar.md5", includes, Collections.emptyList(), true));
        assertTrue(PathMatcher.matches("apath1/sub/toto-sources.jar.md5", includes, Collections.emptyList(), true));
        assertTrue(PathMatcher.matches("apath1/toto-1.0.jar", includes, Collections.emptyList(), true));
        assertTrue(PathMatcher.matches("apath1/sub/toto-1.0.jar", includes, Collections.emptyList(), true));
        assertTrue(PathMatcher.matches("apath1/toto-1.0.jar.md5", includes, Collections.emptyList(), true));
        assertTrue(PathMatcher.matches("apath1/sub/toto-1.0.jar.md5", includes, Collections.emptyList(), true));
    }

    @Test
    public void test() {
        List<String> includes = Arrays.asList("**/*-test.jar");
        List<String> excludes = Arrays.asList();
        assertFalse(PathMatcher.matches("a/", includes, excludes, false));
        assertTrue(PathMatcher.matches("a/", includes, excludes, true));
    }

    @Test
    public void matchTrim() {
        assertFalse(PathMatcher.matches("a/b/c", Collections.emptyList(), Arrays.asList(" **/b/**"), false));
        assertFalse(PathMatcher.matches("a/b/c", Collections.emptyList(), Arrays.asList("**/b/**"), false));
        assertFalse(PathMatcher.matches(" a/b/c ", Collections.emptyList(), Arrays.asList("**/b/**"), false));
        assertFalse(PathMatcher.matches("a/b/c", Collections.emptyList(), Arrays.asList("**/b/** "), false));
        assertTrue(PathMatcher.matches("a/b/c", Arrays.asList(" **/b/**"), Collections.emptyList(), false));
        assertTrue(PathMatcher.matches("a/b/c", Arrays.asList("**/b/**"), Collections.emptyList(), false));
        assertTrue(PathMatcher.matches(" a/b/c ", Arrays.asList("**/b/**"), Collections.emptyList(), false));
        assertTrue(PathMatcher.matches("a/b/c", Arrays.asList("**/b/** "), Collections.emptyList(), false));
    }

    @Test
    public void testRegexActsTheSameAsAntDefaultExcludes() {
        List<String> includes = Collections.emptyList();
        Pattern pattern = createPattern(DEFAULT_GLOBAL_EXCLUDES);
        assertFalse(antMatcher("repo/amir~"));
        assertFalse(antMatcher("amir~"));
        assertFalse(antMatcher("#hey#"));
        assertFalse(antMatcher("repo/amir/#hey#"));
        assertFalse(antMatcher(".#temporary_file"));
        assertFalse(antMatcher("folder2/subfolder/.#temporary_file"));
        assertFalse(antMatcher("CVS"));
        assertFalse(antMatcher("CVS/.svn"));
        assertFalse(antMatcher("folder2/subfolder/CVS"));
        assertFalse(antMatcher("CVS/amir.txt"));
        assertFalse(antMatcher("folder2/subfolder/CVS/amir.txt"));
        assertFalse(antMatcher("folder1/file/%temp%"));
        assertFalse(antMatcher("CVS/subfolder/amir.txt"));
        assertFalse(antMatcher("folder1/._backup.txt"));
        assertFalse(antMatcher("._backup.txt"));
        assertFalse(antMatcher("folder2/subfolder/CVS/hey.txt"));
        assertFalse(antMatcher("folder2/subfolder/SCCS/hey.txt"));
        assertFalse(antMatcher("SCCS"));
        assertFalse(antMatcher("SCCS/amir.txt"));
        assertFalse(antMatcher("folder2/subfolder/.svn/hey.txt"));
        assertFalse(antMatcher("folder2/subfolder/.svn"));
        assertFalse(antMatcher(".svn/hey.txt"));
        assertFalse(antMatcher(".svn"));


        assertTrue(antMatcher("repo/#amir#/hey"));
        assertTrue(antMatcher("repo/amir"));
        assertTrue(antMatcher("repo/amir/hey"));
        assertTrue(antMatcher("folder2/subfolder/temporary_file"));
        assertTrue(antMatcher("folder2/subfolder"));
        assertTrue(antMatcher("folder1/file/temp"));
        assertTrue(antMatcher("folder1/backup.txt"));
        assertTrue(antMatcher("folder2/subfolder/hey.txt"));
        assertTrue(antMatcher("folder2/subfolder/hey.txt"));
        assertTrue(antMatcher("folder2/subfolder/hey.txt"));


        // REGEX!
        assertFalse(PathMatcher.matches("CVS/.svn", includes, pattern, false));
        assertFalse(PathMatcher.matches(".svn/hey.txt", includes, pattern, false));
        assertFalse(PathMatcher.matches(".svn", includes, pattern, false));
        assertFalse(PathMatcher.matches("SCCS", includes, pattern, false));
        assertFalse(PathMatcher.matches("SCCS/amir.txt", includes, pattern, false));
        assertFalse(PathMatcher.matches("._backup.txt", includes, pattern, false));
        assertFalse(PathMatcher.matches("CVS", includes, pattern, false));
        assertFalse(PathMatcher.matches("CVS/amir.txt", includes, pattern, false));
        assertFalse(PathMatcher.matches(".#temporary_file", includes, pattern, false));
        assertFalse(PathMatcher.matches("#hey#", includes, pattern, false));
        assertFalse(PathMatcher.matches("amir~", includes, pattern, false));
        assertFalse(PathMatcher.matches("CVS/subfolder/amir.txt", includes, pattern, false));
        assertFalse(PathMatcher.matches("folder2/subfolder/CVS/amir.txt", includes, pattern, false));
        assertFalse(PathMatcher.matches("repo/amir~", includes, pattern, false));
        assertFalse(PathMatcher.matches("repo/amir/#hey#", includes, pattern, false));
        assertFalse(
                PathMatcher.matches("folder2/subfolder/.#temporary_file", includes, pattern, false));
        assertFalse(PathMatcher.matches("folder2/subfolder/CVS", includes, pattern, false));
        assertFalse(PathMatcher.matches("folder1/file/%temp%", includes, pattern, false));
        assertFalse(PathMatcher.matches("folder1/._backup.txt", includes, pattern, false));
        assertFalse(PathMatcher.matches("folder2/subfolder/CVS/hey.txt", includes, pattern, false));
        assertFalse(PathMatcher.matches("folder2/subfolder/SCCS/hey.txt", includes, pattern, false));
        assertFalse(PathMatcher.matches("folder2/subfolder/.svn/hey.txt", includes, pattern, false));
        assertFalse(PathMatcher.matches("folder2/subfolder/.svn", includes, pattern, false));

        assertTrue(PathMatcher.matches("repo/#amir#/hey", includes, pattern, false));
        assertTrue(PathMatcher.matches("repo/amir", includes, pattern, false));
        assertTrue(PathMatcher.matches("repo/amir/hey", includes, pattern, false));
        assertTrue(PathMatcher.matches("folder2/subfolder/temporary_file", includes, pattern, false));
        assertTrue(PathMatcher.matches("folder2/subfolder", includes, pattern, false));
        assertTrue(PathMatcher.matches("folder1/file/temp", includes, pattern, false));
        assertTrue(PathMatcher.matches("folder1/backup.txt", includes, pattern, false));
        assertTrue(PathMatcher.matches("folder2/subfolder/hey.txt", includes, pattern, false));
        assertTrue(PathMatcher.matches("folder2/subfolder/hey.txt", includes, pattern, false));
        assertTrue(PathMatcher.matches("folder2/subfolder/hey.txt", includes, pattern, false));
    }

    @Test
    public void useDefaultExcludeAndGlobalExcludes() {
        assertTrue(PathMatcher.matches("amir~", Collections.emptyList(), GlobalExcludes.getGlobalExcludes(), false));
        assertTrue(PathMatcher.matches("a/b/c", Collections.emptyList(), GlobalExcludes.getGlobalExcludes(), false));
        context.getArtifactoryHome().getArtifactoryProperties()
                .setProperty(ConstantValues.useEmptyListForDefaultGlobalExcludes.getPropertyName(), "false");
        assertFalse(PathMatcher.matches("amir~", Collections.emptyList(), GlobalExcludes.getGlobalExcludes(), false));
        assertTrue(
                PathMatcher.matches("a/b/c", Collections.singleton("**/*"), GlobalExcludes.getGlobalExcludes(),
                        false));
        context.getArtifactoryHome().getArtifactoryProperties()
                .setProperty(ConstantValues.globalExcludes.getPropertyName(), "**/*~,**/CVS");
        assertFalse(
                PathMatcher.matches("amir~", Collections.singleton("**/*"), GlobalExcludes.getGlobalExcludes(),
                        false));
        assertFalse(
                PathMatcher.matches("repo/amir~", Collections.singleton("**/*"), GlobalExcludes.getGlobalExcludes(),
                        false));
        assertFalse(
                PathMatcher.matches("repo/CVS", Collections.singleton("**/*"), GlobalExcludes.getGlobalExcludes(),
                        false));
        assertTrue(
                PathMatcher.matches("a/b/c", Collections.singleton("**/*"), GlobalExcludes.getGlobalExcludes(),
                        false));
    }

    @Test
    public void useEmptyListAsExcludes() {
        assertTrue(PathMatcher.matches("amir~", null, GlobalExcludes.getGlobalExcludes(), false));
        assertTrue(PathMatcher.matches("a/b/c", null, GlobalExcludes.getGlobalExcludes(), false));
        assertTrue(
                PathMatcher.matches("repo/CVS", Collections.singleton("**/*"), GlobalExcludes.getGlobalExcludes(),
                        false));
        assertTrue(PathMatcher.matches(".DS_Store", null, GlobalExcludes.getGlobalExcludes(), false));
        File shortFileName = new File("/t");
        assertFalse(GlobalExcludes.isInGlobalExcludes(shortFileName));
        assertTrue(PathMatcher.matches("/t", null, GlobalExcludes.getGlobalExcludes(), false));
        assertEquals(new ArrayList<>(), GlobalExcludes.getGlobalExcludes());
    }

    @Test
    public void overrideGlobalExcludeWithEmptyString() {
        context.getArtifactoryHome().getArtifactoryProperties()
                .setProperty(ConstantValues.globalExcludes.getPropertyName(), "");
        context.getArtifactoryHome().getArtifactoryProperties()
                .setProperty(ConstantValues.useEmptyListForDefaultGlobalExcludes.getPropertyName(), "false");
        assertEquals(new ArrayList<>(), GlobalExcludes.getGlobalExcludes());
    }

    private boolean antMatcher(String path) {
        for (String exclude : DEFAULT_GLOBAL_EXCLUDES) {
            if (antPathMatcher.match(exclude, path)) {
                return false;
            }
        }
        return true;
    }

    private Pattern createPattern(Collection<String> excludes) {
        List<String> patterns = new ArrayList<>();
        for (String exclude : excludes) {
            patterns.add(AntGlob.toPattern(exclude).toString());
        }
        String combinedPattern = String.join("|", patterns);
        combinedPattern = "(?:" + combinedPattern + ")";
        return Pattern.compile(combinedPattern);
    }
}
