package org.artifactory.storage.db.devopsagent.errorcatalog.model;

import java.util.List;

/**
 * Validated, parsed criteria used by the agentic REST surface to search the templates table.
 * Empty / null lists or null bounds mean "no constraint" on that dimension.
 *
 * @param ids                  optional IN-filter on {@code id}
 * @param severities           optional IN-filter on {@code severity}
 * @param loggerClasses        optional IN-filter on {@code logger_class}
 * @param exceptionClasses     optional IN-filter on {@code exception_class}
 * @param occurrenceCountFrom  inclusive lower bound on {@code occurrence_count}
 * @param occurrenceCountTo    inclusive upper bound on {@code occurrence_count}
 * @param firstOccurrenceFrom  inclusive lower bound on {@code first_occurrence_at}
 * @param firstOccurrenceTo    inclusive upper bound on {@code first_occurrence_at}
 * @param lastOccurrenceFrom   inclusive lower bound on {@code last_occurrence_at}
 * @param lastOccurrenceTo     inclusive upper bound on {@code last_occurrence_at}
 * @param tsqueryString        pre-composed argument for PG {@code to_tsquery('english', ?)}; null means no text search
 */
public record AgenticSearchCriteria(
        List<Long> ids,
        List<String> severities,
        List<String> loggerClasses,
        List<String> exceptionClasses,
        Long occurrenceCountFrom,
        Long occurrenceCountTo,
        Long firstOccurrenceFrom,
        Long firstOccurrenceTo,
        Long lastOccurrenceFrom,
        Long lastOccurrenceTo,
        String tsqueryString) {
}
