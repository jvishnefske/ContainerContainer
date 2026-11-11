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

package org.artifactory.traffic.read;

import com.google.common.base.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.NullReader;
import org.apache.commons.lang3.time.DateUtils;
import org.artifactory.traffic.entry.LegacyTrafficEntry;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.jfrog.common.ResourceUtils.getResourceAsFile;
import static org.testng.Assert.assertTrue;

/**
 * Unit test for the TrafficStreamParser
 *
 * @author Noam Tenne
 */
@Test
public class TrafficStreamParserTest {
    private SimpleDateFormat entryDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    private File trafficLogFile = getResourceAsFile(
            "/org/artifactory/traffic/logs/artifactory-traffic.1238313542120.log");

    /**
     * Supply the parser with a null object instead of a reader
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNullReader() throws IOException {
        TrafficStreamParser.parse(null, null, null);
    }

    /**
     * Supply the parser with null objects instead of dates
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNullDates() throws IOException {
        TrafficStreamParser.parse(new NullReader(0), null, null);
    }

    /**
     * Supply the parser with invalid dates
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalidDates() throws IOException {
        long currentTime = System.currentTimeMillis();
        Date startDate = new Date(currentTime + 1000);
        Date endDate = new Date(currentTime);
        TrafficStreamParser.parse(new NullReader(0), startDate, endDate);
    }

    /**
     * Test the parser with valid in-range dates
     */
    public void testWithinRange() throws IOException, ParseException {
        List<String> list = FileUtils.readLines(trafficLogFile, Charsets.UTF_8);
        String firstEntry = list.get(0);
        String[] splitFirstEntry = firstEntry.split("\\|");
        String lastEntry = list.get(list.size() - 1);
        String[] splitLastEntry = lastEntry.split("\\|");

        Date startDate = entryDateFormat.parse(splitFirstEntry[0]);
        Date endDate = entryDateFormat.parse(splitLastEntry[0]);
        startDate = DateUtils.truncate(startDate, Calendar.MINUTE);
        endDate = DateUtils.addMinutes(DateUtils.round(endDate, Calendar.MINUTE), 1);
        List<LegacyTrafficEntry> entries =
                TrafficStreamParser.parse(new FileReader(trafficLogFile), startDate, endDate);

        Assert.assertFalse(entries.isEmpty(), "Log parsing should return results.");
        for (LegacyTrafficEntry entry : entries) {
            long currentEntryDate = entry.getTime();
            assertTrue(currentEntryDate > startDate.getTime(), "Current entry date should be within range.");
            assertTrue(currentEntryDate < endDate.getTime(), "Current entry date should be within range.");
        }
    }

    /**
     * Test the parser with dates that should be out of range
     */
    public void testOutOfRange() throws IOException {
        List<LegacyTrafficEntry> trafficEntryList =
                TrafficStreamParser.parse(new FileReader(trafficLogFile), new Date(), new Date());
        assertTrue(trafficEntryList.isEmpty(), "Log parsing shouldn't return results.");
    }
}