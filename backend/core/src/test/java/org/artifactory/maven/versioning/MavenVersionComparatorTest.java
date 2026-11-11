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

package org.artifactory.maven.versioning;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests the {@link MavenVersionComparator}.
 *
 * @author Yossi Shaul
 * @author Natalie Berni
 */
@Test
public class MavenVersionComparatorTest {
    private final MavenVersionComparator comparator = new MavenVersionComparator();

    public void numericMajorVersionOnly() {
        assertEquals(comparator.compare("1", "2"), -1, "2>1");
        assertEquals(comparator.compare("2", "1"), 1, "2>1");
        assertEquals(comparator.compare("3", "3"), 0, "3=3");
    }

    public void numericMajorMinor() {
        assertEquals(comparator.compare("1.13", "2.0"), -1, "2.0>1.1");
        assertEquals(comparator.compare("5.06", "5.08"), -1, "5.06<5.08");
        assertEquals(comparator.compare("5.06", "5"), 1, "5<5.06");
        assertEquals(comparator.compare("5.0", "5"), 0, "5.0=5");
    }

    public void numericMajorMinorBug() {
        assertEquals(comparator.compare("2.1.2", "2.1.3"), -1, "2.1.3>2.1.2");
        assertEquals(comparator.compare("2.2.2", "2.1.3"), 1, "2.1.3<2.2.2");
        assertEquals(comparator.compare("2.2.2", "3.2.2"), -1, "2.2.2<3.2.2");

        assertEquals(comparator.compare("2.2.2", "3"), -1, "2.2.2<3");
        assertEquals(comparator.compare("2.2.2", "3.0"), -1, "2.2.2<3.0");
        assertEquals(comparator.compare("2.2.2", "3.0.0"), -1, "2.2.2<3.0.0");
    }

    public void alphaBetaMilestoneRcGaVersions() {

        // alpha before beta
        assertEquals(comparator.compare("2.1.0-alpha", "2.1.0-alpha-1"), -1, "2.1.0-alpha<2.1.0-alpha-1");
        assertEquals(comparator.compare("2.1.0-alpha-1", "2.1.0-alpha-2"), -1, "2.1.0-alpha-1<2.1.0-alpha-2");
        assertEquals(comparator.compare("2.1.0-alpha-2", "2.1.0-beta-1"), -1, "2.1.0-alpha-2<2.1.0-beta-1");

        // beta before release candidate
        assertTrue(comparator.compare("2.1.0-beta-2", "2.1.0-rc") < 0, "2.1.0-beta-2<2.1.0-rc");
        assertTrue(comparator.compare("2.1.0-beta-2", "2.1.0-rc-1") < 0, "2.1.0-beta-2<2.1.0-rc-1");

        // beta before milestone
        assertTrue(comparator.compare("2.1.0-alpha-1", "2.1.0-m1") < 0, "2.1.0-alpha-1<2.1.0-m1");
        assertTrue(comparator.compare("2.1.0-beta-1", "2.1.0-m-1") < 0, "2.1.0-beta-1<2.1.0-m-1");

        // milestone before release candidate
        assertTrue(comparator.compare("2.1.0-m1", "2.1.0-ga") < 0, "2.1.0-m1<2.1.0-ga");
        assertTrue(comparator.compare("2.1.0-m5", "2.1.0-ga-1") < 0, "2.1.0-m5<2.1.0-ga-1");

        // release candidate before general availability
        assertEquals(comparator.compare("2.1.0-rc-1", "2.1.0-ga"), -1, "2.1.0-rc-1<2.1.0-ga");
        assertEquals(comparator.compare("2.1.0-ga", "2.1.0-ga-1"), -1, "2.1.0-ga<2.1.0-ga-1");

        // final are equals
        assertEquals(comparator.compare("5.1-ga", "5.1"), 0, "5.1-ga=5.1");
        assertEquals(comparator.compare("5.1-ga", "5.1-final"), 0, "5.1-ga=5.1-final");

        // release is better
        assertTrue(comparator.compare("4.0.0", "4.0.0-alpha") > 0, "4.0.0>4.0.0-alpha");
        assertTrue(comparator.compare("4.0.0", "4.0.0-beta") > 0, "4.0.0>4.0.0-beta");
        assertEquals(comparator.compare("4.0.0", "4.0.0-rc"), 1, "4.0.0>4.0.0-rc");
        assertEquals(comparator.compare("4.0.0", "4.0.0-rc1"), 1, "4.0.0>4.0.0-rc1");
        assertEquals(comparator.compare("4.0.0", "4.0.0-rc6"), 1, "4.0.0>4.0.0-rc6");
    }

    public void snapshotVersions() {
        assertEquals(comparator.compare("2.1.0-alpha", "2.1.0-SNAPSHOT"), 1, "2.1.0-alpha>2.1.0-SNAPSHOT");
        assertTrue(comparator.compare("2.1.0", "2.1.0-SNAPSHOT") > 0, "2.1.0>2.1.0-SNAPSHOT");
    }

    @Test
    public void testNullHandling() {
        try {
            comparator.compare(null, "1.0.0");
            fail("Should throw NullPointerException when first version is null");
        } catch (NullPointerException expected) {
            // Expected exception
        }

        try {
            comparator.compare("1.0.0", null);
            fail("Should throw NullPointerException when second version is null");
        } catch (NullPointerException expected) {
            // Expected exception
        }
    }

    @Test
    public void testEmptyStrings() {
        assertEquals(comparator.compare("", ""), 0, "Empty strings should be equal");
        assertTrue(comparator.compare("", "1.0.0") < 0, "Empty string should be less than any version");
        assertTrue(comparator.compare("1.0.0", "") > 0, "Any version should be greater than empty string");
    }

    @Test
    public void testWhitespaceHandling() {
        assertEquals(comparator.compare("1.0.0", " 1.0.0 "), 1, "Versions with whitespace should not be equal");
        assertEquals(comparator.compare(" 1.0.0", "1.0.0 "), -1, "Versions with whitespace should not be equal");
    }

    @Test
    public void testLeadingZeros() {
        assertEquals(comparator.compare("1.0.0", "1.00.0"), 0, "Leading zeros should be ignored");
        assertEquals(comparator.compare("01.2.3", "1.2.3"), 0, "Leading zeros should be ignored");
        assertEquals(comparator.compare("1.02.3", "1.2.3"), 0, "Leading zeros should be ignored");
    }

    @Test
    public void testDifferentSegmentCounts() {
        assertEquals(comparator.compare("1.0", "1.0.0"), 0, "Trailing zeros should be ignored");
        assertEquals(comparator.compare("1.0.0.0", "1.0.0"), 0, "Trailing zeros should be ignored");
        assertTrue(comparator.compare("1.0.1", "1.0") > 0, "More specific version should be greater");
    }

    @DataProvider(name = "qualifierOrderingProvider")
    public Object[][] qualifierOrderingProvider() {
        return new Object[][] {
                // Order should be: SNAPSHOT < alpha < beta < milestone < rc < (no qualifier / ga / final)
                {"1.0-SNAPSHOT", "1.0-alpha", true},
                {"1.0-SNAPSHOT", "1.0-beta", true},
                {"1.0-SNAPSHOT", "1.0-milestone", true},
                {"1.0-SNAPSHOT", "1.0-m1", true},
                {"1.0-SNAPSHOT", "1.0-rc", true},
                {"1.0-SNAPSHOT", "1.0", true},
                {"1.0-SNAPSHOT", "1.0-ga", true},
                {"1.0-SNAPSHOT", "1.0-final", true},

                {"1.0-alpha", "1.0-beta", true},
                {"1.0-alpha", "1.0-milestone", true},
                {"1.0-alpha", "1.0-m1", true},
                {"1.0-alpha", "1.0-rc", true},
                {"1.0-alpha", "1.0", true},
                {"1.0-alpha", "1.0-ga", true},
                {"1.0-alpha", "1.0-final", true},

                {"1.0-beta", "1.0-milestone", true},
                {"1.0-beta", "1.0-m1", true},
                {"1.0-beta", "1.0-rc", true},
                {"1.0-beta", "1.0", true},
                {"1.0-beta", "1.0-ga", true},
                {"1.0-beta", "1.0-final", true},

                {"1.0-milestone", "1.0-rc", true},
                {"1.0-milestone", "1.0", true},
                {"1.0-milestone", "1.0-ga", true},
                {"1.0-milestone", "1.0-final", true},

                {"1.0-m1", "1.0-rc", true},
                {"1.0-m1", "1.0", true},
                {"1.0-m1", "1.0-ga", true},
                {"1.0-m1", "1.0-final", true},

                {"1.0-rc", "1.0", true},
                {"1.0-rc", "1.0-ga", true},
                {"1.0-rc", "1.0-final", true},

                // Equivalence relationships
                {"1.0", "1.0-ga", false},
                {"1.0", "1.0-final", false},
                {"1.0-ga", "1.0-final", false},

                // Numeric qualifiers
                {"1.0-alpha1", "1.0-alpha2", true},
                {"1.0-alpha2", "1.0-alpha10", true},
                {"1.0-beta1", "1.0-beta2", true},
                {"1.0-milestone1", "1.0-milestone2", true},
                {"1.0-m1", "1.0-m2", true},
                {"1.0-rc1", "1.0-rc2", true},

                // Mixed case should be equal
                {"1.0-ALPHA", "1.0-alpha", false},
                {"1.0-BETA", "1.0-beta", false},
                {"1.0-RC", "1.0-rc", false},
                {"1.0-MILESTONE", "1.0-milestone", false},
                {"1.0-FINAL", "1.0-final", false},
                {"1.0-GA", "1.0-ga", false}
        };
    }

    @Test(dataProvider = "qualifierOrderingProvider")
    public void testQualifierOrdering(String v1, String v2, boolean lessThan) {
        if (lessThan) {
            assertTrue(comparator.compare(v1, v2) < 0, v1 + " should be less than " + v2);
        } else {
            assertEquals(comparator.compare(v1, v2), 0, v1 + " should be equal to " + v2);
        }
    }

    @Test
    public void testUnusualFormats() {
        // Test versions with unusual formats
        assertEquals(comparator.compare("1.0.0.Final", "1.0.0"), 0,
                "Final qualifier should be equivalent to no qualifier");
        assertTrue(comparator.compare("1.0.0-rc", "1.0.0") < 0,
                "RC qualifier should be less than release version");
        assertEquals(comparator.compare("1.0.0.GA", "1.0.0"), 0,
                "GA qualifier should be equivalent to no qualifier");
        assertTrue(comparator.compare("1.0.0-alpha", "1.0.0-rc") < 0,
                "alpha qualifier should be less than RC");
        assertTrue(comparator.compare("1.0.0-beta", "1.0.0-rc") < 0,
                "beta qualifier should be less than RC");
    }

    @Test
    public void testDottedAndHyphenatedQualifiers() {
        assertEquals(comparator.compare("1.0.0.alpha-1", "1.0.0-alpha.1"), 1,
                "Dot and hyphen should not be treated similarly in qualifiers");
        assertTrue(comparator.compare("1.0.0.alpha.1", "1.0.0.alpha.2") < 0,
                "Numeric parts of qualifiers should be ordered");
    }

    @Test
    public void testMixedNumericAndStringSegments() {
        assertTrue(comparator.compare("1.0.a", "1.0.b") < 0,
                "String segments should be compared lexically");
        assertTrue(comparator.compare("1.a.0", "1.b.0") < 0,
                "String segments should be compared lexically");
    }

    @Test
    public void testExtremeLongVersions() {
        String longVersion1 = "1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.1";
        String longVersion2 = "1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.2";
        assertTrue(comparator.compare(longVersion1, longVersion2) < 0,
                "Long versions should be compared correctly");
    }

    @Test
    public void testQualifierWithAlphaNumericVersions() {
        // Tests when qualifiers have both letters and numbers
        assertTrue(comparator.compare("1.0-alpha1", "1.0-alpha2") < 0);
        assertTrue(comparator.compare("1.0-alpha2", "1.0-alpha10") < 0,
                "Numeric part of qualifiers should be compared numerically");
        assertTrue(comparator.compare("1.0-beta1", "1.0-beta10") < 0);
    }

    @Test
    public void testSnapshots() {
        // SNAPSHOT is treated specially and comes before other qualifiers
        assertTrue(comparator.compare("1.0-SNAPSHOT", "1.0-alpha") < 0);
        assertTrue(comparator.compare("1.0-SNAPSHOT", "1.0") < 0);

        // Snapshot at different version numbers
        assertTrue(comparator.compare("1.0-SNAPSHOT", "2.0-SNAPSHOT") < 0);
        assertTrue(comparator.compare("1.9-SNAPSHOT", "1.10-SNAPSHOT") < 0);

        // SNAPSHOT is case-insensitive
        assertEquals(comparator.compare("1.0-SNAPSHOT", "1.0-snapshot"), 0);

        // Timestamp snapshots (Maven generates these)
        assertTrue(comparator.compare("1.0-20220101.123456-1", "1.0") > 0,
                "Timestamp snapshots should be gather than release");
    }

    @Test
    public void testDevVersions() {
        // Test "dev" qualifier which is common in some ecosystems
        assertTrue(comparator.compare("1.0-dev", "1.0-alpha") > 0);
        assertTrue(comparator.compare("1.0-dev", "1.0") > 0);
        assertTrue(comparator.compare("1.0-dev1", "1.0-dev2") < 0);
    }

    @Test
    public void testTransitivity() {
        // If a > b and b > c, then a > c
        assertTrue(comparator.compare("3.0.0", "2.0.0") > 0);
        assertTrue(comparator.compare("2.0.0", "1.0.0") > 0);
        assertTrue(comparator.compare("3.0.0", "1.0.0") > 0);

        // For qualifiers
        assertTrue(comparator.compare("1.0", "1.0-rc") > 0);
        assertTrue(comparator.compare("1.0-rc", "1.0-beta") > 0);
        assertTrue(comparator.compare("1.0", "1.0-beta") > 0);
    }

    @Test
    public void testQualifierPermutations() {
        // This tests all permutations of common qualifiers
        String[] qualifiers = new String[] {
                "SNAPSHOT", "alpha", "beta",  "milestone", "rc", "", "ga", "final", "sp", "m"
        };

        // Test that each qualifier is correctly ordered relative to all others
        for (int i = 0; i < qualifiers.length; i++) {
            for (int j = i + 1; j < qualifiers.length; j++) {
                String v1 = "1.0-" + qualifiers[i];
                String v2 = "1.0-" + qualifiers[j];

                // Skip comparing empty qualifier with ga/final as they're equivalent
                boolean iGaOrFinal = qualifiers[i].equals("ga") || qualifiers[i].equals("final");
                boolean jGaOrFinal = qualifiers[j].equals("ga") || qualifiers[j].equals("final");
                if ((qualifiers[i].isEmpty() && jGaOrFinal) ||
                        (qualifiers[j].isEmpty() && iGaOrFinal) ||(jGaOrFinal && iGaOrFinal)) {
                    assertEquals(comparator.compare(v1, v2), 0,
                            v1 + " should be equal to " + v2);
                } else if (i < j) { // Otherwise, earlier in the array should be less than later
                    assertTrue(comparator.compare(v1, v2) < 0,
                            v1 + " should be less than " + v2);
                }
            }
        }
    }

    @Test
    public void testOsgiVersionFormat() {
        // OSGi versions like 1.2.0.qualifier
        assertTrue(comparator.compare("1.2.0.qualifier", "1.2.0") > 0);
        assertTrue(comparator.compare("1.2.0.qualifier", "1.2.1") < 0);
        assertTrue(comparator.compare("1.2.0.alpha", "1.2.0.beta") < 0);
    }

    @Test
    public void testServicePacks() {
        // SP (service pack) should be lower than GA/release
        assertTrue(comparator.compare("1.0", "1.0-sp1") < 0);
        assertTrue(comparator.compare("1.0-sp1", "1.0-sp2") < 0);
        assertTrue(comparator.compare("1.0-ga", "1.0-sp1") < 0);
    }

    @Test
    public void testCalendarVersions() {
        // Calendar versioning (CalVer) format
        assertTrue(comparator.compare("2021.1.0", "2022.1.0") < 0);
        assertTrue(comparator.compare("2022.1.0", "2022.2.0") < 0);
        assertTrue(comparator.compare("2022.12.0", "2023.1.0") < 0);
    }
}
