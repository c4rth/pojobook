package org.pojobook.generator;

import java.util.Locale;

/**
 * Utility class for name transformations.
 */
public final class NamingUtils {

    private NamingUtils() {
        // Prevent instantiation
    }

    /**
     * Converts a COBOL name to camelCase.
     */
    public static String toCamelCase(String name) {
        return transformName(name, false);
    }

    /**
     * Converts a COBOL name to PascalCase.
     */
    public static String toPascalCase(String name) {
        return transformName(name, true);
    }

    /**
     * Generic name transformation.
     */
    private static String transformName(String name, boolean capitalizeFirst) {
        String normalized = stripLeadingDelimiters(name);
        if (normalized.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder(normalized.length());
        StringBuilder token = new StringBuilder();
        boolean firstToken = true;

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '-' || c == '_') {
                if (!token.isEmpty()) {
                    appendToken(result, token, firstToken && !capitalizeFirst);
                    firstToken = false;
                    token.setLength(0);
                }
            } else {
                token.append(c);
            }
        }

        if (!token.isEmpty()) {
            appendToken(result, token, firstToken && !capitalizeFirst);
        }

        return result.toString();
    }

    private static void appendToken(StringBuilder result, StringBuilder token, boolean lowercaseOnly) {
        String part = token.toString().toLowerCase(Locale.ROOT);
        if (lowercaseOnly) {
            result.append(part);
        } else {
            result.append(capitalizeFirst(part));
        }
    }

    /**
     * Removes leading delimiters.
     */
    private static String stripLeadingDelimiters(String name) {
        int start = 0;
        while (start < name.length() && (name.charAt(start) == '-' || name.charAt(start) == '_')) {
            start++;
        }
        return name.substring(start);
    }

    /**
     * Capitalizes first character.
     */
    private static String capitalizeFirst(String str) {
        return str.isEmpty()
                ? str
                : Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
