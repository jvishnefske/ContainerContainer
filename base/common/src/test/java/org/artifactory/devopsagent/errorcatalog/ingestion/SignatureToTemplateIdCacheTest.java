package org.artifactory.devopsagent.errorcatalog.ingestion;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class SignatureToTemplateIdCacheTest {

    @Test
    public void testGetThenPutRoundTripIncrementsHitAndMissCounters() {
        SignatureToTemplateIdCache cache = new SignatureToTemplateIdCache();

        assertNull(cache.getIfPresent("hash-A"));
        assertEquals(cache.missCount(), 1L);
        assertEquals(cache.hitCount(), 0L);

        cache.put("hash-A", 42L);

        assertEquals(cache.getIfPresent("hash-A"), Long.valueOf(42L));
        assertEquals(cache.hitCount(), 1L);
        assertEquals(cache.missCount(), 1L);
    }

    @Test
    public void testHitRateReturnsZeroWhenNoLookups() {
        SignatureToTemplateIdCache cache = new SignatureToTemplateIdCache();
        assertEquals(cache.hitRate(), 0.0, 0.0);
        assertEquals(cache.size(), 0L);
    }

    @Test
    public void testInvalidateRemovesEntryAndNextLookupCountsAsMiss() {
        SignatureToTemplateIdCache cache = new SignatureToTemplateIdCache();
        cache.put("hash-B", 7L);
        assertEquals(cache.getIfPresent("hash-B"), Long.valueOf(7L));

        cache.invalidate("hash-B");

        assertNull(cache.getIfPresent("hash-B"));
        assertTrue(cache.missCount() >= 1L, "invalidate-then-get must count as a miss");
    }
}
