package org.artifactory.ui.rest.service.artifacts.search.syntax;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builder for a parameterized SQL query used by the UI syntax search.
 * <p>
 * Collects SQL fragments and their bind parameters so that a single {@code PreparedStatement} can be built with
 * placeholders ({@code ?}) and bound values in the correct order. This avoids SQL injection and allows the database
 * to cache execution plans.
 * <p>
 *
 * @author Igor Usenko
 */
public class PreparedQuery {

    private final List<SqlFragment> fragments = new ArrayList<>();

    /**
     * Appends a literal SQL fragment with no bind parameters.
     *
     * @param fragment SQL text to append (e.g. {@code "select ..."}, {@code "where"}, {@code "order by ..."})
     */
    public void add(String fragment) {
        fragments.add(SqlFragment.of(fragment));
    }

    /**
     * Appends a parameterized SQL fragment. Empty fragments are ignored and not added.
     *
     * @param fragment fragment containing template (with {@code ?} placeholders) and corresponding params
     */
    public void add(SqlFragment fragment) {
        if (!fragment.isEmpty()) {
            fragments.add(fragment);
        }
    }

    public void addAll(List<SqlFragment> fragments) {
        fragments.forEach(this::add);
    }

    /**
     * Returns the full SQL template by joining all fragment templates with {@code \r\n}.
     * Placeholders ({@code ?}) appear in the order of parameters returned by {@link #getParams()}.
     *
     * @return the complete SQL string with placeholders, never null
     */
    public String getTemplate() {
        return fragments.stream().map(SqlFragment::template).collect(Collectors.joining("\r\n"));
    }

    /**
     * Returns the ordered list of bind parameters for this query, in the same order as the placeholders
     * in the template from {@link #getTemplate()}. Parameters from each fragment are included in sequence.
     *
     * @return list of parameter values to bind to the prepared statement
     */
    public List<Object> getParams() {
        return fragments.stream().flatMap(f -> f.params().stream()).toList();
    }

}
