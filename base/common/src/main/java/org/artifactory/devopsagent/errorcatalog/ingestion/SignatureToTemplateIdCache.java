package org.artifactory.devopsagent.errorcatalog.ingestion;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.concurrent.atomic.LongAdder;

/**
 * Small LRU cache mapping {@code signatureHash → templateId}.
 * Populated by the flush job after each successful template UPSERT; consulted by the flush job
 * to avoid issuing a SELECT-by-signature_hash for raw INSERTs.
 *
 * <p>Per-JVM only — the cache is not coordinated across cluster nodes. This is fine because
 * the templates table's UNIQUE constraint on {@code signature_hash} guarantees one row regardless
 * of which node first inserted it.</p>
 */
public class SignatureToTemplateIdCache {

    private static final int DEFAULT_MAX_SIZE = 50_000;
    private static final Duration DEFAULT_EXPIRE_AFTER_ACCESS = Duration.ofHours(1);

    private final Cache<String, Long> cache;
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();

    public SignatureToTemplateIdCache() {
        this(DEFAULT_MAX_SIZE, DEFAULT_EXPIRE_AFTER_ACCESS);
    }

    public SignatureToTemplateIdCache(int maxSize, Duration expireAfterAccess) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterAccess(expireAfterAccess)
                .build();
    }

    public Long getIfPresent(String signatureHash) {
        Long id = cache.getIfPresent(signatureHash);
        if (id != null) {
            hits.increment();
        } else {
            misses.increment();
        }
        return id;
    }

    public void put(String signatureHash, long templateId) {
        cache.put(signatureHash, templateId);
    }

    public void invalidate(String signatureHash) {
        cache.invalidate(signatureHash);
    }

    public long hitCount() {
        return hits.sum();
    }

    public long missCount() {
        return misses.sum();
    }

    public double hitRate() {
        long h = hits.sum();
        long m = misses.sum();
        long total = h + m;
        return total == 0 ? 0.0 : (double) h / total;
    }

    public long size() {
        return cache.estimatedSize();
    }
}
