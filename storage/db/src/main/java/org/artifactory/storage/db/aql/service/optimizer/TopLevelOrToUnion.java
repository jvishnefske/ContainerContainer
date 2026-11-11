package org.artifactory.storage.db.aql.service.optimizer;

import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQueryElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.CloseParenthesisAqlElement;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Tomer Slutsky
 */
public class TopLevelOrToUnion extends OptimizationStrategy {
    private static final Pattern pattern = Pattern.compile("\\(+[pkv]([^)]+)\\)+");
    private static final Pattern patternPrefix = Pattern.compile("^\\(+[KV]([^)]*)\\)+");
    private final List<Pair<Integer, Integer>> indices = new ArrayList<>();
    private int openParenthesisNum = -1;
    private int unionStartIndex = -1;
    private boolean foundFirst = false;

    @Override
    public <T extends AqlRowResult> void optimize(AqlQuery<T> aqlQuery, String transformation) {
        if (shouldSkipOptimization(aqlQuery)) {
            return;
        }
        // Try to find matching sub strings
        Matcher matcher = pattern.matcher(transformation);
        Matcher matcherPrefix = patternPrefix.matcher(transformation);
        List<AqlQueryElement> staticElems = List.of();
        if (matcher.find()) {
            staticElems = initStaticElems(aqlQuery, matcher, matcherPrefix);
        } else {
            resetDataMembers();
        }

        matcher.reset(transformation);
        // Each match represent sub query that can be optimized
        while (matcher.find()) {
            String group = matcher.group();
            int end = matcher.end();
            if (shouldCreateSubQuery(transformation, group, end)) {
                createSubQuery(aqlQuery, staticElems, end);
            }
        }

        cleanupElements(aqlQuery);
    }

    private <T extends AqlRowResult> void createSubQuery(AqlQuery<T> aqlQuery, List<AqlQueryElement> staticElems,
            int end) {
        List<AqlQueryElement> elems = new ArrayList<>(staticElems);

        // The first time we want to ignore the first opening parenthesis since we use it in the original query
        // after we create the sub lists
        if (foundFirst) {
            foundFirst = false;
            createSublist(aqlQuery, unionStartIndex + 1, end, elems);
        } else {
            createSublist(aqlQuery, unionStartIndex, end, elems);
        }

        // Only add a close parenthesis if we found a prefix, since the prefix adds an open parenthesis
        if (!staticElems.isEmpty()) {
            elems.add(new CloseParenthesisAqlElement());
        }

        // Advance the start index to the next element since we will cut the elements until the end index
        // in the original list
        unionStartIndex = end + 1;
        aqlQuery.putSubQuery(elems);
    }

    @Nonnull
    private <T extends AqlRowResult> List<AqlQueryElement> initStaticElems(AqlQuery<T> aqlQuery, Matcher matcher,
            Matcher matcherPrefix) {
        List<AqlQueryElement> staticElems;
        // Get the query include part, including the operator and the first parenthesis
        staticElems = getStaticQueryElems(aqlQuery, matcherPrefix, matcherPrefix.find());

        // The first match should find the opening element after the 'include' part
        // meaning it has the max possible open parenthesis
        String group = matcher.group();
        foundFirst = true;
        unionStartIndex = matcher.start();
        openParenthesisNum = StringUtils.countOccurrencesOf(group, "(");
        return staticElems;
    }

    private void resetDataMembers() {
        openParenthesisNum = -1;
        unionStartIndex = -1;
        foundFirst = false;
    }


    @Nonnull
    private <T extends AqlRowResult> List<AqlQueryElement> getStaticQueryElems(AqlQuery<T> aqlQuery,
            Matcher matcherPrefix, boolean prefixExists) {
        return prefixExists ? new ArrayList<>(aqlQuery.getAqlElements()
                .subList(matcherPrefix.start(), matcherPrefix.end() + 2)) : List.of();
    }

    private boolean shouldCreateSubQuery(String transformation, String group, int end) {
        return openParenthesisNum - StringUtils.countOccurrencesOf(group, ")") == 1 &&
                transformation.charAt(end) == 'o';
    }

    private <T extends AqlRowResult> void createSublist(AqlQuery<T> aqlQuery, int start, int end,
            List<AqlQueryElement> elems) {
        elems.addAll(aqlQuery.getAqlElements().subList(start, end));
        indices.add(0, Pair.of(start, end + 1));
    }

    private <T extends AqlRowResult> void cleanupElements(AqlQuery<T> aqlQuery) {
        for (Pair<Integer, Integer> subList : indices) {
            aqlQuery.getAqlElements().subList(subList.getLeft(), subList.getRight()).clear();
        }
        indices.clear();
    }


}
