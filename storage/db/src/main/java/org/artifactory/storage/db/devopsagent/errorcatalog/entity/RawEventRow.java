package org.artifactory.storage.db.devopsagent.errorcatalog.entity;

public record RawEventRow(
        long id,
        long templateId,
        long occurredAt,
        String rawMessage) {
}
