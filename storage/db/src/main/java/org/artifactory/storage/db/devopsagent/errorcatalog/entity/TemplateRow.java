package org.artifactory.storage.db.devopsagent.errorcatalog.entity;

public record TemplateRow(
        long id,
        String signatureHash,
        String severity,
        String loggerClass,
        String exceptionClass,
        String templateMessage,
        long firstOccurrenceAt,
        long lastOccurrenceAt,
        long occurrenceCount,
        long createdAt,
        long updatedAt) {
}
