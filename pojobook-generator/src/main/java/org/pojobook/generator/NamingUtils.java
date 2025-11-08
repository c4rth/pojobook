package org.pojobook.generator;

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
        String[] parts = stripLeadingDelimiters(name).split("[-_]");
        if (parts.length == 0) return "";

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;

            if (i == 0 && !capitalizeFirst) {
                result.append(part.toLowerCase());
            } else {
                result.append(capitalizeFirst(part.toLowerCase()));
            }
        }
        return result.toString();
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
