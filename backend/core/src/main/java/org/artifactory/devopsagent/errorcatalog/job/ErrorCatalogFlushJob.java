package org.artifactory.devopsagent.errorcatalog.job;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.devopsagent.errorcatalog.ErrorCatalogEvent;
import org.artifactory.devopsagent.errorcatalog.ingestion.ErrorCatalogEventBuffer;
import org.artifactory.devopsagent.errorcatalog.ingestion.SignatureToTemplateIdCache;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.artifactory.storage.db.devopsagent.errorcatalog.dao.ErrorCatalogDao;
import org.artifactory.storage.db.devopsagent.errorcatalog.dao.ErrorCatalogTemplateDao;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.RawEventInsertRequest;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.TemplateUpsertRequest;
import org.jfrog.common.ClockUtils;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Drains the per-JVM error/warning buffer and writes aggregated templates + raw events to the DB.
 *
 * <p>Per-node (NOT cluster-singleton) — each JVM holds its own in-memory buffer and is responsible
 * for flushing it. The templates table's UNIQUE constraint on {@code signature_hash} merges
 * concurrent UPSERTs from sibling nodes server-side.</p>
 */
@JobCommand(jobName = JobName.DEVOPS_AGENT_ERROR_CATALOG_FLUSH,
        singleton = true,
        clusterSingleton = false,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        description = "Flush in-memory error_catalog buffer to error_catalog_templates + error_catalog")
@Slf4j
public class ErrorCatalogFlushJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        ArtifactoryContext context = ContextHelper.get();
        if (context == null || !context.isReady()) {
            log.debug("Skipping {}, server is not ready yet", ErrorCatalogFlushJob.class.getSimpleName());
            return;
        }
        ErrorCatalogEventBuffer buffer = context.beanForType(ErrorCatalogEventBuffer.class);
        ErrorCatalogTemplateDao templatesDao = context.beanForType(ErrorCatalogTemplateDao.class);
        ErrorCatalogDao rawDao = context.beanForType(ErrorCatalogDao.class);
        SignatureToTemplateIdCache cache = context.beanForType(SignatureToTemplateIdCache.class);

        long flushStart = ClockUtils.epochMillis();
        ErrorCatalogEventBuffer.DrainResult drained = buffer.drain();
        if (drained.aggregated().isEmpty() && drained.rawEvents().isEmpty()) {
            return;
        }
        long nowMs = ClockUtils.epochMillis();
        try {
            upsertTemplates(templatesDao, drained.aggregated().values(), nowMs);
            Map<String, Long> signatureToId = resolveTemplateIds(templatesDao, cache, drained);
            insertRawEvents(rawDao, drained.rawEvents(), signatureToId);
            // Diagnostic log in lieu of metrics (feature is pre-GA): one line per non-empty flush
            // carrying the flush deltas plus a live snapshot of buffer/cache state and cumulative drops.
            log.debug("error_catalog flush ok: aggregated={}, raw={}, flush-ms={}, queue_depth={}, "
                            + "signatures={}, cache_hit_rate={}, cache_size={}, dropped_overflow={}, dropped_raw={}",
                    drained.aggregated().size(), drained.rawEvents().size(),
                    ClockUtils.epochMillis() - flushStart,
                    buffer.rawQueueDepth(), buffer.signatureCount(),
                    cache.hitRate(), cache.size(),
                    buffer.overflowDroppedCount(), buffer.rawDroppedCount());
        } catch (RuntimeException ex) {
            log.warn("error_catalog flush failed (aggregated={}, raw={}): {}",
                    drained.aggregated().size(), drained.rawEvents().size(), ex.toString());
            // Re-throw so Quartz records the failure; the next tick will reattempt the next batch.
            throw ex;
        }
    }

    private static void upsertTemplates(ErrorCatalogTemplateDao dao,
            java.util.Collection<ErrorCatalogEventBuffer.AggregatedEntry> entries, long nowMs) {
        List<TemplateUpsertRequest> reqs = new ArrayList<>(entries.size());
        for (ErrorCatalogEventBuffer.AggregatedEntry e : entries) {
            reqs.add(new TemplateUpsertRequest(
                    e.signatureHash(),
                    e.severity(),
                    e.loggerClass(),
                    e.exceptionClass(),
                    e.templateMessage(),
                    e.occurrenceCount(),
                    e.lastOccurredAtMs(),
                    nowMs));
        }
        dao.upsertBatch(reqs);
    }

    /**
     * Resolves the {@code signatureHash → templateId} map needed for raw INSERTs. Cache-first;
     * any cache misses (new signatures observed this window) are looked up in one bulk query.
     */
    private static Map<String, Long> resolveTemplateIds(ErrorCatalogTemplateDao templatesDao,
            SignatureToTemplateIdCache cache, ErrorCatalogEventBuffer.DrainResult drained) {
        Map<String, Long> out = new HashMap<>(drained.aggregated().size() * 2);
        List<String> misses = new ArrayList<>();
        for (String hash : drained.aggregated().keySet()) {
            Long cached = cache.getIfPresent(hash);
            if (cached != null) {
                out.put(hash, cached);
            } else {
                misses.add(hash);
            }
        }
        if (!misses.isEmpty()) {
            Map<String, Long> resolved = templatesDao.findIdsBySignatureHashes(misses);
            for (Map.Entry<String, Long> e : resolved.entrySet()) {
                out.put(e.getKey(), e.getValue());
                cache.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    private static void insertRawEvents(ErrorCatalogDao rawDao,
            List<ErrorCatalogEvent> rawEvents, Map<String, Long> signatureToId) {
        if (rawEvents.isEmpty()) {
            return;
        }
        List<RawEventInsertRequest> rows = new ArrayList<>(rawEvents.size());
        for (ErrorCatalogEvent ev : rawEvents) {
            Long templateId = signatureToId.get(ev.signatureHash());
            if (templateId == null) {
                // Should not happen — every raw event's signature was UPSERTed and resolved.
                continue;
            }
            rows.add(new RawEventInsertRequest(templateId, ev.occurredAtMs(), ev.rawMessage()));
        }
        rawDao.insertBatch(rows);
    }
}
