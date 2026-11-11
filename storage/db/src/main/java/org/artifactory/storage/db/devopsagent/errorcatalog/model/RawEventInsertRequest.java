package org.artifactory.storage.db.devopsagent.errorcatalog.model;

public record RawEventInsertRequest(
        long templateId,
        long occurredAt,
        String rawMessage) {
}
