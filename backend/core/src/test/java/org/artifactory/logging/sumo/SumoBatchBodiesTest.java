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

package org.artifactory.logging.sumo;

import com.google.common.base.Joiner;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.testng.Assert.assertEquals;

public class SumoBatchBodiesTest {

    @Test
    public void testEncodeUtf8EmptyBatch() {
        assertEquals(SumoBatchBodies.encodeUtf8(Collections.emptyList()), new byte[0]);
    }

    @Test(dataProvider = "provideEncodeUtf8Batches")
    public void testEncodeUtf8MatchesLegacyJoinAndEncode(List<String> lines) {
        byte[] expected = Joiner.on("").join(lines).getBytes(StandardCharsets.UTF_8);
        assertEquals(SumoBatchBodies.encodeUtf8(lines), expected);
    }

    @DataProvider
    private Object[][] provideEncodeUtf8Batches() {
        return new Object[][] {
                {Collections.singletonList("[INFO ] - single line\n")},
                {Arrays.asList("[TRACE] - line 1\n", "[DEBUG] - line 2\n", "[WARN ] - line 3\n")},
                {Arrays.asList("", "non-empty\n")},
                {Collections.singletonList("unicode: café 日本語\n")},
        };
    }
}
