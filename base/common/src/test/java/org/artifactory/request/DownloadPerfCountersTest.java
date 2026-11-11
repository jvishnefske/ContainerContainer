package org.artifactory.request;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DownloadPerfCountersTest {

    @Test
    void addRemoteDownloadTime_shouldSetValue_whenRemoteDownloadDurationMillisIsNull() {
        // Arrange
        DownloadPerfCounters counters = new DownloadPerfCounters();

        // Act
        counters.addRemoteDownloadTime(100L);

        // Assert
        assertEquals(100L, counters.getRemoteDownloadDurationMillis());
    }

    @Test
    void addRemoteDownloadTime_shouldAddToExistingValue_whenRemoteDownloadDurationMillisIsNotNull() {
        // Arrange
        DownloadPerfCounters counters = new DownloadPerfCounters();
        counters.setRemoteDownloadDurationMillis(50L);

        // Act
        counters.addRemoteDownloadTime(100L);

        // Assert
        assertEquals(150L, counters.getRemoteDownloadDurationMillis());
    }

    @Test
    void addRemoteDownloadTime_shouldAccumulateMultipleCalls() {
        // Arrange
        DownloadPerfCounters counters = new DownloadPerfCounters();

        // Act
        counters.addRemoteDownloadTime(100L);
        counters.addRemoteDownloadTime(200L);
        counters.addRemoteDownloadTime(50L);

        // Assert
        assertEquals(350L, counters.getRemoteDownloadDurationMillis());
    }

    @Test
    void addExternalWaitTime_shouldInitializeMapAndSetValue_whenExternalWaitTimeMsecIsNull() {
        // Arrange
        DownloadPerfCounters counters = new DownloadPerfCounters();

        // Act
        counters.addExternalWaitTime("xray", 100L);

        // Assert
        Map<String, Long> waitTimeMap = counters.getExternalWaitTimeMsec();
        assertEquals(1, waitTimeMap.size());
        assertEquals(100L, waitTimeMap.get("xray"));
    }

    @Test
    void addExternalWaitTime_shouldAddToExistingValue_whenServiceAlreadyExists() {
        // Arrange
        DownloadPerfCounters counters = new DownloadPerfCounters();
        counters.addExternalWaitTime("xray", 50L);

        // Act
        counters.addExternalWaitTime("xray", 100L);

        // Assert
        Map<String, Long> waitTimeMap = counters.getExternalWaitTimeMsec();
        assertEquals(1, waitTimeMap.size());
        assertEquals(150L, waitTimeMap.get("xray"));
    }

    @Test
    void addExternalWaitTime_shouldTrackMultipleServicesSeparately() {
        // Arrange
        DownloadPerfCounters counters = new DownloadPerfCounters();

        // Act
        counters.addExternalWaitTime("xray", 100L);
        counters.addExternalWaitTime("jfrog", 200L);
        counters.addExternalWaitTime("xray", 50L);

        // Assert
        Map<String, Long> waitTimeMap = counters.getExternalWaitTimeMsec();
        assertEquals(2, waitTimeMap.size());
        assertEquals(150L, waitTimeMap.get("xray"));
        assertEquals(200L, waitTimeMap.get("jfrog"));
    }

    @Test
    void addExternalWaitTime_shouldAccumulateMultipleCallsForSameService() {
        // Arrange
        DownloadPerfCounters counters = new DownloadPerfCounters();

        // Act
        counters.addExternalWaitTime("xray", 100L);
        counters.addExternalWaitTime("xray", 200L);
        counters.addExternalWaitTime("xray", 50L);

        // Assert
        Map<String, Long> waitTimeMap = counters.getExternalWaitTimeMsec();
        assertEquals(1, waitTimeMap.size());
        assertEquals(350L, waitTimeMap.get("xray"));
    }

    @Test
    void addExternalWaitTime_shouldHandleZeroDuration() {
        // Arrange
        DownloadPerfCounters counters = new DownloadPerfCounters();
        counters.addExternalWaitTime("xray", 100L);

        // Act
        counters.addExternalWaitTime("xray", 0L);

        // Assert
        Map<String, Long> waitTimeMap = counters.getExternalWaitTimeMsec();
        assertEquals(100L, waitTimeMap.get("xray"));
    }

    @Test
    void addExternalWaitTime_shouldHandleNegativeDuration() {
        // Arrange
        DownloadPerfCounters counters = new DownloadPerfCounters();
        counters.addExternalWaitTime("xray", 100L);

        // Act
        counters.addExternalWaitTime("xray", -50L);

        // Assert
        Map<String, Long> waitTimeMap = counters.getExternalWaitTimeMsec();
        assertEquals(50L, waitTimeMap.get("xray"));
    }
}

