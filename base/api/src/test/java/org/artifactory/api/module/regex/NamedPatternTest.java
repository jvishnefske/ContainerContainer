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

package org.artifactory.api.module.regex;

import com.github.benmanes.caffeine.cache.Cache;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.regex.Pattern;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

@Test
public class NamedPatternTest {

    private static final String NAMED_REGEX = "(?<module>\\w+)-(?<version>\\d+\\.\\d+)";
    private static final String INPUT = "artifact-1.0";

    public void compileReturnsCachedInstanceForSameRegex() {
        NamedPattern first = NamedPattern.compile(NAMED_REGEX);
        NamedPattern second = NamedPattern.compile(NAMED_REGEX);

        assertSame(first, second);
    }

    public void compileWithFlagsReturnsCachedInstanceForSameRegexAndFlags() {
        int flags = Pattern.CASE_INSENSITIVE;
        NamedPattern first = NamedPattern.compile(NAMED_REGEX, flags);
        NamedPattern second = NamedPattern.compile(NAMED_REGEX, flags);

        assertSame(first, second);
    }

    public void compileWithDifferentFlagsReturnsDistinctInstances() {
        NamedPattern defaultFlags = NamedPattern.compile(NAMED_REGEX);
        NamedPattern caseInsensitive = NamedPattern.compile(NAMED_REGEX, Pattern.CASE_INSENSITIVE);

        assertNotSame(defaultFlags, caseInsensitive);
    }

    public void singleArgAndZeroFlagOverloadsShareCanonicalKey() {
        NamedPattern singleArg = NamedPattern.compile(NAMED_REGEX);
        NamedPattern explicitZeroFlags = NamedPattern.compile(NAMED_REGEX, 0);

        assertSame(singleArg, explicitZeroFlags);
    }

    public void cacheIsBoundedAndEvictsBeyondMaxSize() throws Exception {
        for (long i = 0; i < NamedPattern.MAX_CACHE_SIZE * 2; i++) {
            NamedPattern.compile("(?<group" + i + ">\\w+)");
        }

        assertTrue(currentCacheSize() <= NamedPattern.MAX_CACHE_SIZE,
                "Cache must stay bounded by MAX_CACHE_SIZE to avoid unbounded heap growth");
    }

    public void cachedPatternPreservesMatchingBehavior() {
        NamedPattern pattern = NamedPattern.compile(NAMED_REGEX);
        NamedMatcher matcher = pattern.matcher(INPUT);

        assertTrue(matcher.matches());
        assertEquals(matcher.group("module"), "artifact");
        assertEquals(matcher.group("version"), "1.0");
    }

    @SuppressWarnings("unchecked")
    private static long currentCacheSize() throws Exception {
        Field cacheField = NamedPattern.class.getDeclaredField("CACHE");
        cacheField.setAccessible(true);
        Cache<String, NamedPattern> cache = (Cache<String, NamedPattern>) cacheField.get(null);
        cache.cleanUp();
        return cache.estimatedSize();
    }
}
