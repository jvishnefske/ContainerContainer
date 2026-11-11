package com.jfrog.build.model;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BuildIdImplTest {

    @Test
    void getStarted_ReturnsEmpty_WhenStartedIsZero() {
        BuildIdImpl buildId = BuildIdImpl.builder()
                .buildId(1L)
                .name("my-build")
                .number("1")
                .started(0L)
                .buildRepo("repo")
                .build();

        assertEquals(EMPTY, buildId.getStarted());
    }

    @Test
    void getStarted_ReturnsFormattedTime_WhenStartedIsPositive() {
        long millis = 1000L;
        BuildIdImpl buildId = BuildIdImpl.builder()
                .buildId(1L)
                .name("my-build")
                .number("1")
                .started(millis)
                .buildRepo("repo")
                .build();

        String started = buildId.getStarted();

        assertNotNull(started);
        assertEquals(millis, buildId.getStartedDate().getTime());
    }

    @Test
    void getStartedDate_ReturnsDateWithCorrectMillis_WhenStartedIsPositive() {
        long millis = 1234567890L;
        BuildIdImpl buildId = BuildIdImpl.builder()
                .buildId(1L)
                .name("my-build")
                .number("2")
                .started(millis)
                .buildRepo("repo")
                .build();

        Date date = buildId.getStartedDate();

        assertNotNull(date);
        assertEquals(millis, date.getTime());
    }
}
