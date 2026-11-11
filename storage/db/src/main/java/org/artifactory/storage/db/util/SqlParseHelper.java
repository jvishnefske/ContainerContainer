package org.artifactory.storage.db.util;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * A helper class, providing utility functions for analyzing SQL queries, mostly to categorize them for metrics.
 * The main functions are:
 *
 * <ul>
 *     <li>{@link #removeSqlCommentsAndNormalizeSpaces(String)} - Normalizes SQL to simplify its further analysis</li>
 *     <li>{@link #getSqlStatementType(String)} - Detects the SQL statement type</li>
 *     <li>{@link #extractTableNames(String, String)} - Gets list of all tables that are mentioned in SQL query</li>
 * </ul>
 *
 * @author Sergey Steinvil
 */
@UtilityClass
public class SqlParseHelper {
    /**
     * Removes comments from SQL query, replaces line breaks with spaces, collapses multiple spaces to one,
     * removes leading and trailing spaces.
     * @param query - The SQL query to normalize
     * @return The normalized SQL query
     */
    @SuppressWarnings("java:S3776") // Ignore problem of the cognitive complexity 16
    public static String removeSqlCommentsAndNormalizeSpaces(String query) {
        if (query == null || query.isEmpty()) {
            return "";
        }

        char[] buffer = query.toCharArray();
        int lastSpace = -1;

        int i = 0;
        int count = 0;
        while (i < buffer.length) {
            char c = buffer[i++];
            if (c == '-' && i < buffer.length && buffer[i] == '-') {
                i = skipSingleLineComment(buffer, i + 1);
            } else if (c == '/' && i < buffer.length && buffer[i] == '*') {
                i = skipMultilineComment(buffer, i + 1);
            } else if (c == '\'' || c == '"') {
                buffer[count++] = c;
                int length = copyLiteral(buffer, i, count, c);
                i += length;
                count += length;
            } else if (Character.isWhitespace(c)) {
                if (lastSpace != count - 1) {
                    lastSpace = count;
                    buffer[count++] = ' ';
                }
            } else {
                buffer[count++] = c;
            }
        }

        // Remove trailing space (if any)
        if (lastSpace == count - 1) {
            count--;
        }

        return new String(buffer, 0, count);
    }

    /**
     * Extracts the first token from the SQL query and converts it to the upper case.
     * Supposed to be SELECT, INSERT, UPDATE or DELETE.
     * @param normalizedQuery - The SQL query text to analyze (after removeSqlCommentsAndNormalizeSpaces)
     * @return The text before the first space, converted to the upper case.
     */
    public static String getSqlStatementType(String normalizedQuery) {
        return StringUtils.substringBefore(normalizedQuery, " ").toUpperCase();
    }

    /**
     * Scans SQL query and extracts names of all tables that are participated in this query
     * @param normalizedQuery - The SQL query text to analyze (after removeSqlCommentsAndNormalizeSpaces)
     * @param queryType - The SQL statement type ("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", etc.)
     * @return The set of table names
     */
    public static Set<String> extractTableNames(String normalizedQuery, String queryType) {
        return switch (queryType) {
            case "SELECT" -> extractTableNamesFromSelectQuery(normalizedQuery);
            case "INSERT" -> extractTableNamesAfter(normalizedQuery, " INTO ");
            case "UPDATE" -> extractTableNamesAfter(normalizedQuery, "UPDATE ");
            case "DELETE" -> extractTableNamesAfter(normalizedQuery, " FROM ");
            case "TRUNCATE" -> extractTableNamesAfter(normalizedQuery, " TABLE ");
            default -> Set.of();
        };
    }

    public static Set<String> extractTableNamesFromSelectQuery(String normalizedQuery) {
        Set<String> tableNames = new HashSet<>();
        int from = 0;
        while (from < normalizedQuery.length()) {
            int start = findNextTableNameOffset(normalizedQuery, from);
            if (start < 0) {
                break;
            }

            if (start < normalizedQuery.length() && Character.isAlphabetic(normalizedQuery.charAt(start))) {
                int end = normalizedQuery.indexOf(' ', start);
                if (end < 0) {
                    tableNames.add(normalizedQuery.substring(start));
                    from = normalizedQuery.length(); // Last token
                } else {
                    tableNames.add(normalizedQuery.substring(start, end));
                    from = end + 1;
                }
            } else {
                from = start;
            }
        }
        return tableNames;
    }

    private static int findNextTableNameOffset(String normalizedQuery, int from) {
        int start = StringUtils.indexOfIgnoreCase(normalizedQuery, " FROM ", from);
        if (start < 0) {
            start = StringUtils.indexOfIgnoreCase(normalizedQuery, " JOIN ", from);
            if (start < 0) {
                return start;
            }
        } else {
            int joinStart = StringUtils.indexOfIgnoreCase(normalizedQuery.subSequence(from, start), " JOIN ");
            if (joinStart >= 0) {
                start = from + joinStart;
            }
        }
        return start + 6; // skip JOIN / FROM
    }

    private static Set<String> extractTableNamesAfter(String normalizedQuery, String afterText) {
        String tableName = extractTableNameAfter(normalizedQuery, afterText, null);
        return tableName == null ? Set.of() : Set.of(tableName);
    }

    public static String extractTableNameAfter(String normalizedQuery, String afterText, String defaultValue) {
        int start = StringUtils.indexOfIgnoreCase(normalizedQuery, afterText);
        if (start < 0) {
            return defaultValue;
        }
        start += afterText.length();

        int end = start;
        while (end < normalizedQuery.length()) {
            char symbol = normalizedQuery.charAt(end);
            if (!Character.isLetterOrDigit(symbol) && symbol != '_' && symbol != '$') {
                break;
            }
            end++;
        }
        return normalizedQuery.substring(start, end);
    }

    private static int skipSingleLineComment(char[] buffer, int i) {
        while (i < buffer.length) {
            if (buffer[i] == '\n') {
                return i + 1;
            }
            i++;
        }
        return i;
    }

    private static int skipMultilineComment(char[] buffer, int i) {
        while (i < buffer.length) {
            if (buffer[i] == '*' && i + 1 < buffer.length && buffer[i + 1] == '/') {
                return i + 2;
            }
            i++;
        }
        return i; // Comment is unclosed. Normally, we should not get here
    }

    private static int copyLiteral(char[] buffer, int i, int target, char delimiter) {
        int length = 0;
        while (i < buffer.length) {
            length++;
            buffer[target++] = buffer[i];
            if (buffer[i] == delimiter) {
                break;
            }
            i++;
        }
        return length;
    }
}
