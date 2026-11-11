package org.artifactory.storage.db.devopsagent.errorcatalog.model;

public record TemplateUpsertRequest(
        String signatureHash,
        String severity,
        String loggerClass,
        String exceptionClass,
        String templateMessage,
        long occurrenceCountDelta,
        long latestOccurrenceAt,
        long nowEpochMs) {
}
