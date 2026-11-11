package org.artifactory.devopsagent.errorcatalog.ingestion;

import org.artifactory.devopsagent.errorcatalog.ErrorCatalogEvent;

import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class ErrorCatalogEventBufferTest {

    @Test
    public void testOfferAggregatesBySignature() {
        ErrorCatalogEventBuffer buffer = new ErrorCatalogEventBuffer(100, 100);
        ErrorCatalogEvent a = event("sigA", "WARN", 1000L);
        ErrorCatalogEvent b = event("sigA", "WARN", 1500L);
        ErrorCatalogEvent c = event("sigB", "ERROR", 2000L);

        buffer.offer(a);
        buffer.offer(b);
        buffer.offer(c);

        ErrorCatalogEventBuffer.DrainResult result = buffer.drain();
        assertEquals(result.aggregated().size(), 2);
        ErrorCatalogEventBuffer.AggregatedEntry aggA = result.aggregated().get("sigA");
        assertEquals(aggA.occurrenceCount(), 2L);
        assertEquals(aggA.firstOccurredAtMs(), 1000L);
        assertEquals(aggA.lastOccurredAtMs(), 1500L);
        assertEquals(result.rawEvents().size(), 3);
    }

    @Test
    public void testSignatureCapDropsExcess() {
        ErrorCatalogEventBuffer buffer = new ErrorCatalogEventBuffer(2, 100);
        buffer.offer(event("s1", "WARN", 1L));
        buffer.offer(event("s2", "WARN", 2L));
        buffer.offer(event("s3", "WARN", 3L));
        assertTrue(buffer.overflowDroppedCount() >= 1);
    }

    @Test
    public void testRawQueueCapDropsExcessWithoutLosingAggregated() {
        ErrorCatalogEventBuffer buffer = new ErrorCatalogEventBuffer(100, 2);
        buffer.offer(event("s1", "ERROR", 1L));
        buffer.offer(event("s1", "ERROR", 2L));
        buffer.offer(event("s1", "ERROR", 3L));
        assertEquals(buffer.signatureCount(), 1, "aggregated must still see all three");
        assertTrue(buffer.rawDroppedCount() >= 1);
    }

    @Test
    public void testDrainResetsBuffer() {
        ErrorCatalogEventBuffer buffer = new ErrorCatalogEventBuffer(100, 100);
        buffer.offer(event("s1", "WARN", 1L));
        Map<String, ErrorCatalogEventBuffer.AggregatedEntry> first = buffer.drain().aggregated();
        assertNotNull(first.get("s1"));
        Map<String, ErrorCatalogEventBuffer.AggregatedEntry> secondDrain = buffer.drain().aggregated();
        assertTrue(secondDrain.isEmpty(), "After drain, buffer must be empty");
    }

    private static ErrorCatalogEvent event(String sig, String severity, long ts) {
        return new ErrorCatalogEvent(severity, "com.example.Foo", null,
                "template", "raw message", sig, ts);
    }
}
