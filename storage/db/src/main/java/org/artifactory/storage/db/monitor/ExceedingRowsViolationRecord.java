package org.artifactory.storage.db.monitor;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

/**
 * Represents a single exceeding-rows violation.
 *
 * @author Eli Fishman
 */
@Builder
@Jacksonized
public record ExceedingRowsViolationRecord(
        String query,
        long exceedingRowsCount,
        long threshold,
        String actionName,
        String actionKind,
        String actionGroup,
        String actionPackage,
        String actionActor,
        String hint,
        boolean streaming,
        String routeKey) {
}
