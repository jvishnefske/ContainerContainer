package org.artifactory.devopsagent.errorcatalog;

/**
 * An ERROR/WARN log event captured by the appender and ready for the producer pipeline.
 *
 * <p>{@code templateMessage} is the deduplication key: SLF4J's parameterized pattern when present,
 * otherwise the regex-normalized form of the formatted message. {@code rawMessage} is the
 * sanitized formatted text. {@code signatureHash} is the SHA-256 dedup key over
 * {@code (severity | loggerClass | exceptionClass | templateMessage)}.
 */
public record ErrorCatalogEvent(
        String severity,
        String loggerClass,
        String exceptionClass,
        String templateMessage,
        String rawMessage,
        String signatureHash,
        long occurredAtMs) {
}
