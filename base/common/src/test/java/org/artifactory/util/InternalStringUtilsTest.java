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

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Yinon Avraham
 */
@Test
public class InternalStringUtilsTest {

    @Test(dataProvider = "provideCompareNullLast")
    public void testCompareNullLast(String s1, String s2, int expected) {
        assertEquals(Math.signum(InternalStringUtils.compareNullLast(s1, s2)), Math.signum(expected));
    }

    @Test(dataProvider = "provideRemoveUntil")
    public void testRemoveUntil(char untilChar, String testString, boolean inclusive, String expected) {
        String outPut = InternalStringUtils.removeUntil(testString, untilChar, inclusive);
        assertEquals(outPut, expected);
    }

    @Test
    public void testContainsEmojiEmptyInput() {
        assertFalse(InternalStringUtils.containsEmoji(""), "Empty string should not contain emoji");
        assertFalse(InternalStringUtils.containsEmoji(null), "Null input should not contain emoji");
    }

    public void testContainsEmojiWithEmoji() {
        assertTrue(InternalStringUtils.containsEmoji("Hello 😀"), "String with emoji should return true");
        assertTrue(InternalStringUtils.containsEmoji("\uD83C\uDF08"), "String with emoji should return true");
    }

    @DataProvider
    private Object[][] provideCompareNullLast() {
        return new Object[][] {
                {"abc", "xyz", -1},
                {"abc", "abc", 0},
                {"xyz", "abc", 1},
                {"abc", null, 1},
                {null, "abc", -1},
                {null, null, 0},
        };
    }

    @DataProvider
    private Object[][] provideRemoveUntil() {
        return new Object[][] {
                {'/', "buck-bucketName/filestore", true, "filestore"},
                {'/', "buck-bucketName/filestore", false, "/filestore"},
                {' ', "buck-bucketName/filestore", true, "buck-bucketName/filestore"},
                {' ', "buck-bucketName/filestore", false, "buck-bucketName/filestore"},
                {'c', "buck-bucketName/filestore", true, "k-bucketName/filestore"},
                {'c', "buck-bucketName/filestore", false, "ck-bucketName/filestore"},
                {'z', "buck-bucketName/filestore", true, "buck-bucketName/filestore"},
                {'z', "buck-bucketName/filestore", false, "buck-bucketName/filestore"},
                {'b', "buck-bucketName/filestore", true, "uck-bucketName/filestore"},
                {'b', "buck-bucketName/filestore", false, "buck-bucketName/filestore"},
                {'a', "", true, ""},
                {'a', "", false, ""},
                {'a', "a", true, ""},
                {'a', "a", false, "a"},
                {' ', "", true, ""},
                {' ', "", false, ""},
        };
    }

}