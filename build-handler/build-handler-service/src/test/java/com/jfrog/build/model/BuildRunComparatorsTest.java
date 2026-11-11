package com.jfrog.build.model;

import com.google.common.collect.Lists;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.acl.model.BuildRunImpl;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildRunComparatorsTest {

    @Test
    void getComparatorFor_ReturnsLongComparator_WhenAllBuildNumbersAreNumeric() {
        BuildRun b1 = new BuildRunImpl("a", "1", new Date(1000));
        BuildRun b2 = new BuildRunImpl("b", "2", new Date(2000));
        List<BuildRun> builds = List.of(b1, b2);

        Comparator<BuildRun> comparator = BuildRunComparators.getComparatorFor(builds);

        assertTrue(comparator.compare(b1, b2) < 0);
        assertTrue(comparator.compare(b2, b1) > 0);
        assertEquals(0, comparator.compare(b1, b1));
    }

    @Test
    void getComparatorFor_ReturnsStringComparator_WhenAnyBuildNumberIsNonNumeric() {
        BuildRun b1 = new BuildRunImpl("a", "11a", new Date(1000));
        BuildRun b2 = new BuildRunImpl("b", "11b", new Date(1000));
        List<BuildRun> builds = List.of(b1, b2);

        Comparator<BuildRun> comparator = BuildRunComparators.getComparatorFor(builds);

        assertTrue(comparator.compare(b1, b2) < 0);
        assertTrue(comparator.compare(b2, b1) > 0);
        assertEquals(0, comparator.compare(b1, b1));
    }

    @Test
    void getComparatorFor_ComparesByStartedDateThenName_WhenNumbersEqual() {
        Date sameDate = new Date(1000);
        BuildRun b1 = new BuildRunImpl("a", "1", sameDate);
        BuildRun b2 = new BuildRunImpl("b", "1", sameDate);
        List<BuildRun> builds = List.of(b1, b2);

        Comparator<BuildRun> comparator = BuildRunComparators.getComparatorFor(builds);

        assertTrue(comparator.compare(b1, b2) < 0);
        assertTrue(comparator.compare(b2, b1) > 0);
    }

    @Test
    void getBuildStartDateComparator_ReturnsNegative_WhenFirstBuildOlder() {
        Calendar cal = Calendar.getInstance();
        cal.set(2014, Calendar.JANUARY, 1);
        BuildRun older = new BuildRunImpl("a", "1", cal.getTime());
        cal.set(2014, Calendar.FEBRUARY, 1);
        BuildRun newer = new BuildRunImpl("b", "2", cal.getTime());

        Comparator<BuildRun> comparator = BuildRunComparators.getBuildStartDateComparator();

        assertTrue(comparator.compare(older, newer) < 0);
        assertTrue(comparator.compare(newer, older) > 0);
        assertEquals(0, comparator.compare(older, older));
    }

    @Test
    void getBuildStartDateComparator_ReturnsZero_WhenEitherBuildIsNull() {
        BuildRun b = new BuildRunImpl("a", "1", new Date());
        Comparator<BuildRun> comparator = BuildRunComparators.getBuildStartDateComparator();

        assertEquals(0, comparator.compare(null, b));
        assertEquals(0, comparator.compare(b, null));
    }

    @Test
    void getComparatorFor_ReturnsZero_WhenEitherBuildIsNull() {
        BuildRun b = new BuildRunImpl("a", "1", new Date());
        List<BuildRun> builds = List.of(b);
        Comparator<BuildRun> comparator = BuildRunComparators.getComparatorFor(builds);

        assertEquals(0, comparator.compare(null, b));
        assertEquals(0, comparator.compare(b, null));
    }

    @Test
    void testDateComparator() {
        Calendar instance1 = Calendar.getInstance();
        instance1.set(2014, Calendar.JANUARY, 1);
        Calendar instance2 = Calendar.getInstance();
        instance2.set(2014, Calendar.FEBRUARY, 1);

        BuildRun b1 = new BuildRunImpl("Alice", "1", instance1.getTime());
        BuildRun b2 = new BuildRunImpl("Bob", "2", instance2.getTime());

        Comparator<BuildRun> dateComparator = BuildRunComparators.getBuildStartDateComparator();
        assertEquals(-1, dateComparator.compare(b1, b2));
        assertEquals(1, dateComparator.compare(b2, b1));
        assertEquals(0, dateComparator.compare(b1, b1));
    }

    @Test
    public void testNumberComparator() {
        BuildRun b1 = new BuildRunImpl("Alice", "1", new Date());
        BuildRun b2 = new BuildRunImpl("Bob", "2", new Date());
        ArrayList<BuildRun> buildRuns = Lists.newArrayList(b1, b2);

        //Also implicit check of the comparator factory, in this case should be Number compare.
        Comparator<BuildRun> numberComparator = BuildRunComparators.getComparatorFor(buildRuns);
        assertEquals(-1, numberComparator.compare(b1, b2));
        assertEquals(1, numberComparator.compare(b2, b1));
        assertEquals(0, numberComparator.compare(b1, b1));
    }

    @Test
    public void testStringComparator() {
        BuildRun b1 = new BuildRunImpl("Alice", "11a", new Date());
        BuildRun b2 = new BuildRunImpl("Bob", "11b", new Date());
        ArrayList<BuildRun> buildRuns = Lists.newArrayList(b1, b2);

        //Also implicit check of the comparator factory, in this case should be String compare.
        Comparator<BuildRun> numberComparator = BuildRunComparators.getComparatorFor(buildRuns);
        assertEquals(-1, numberComparator.compare(b1, b2));
        assertEquals(1, numberComparator.compare(b2, b1));
        assertEquals(0, numberComparator.compare(b1, b1));
    }
}
