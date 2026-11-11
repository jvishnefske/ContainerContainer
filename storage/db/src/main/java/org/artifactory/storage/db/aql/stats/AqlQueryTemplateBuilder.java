package org.artifactory.storage.db.aql.stats;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.artifactory.aql.model.AqlQueryTemplate;
import org.artifactory.aql.model.AqlValue;
import org.artifactory.aql.model.AqlVariableTypeEnum;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQueryElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.Criterion;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a normalized template + checksum from a parsed {@link AqlQuery}.
 *
 * <p>Strategy: temporarily swap each {@link AqlValue}'s literal with the wildcard-aware
 * normalized form, call {@link AqlQuery#toNative()} (bit-exact canonical form with whitespace
 * already collapsed), then restore the originals in a finally block. Checksum mirrors
 * {@code DbQueryMonitorImpl.databaseClusterHash}: {@code Integer.toHexString(hashCode())}.
 *
 * @author Amir Avidan
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AqlQueryTemplateBuilder {

    static final char LITERAL_PLACEHOLDER = '_';
    static final String NUMERIC_PLACEHOLDER = "0";
    static final String ITEM_TYPE_PLACEHOLDER = "any";

    /**
     * <p><b>Aliasing precondition:</b> {@code aqlQuery} must not be shared with another thread
     * or read by SQL generation during this call -- the {@link AqlValue} instances are mutated
     * in place and restored in a {@code finally} block. Build a fresh, request-private query
     * for templating.
     *
     * @return template + checksum, or {@code null} when {@code aqlQuery} is {@code null}.
     */
    public static AqlQueryTemplate build(AqlQuery<? extends AqlRowResult> aqlQuery) {
        if (aqlQuery == null) {
            return null;
        }
        List<ValueSnapshot> snapshots = swapValuesToTemplate(aqlQuery);
        try {
            String template = aqlQuery.toNative();
            String checksum = checksum(template);
            return new AqlQueryTemplate(template, checksum);
        } finally {
            restoreValues(snapshots);
        }
    }

    /**
     * Collapse runs of non-wildcard chars to a single {@code _}, preserving {@code *} and
     * {@code ?} in place. Empty/null pass through unchanged.
     *
     * <p>Examples: {@code pom.xml -> _}, {@code *pom.xml -> *_}, {@code *pom.*xml -> *_*_}.
     */
    static String normalizeLiteral(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        StringBuilder out = new StringBuilder(value.length());
        boolean inLiteralRun = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '*' || c == '?') {
                out.append(c);
                inLiteralRun = false;
            } else if (!inLiteralRun) {
                out.append(LITERAL_PLACEHOLDER);
                inLiteralRun = true;
            }
        }
        return out.toString();
    }

    static String checksum(String template) {
        return Integer.toHexString(template.hashCode());
    }

    /**
     * Walks the parsed query's WHERE clauses and replaces each literal with a type-appropriate
     * placeholder. A {@link Criterion} is the AQL parser's representation of a single
     * {@code field op value} predicate -- {@code getVariable2()} returns the right-hand side
     * (the literal value); we only touch criteria whose RHS is an {@link AqlValue} (i.e. an
     * actual literal, not another field reference). Originals are captured per call so
     * {@link #restoreValues(List)} can put them back in the {@code finally} block.
     */
    private static List<ValueSnapshot> swapValuesToTemplate(AqlQuery<? extends AqlRowResult> aqlQuery) {
        List<ValueSnapshot> snapshots = new ArrayList<>();
        for (AqlQueryElement element : aqlQuery.getElementsForNative()) {
            if (element instanceof Criterion criterion && criterion.getVariable2() instanceof AqlValue aqlValue) {
                String original = aqlValue.getValue();
                snapshots.add(new ValueSnapshot(aqlValue, original));
                aqlValue.setValue(placeholderFor(aqlValue.getValueType(), original));
            }
        }
        return snapshots;
    }

    /**
     * Pick a placeholder that (a) survives {@link AqlValue#toNative()}'s strict type validation
     * and (b) is constant across requests so equivalent queries collapse to the same template.
     * Only {@code string} keeps wildcard structure -- numeric/date/itemType values are not
     * wildcardable in AQL syntax.
     */
    private static String placeholderFor(AqlVariableTypeEnum type, String original) {
        if (original == null) {
            return null;
        }
        return switch (type) {
            case string -> normalizeLiteral(original);
            case integer, longInt, date -> NUMERIC_PLACEHOLDER;
            case itemType -> ITEM_TYPE_PLACEHOLDER;
        };
    }

    private static void restoreValues(List<ValueSnapshot> snapshots) {
        for (ValueSnapshot snapshot : snapshots) {
            snapshot.value.setValue(snapshot.original);
        }
    }

    private record ValueSnapshot(AqlValue value, String original) {
    }
}
