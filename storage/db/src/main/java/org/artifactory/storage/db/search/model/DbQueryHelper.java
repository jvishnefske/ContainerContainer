package org.artifactory.storage.db.search.model;

import lombok.NoArgsConstructor;

@NoArgsConstructor
abstract class DbQueryHelper {
    static final String FORWARD_SLASH = "/";
    static final char SLASH_CHAR = '/';
    static final char STAR = '*';
    static final char QUESTION_MARK = '?';
    static final char PERCENT = '%';
    static final char UNDERSCORE = '_';

    public static int addPathValue(StringBuilder builder, String pathValue) {
        return addPathValue(builder, pathValue, "");
    }

    public static int addPathValue(StringBuilder builder, String pathValue, String escapeSymbol) {
        if (!builder.isEmpty()) {
            addCharIfNeeded(builder, SLASH_CHAR);
        }
        return appendWithWildcards(builder, pathValue, escapeSymbol);
    }

    public static String convertWildcards(String value) {
        return convertWildcards(value, "");
    }

    public static String convertWildcards(String value, String escapeSymbol) {
        StringBuilder result = new StringBuilder();
        appendWithWildcards(result, value, escapeSymbol);
        return result.toString();
    }

    public static int appendWithWildcards(StringBuilder builder, String value) {
        return appendWithWildcards(builder, value, "");
    }

    public static int appendWithWildcards(StringBuilder builder, String value, String escapeSymbol) {
        int nbSlash = 0;
        for (char c : value.toCharArray()) {
            if (c == STAR) {
                addCharIfNeeded(builder, PERCENT, escapeSymbol);
            } else if (c == QUESTION_MARK) {
                builder.append(UNDERSCORE);
            } else {
                if (c == SLASH_CHAR) {
                    nbSlash++;
                }
                builder.append(c);
            }
        }
        return nbSlash;
    }

    private static void addCharIfNeeded(StringBuilder builder, char toAdd, String escapeSymbol) {
        char lastChar = 0;
        char secondLastChar = 0;
        int length = builder.length();
        if (length > 0) {
            lastChar = builder.charAt(length - 1);
        }
        if (length > 1) {
            secondLastChar = builder.charAt(length - 2);
        }
        if (lastChar != toAdd || isEscape(escapeSymbol, secondLastChar)) {
            builder.append(toAdd);
        }
    }

    private static boolean isEscape(String escapeSymbol, char secondLastChar) {
        return !escapeSymbol.isEmpty() && secondLastChar == escapeSymbol.charAt(0);
    }

    private static void addCharIfNeeded(StringBuilder builder, char toAdd) {
        char lastChar = 0;
        if (!builder.isEmpty()) {
            lastChar = builder.charAt(builder.length() - 1);
        }
        if (lastChar != toAdd) {
            builder.append(toAdd);
        }
    }

    static boolean hasWildcards(String value) {
        for (char c : value.toCharArray()) {
            if (c == STAR || c == QUESTION_MARK || c == PERCENT || c == UNDERSCORE) {
                return true;
            }
        }
        return false;
    }
}