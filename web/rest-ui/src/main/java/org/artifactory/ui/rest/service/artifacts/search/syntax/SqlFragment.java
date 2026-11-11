package org.artifactory.ui.rest.service.artifacts.search.syntax;

import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Immutable holder for a parameterized SQL fragment used with {@link java.sql.PreparedStatement}.
 * <p>
 * Combines a SQL template string (with {@code ?} placeholders) and the corresponding bind parameters,
 * enabling safe construction of dynamic SQL for the syntax search feature without string concatenation.
 * Fragments can be composed via {@link #join(List, String)} and {@link #formatted(String, List)} and
 * then combined in a {@link PreparedQuery} to produce a full prepared statement template and parameter list.
 * <p>
 *
 * @param template SQL snippet with {@code ?} placeholders for bind parameters
 * @param params   ordered list of values to bind to the placeholders (never null)
 * @author Igor Usenko
 */
public record SqlFragment(String template, List<Object> params) {

    public static final SqlFragment EMPTY = SqlFragment.of("");

    public boolean isEmpty() {
        return isBlank(template()) && params.isEmpty();
    }

    public boolean isNotEmpty() {
        return !isEmpty();
    }

    /**
     * Creates a fragment with the given template and bind parameters.
     *
     * @param template SQL snippet with {@code ?} placeholders
     * @param params   values to bind (order must match placeholders)
     * @return new SqlFragment
     */
    public static SqlFragment of(String template, List<Object> params) {
        return new SqlFragment(template, params);
    }

    /**
     * Creates a fragment with the given SQL and no parameters (static SQL only).
     *
     * @param sql SQL string without placeholders
     * @return new SqlFragment
     */
    public static SqlFragment of(String sql) {
        return new SqlFragment(sql, List.of());
    }

    /**
     * Builds a fragment by repeating a placeholder part for each parameter, joined by the delimiter.
     * Useful for building clauses like {@code (?, ?, ?)} for IN lists.
     *
     * @param params    parameter list; size determines number of repetitions
     * @param part      string to repeat (e.g. {@code "?"})
     * @param delimiter string between repetitions (e.g. {@code ", "})
     * @return new SqlFragment, or {@link #EMPTY} if params is empty
     */
    public static SqlFragment join(List<Object> params, String part, String delimiter) {
        if (params.isEmpty()) {
            return SqlFragment.EMPTY;
        }

        List<SqlFragment> fragments = params.stream()
                .map(p -> new SqlFragment(part, List.of(p)))
                .toList();

        return join(fragments, delimiter);
    }

    /**
     * Concatenates multiple fragments into one: templates are joined with the delimiter,
     * and all parameters are collected in order. Used to build compound conditions
     * (e.g. {@code fragment1 + " and " + fragment2}).
     *
     * @param fragments list of fragments to join
     * @param delimiter separator between fragment templates (e.g. {@code " and "})
     * @return combined SqlFragment, or {@link #EMPTY} if fragments is empty
     */
    public static SqlFragment join(List<SqlFragment> fragments, String delimiter) {
        if (fragments.isEmpty()) {
            return SqlFragment.EMPTY;
        }
        String template = fragments.stream().map(f -> f.template).collect(Collectors.joining(delimiter));
        List<Object> params = fragments.stream().flatMap(f -> f.params().stream()).toList();

        return new SqlFragment(template, params);
    }

    /**
     * Builds a comma-separated list of {@code ?} placeholders with the given parameters.
     * Convenience for IN clauses: e.g. {@code "column IN (" + list(params) + ")"}.
     *
     * @param params values for the placeholders
     * @return fragment whose template is {@code "?, ?, ..."} with one {@code ?} per param
     */
    public static SqlFragment list(List<Object> params) {
        return SqlFragment.join(params, "?", ", ");
    }

    /**
     * Inserts fragment templates into a format string (e.g. {@code "and ((%s) or (%s))"})
     * and collects all fragment parameters in order. Uses {@link String#formatted(Object...)}.
     *
     * @param format    format string with {@code %s} for each fragment template
     * @param fragments fragments to substitute; their templates fill the {@code %s} slots
     * @return composed SqlFragment; {@link #EMPTY} if format is null; if fragments is null/empty,
     * returns a fragment with only the format string and no params
     */
    public static SqlFragment formatted(String format, List<SqlFragment> fragments) {
        if (format == null) {
            return SqlFragment.EMPTY;
        }
        if (fragments == null || fragments.isEmpty()) {
            return SqlFragment.of(format);
        }

        List<String> templates = fragments.stream().map(SqlFragment::template).toList();
        String template = format.formatted(templates.toArray(Object[]::new));

        List<Object> params = fragments.stream().flatMap(f -> f.params().stream()).toList();

        return new SqlFragment(template, params);
    }
}
