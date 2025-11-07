package org.c4rth.pojobook.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enhanced parser for COBOL copybook definitions supporting REDEFINES, OCCURS variations,
 * and comprehensive COBOL language features.
 */
public class CopybookParser {

    // Pattern for field declarations with or without PICTURE
    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "^\\s*(\\d{2})\\s+([A-Z0-9\\-]+|FILLER).*$",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern for PICTURE clause anywhere in the line
    private static final Pattern PICTURE_PATTERN = Pattern.compile(
            "(?:PIC|PICTURE)\\s+([A-Z0-9\\(\\)V\\-\\+S]+)",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern for REDEFINES clause
    private static final Pattern REDEFINES_PATTERN = Pattern.compile(
            "REDEFINES\\s+([A-Z0-9\\-]+)",
            Pattern.CASE_INSENSITIVE
    );

    // Enhanced OCCURS pattern supporting all variations
    private static final Pattern OCCURS_PATTERN = Pattern.compile(
            "OCCURS\\s+(\\d+)(?:\\s+TO\\s+(\\d+))?(?:\\s+TIMES)?(?:\\s+DEPENDING\\s+ON\\s+([A-Z0-9\\-]+))?",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern for VALUE clause
    private static final Pattern VALUE_PATTERN = Pattern.compile(
            "VALUE\\s+(?:IS\\s+)?(['\"][^'\"]*['\"]|[A-Z0-9\\-]+)",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern for USAGE clause
    private static final Pattern USAGE_PATTERN = Pattern.compile(
            "(?:USAGE\\s+(?:IS\\s+)?)?(COMP(?:-[1-5])?|COMPUTATIONAL(?:-[1-5])?|PACKED-DECIMAL|BINARY|DISPLAY)",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern for SYNC/SYNCHRONIZED
    private static final Pattern SYNC_PATTERN = Pattern.compile(
            "SYNC(?:HRONIZED)?(?:\\s+(LEFT|RIGHT))?",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern for INDEXED BY
    private static final Pattern INDEXED_BY_PATTERN = Pattern.compile(
            "INDEXED\\s+BY\\s+([A-Z0-9\\-]+(?:\\s+[A-Z0-9\\-]+)*)",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern for ASCENDING/DESCENDING KEY
    private static final Pattern KEY_PATTERN = Pattern.compile(
            "(ASCENDING|DESCENDING)\\s+KEY\\s+(?:IS\\s+)?([A-Z0-9\\-]+(?:\\s+[A-Z0-9\\-]+)*)",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern for JUSTIFIED RIGHT
    private static final Pattern JUSTIFIED_PATTERN = Pattern.compile(
            "JUST(?:IFIED)?(?:\\s+RIGHT)?",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern for BLANK WHEN ZERO
    private static final Pattern BLANK_WHEN_ZERO_PATTERN = Pattern.compile(
            "BLANK\\s+WHEN\\s+ZERO",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern for SIGN clause
    private static final Pattern SIGN_PATTERN = Pattern.compile(
            "SIGN\\s+(?:IS\\s+)?(LEADING|TRAILING)(?:\\s+SEPARATE(?:\\s+CHARACTER)?)?",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern for 88-level condition names
    private static final Pattern CONDITION_LEVEL_PATTERN = Pattern.compile(
            "^\\s*88\\s+([A-Z0-9\\-]+)\\s+VALUES?\\s+(.+)$",
            Pattern.CASE_INSENSITIVE
    );

    public CopybookParser() {

    }

    /**
     * Parse a copybook from a file.
     */
    public CopybookDefinition parse(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return parse(reader);
        }
    }

    /**
     * Parse a copybook from a Reader.
     */
    public CopybookDefinition parse(Reader reader) throws IOException {
        List<FieldDefinition> fields = new ArrayList<>();
        List<String> lines = new ArrayList<>();

        // Read all lines first
        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        }

        // Parse lines, handling multi-line continuations
        int lineNumber = 0;
        StringBuilder currentLine = new StringBuilder();
        int currentLineNumber = 0;

        for (String line : lines) {
            lineNumber++;

            // Skip empty lines and comments
            if (line.trim().isEmpty() || line.trim().startsWith("*")) {
                continue;
            }

            // Check for continuation (- in column 7 OR line doesn't start with a level number)
            boolean isContinuation = line.length() > 7 && line.charAt(6) == '-';

            // Remove sequence numbers (columns 1-6) if present
            String processedLine = line.length() > 6 ? line.substring(6) : line;

            // Check if this line starts with a level number (implicit continuation if not)
            if (!isContinuation && !currentLine.isEmpty()) {
                String trimmed = processedLine.trim();
                // If line doesn't start with level number (digits), it's a continuation
                if (!trimmed.isEmpty() && !Character.isDigit(trimmed.charAt(0))) {
                    isContinuation = true;
                }
            }

            if (isContinuation) {
                // Continuation of previous line
                String continuation = processedLine;
                if (!processedLine.isEmpty() && processedLine.charAt(0) == '-') {
                    continuation = processedLine.substring(1);
                }
                currentLine.append(" ").append(continuation.trim());
            } else {
                // Process previous accumulated line
                if (!currentLine.isEmpty()) {
                    FieldDefinition field = parseLine(currentLine.toString(), currentLineNumber);
                    if (field != null) {
                        fields.add(field);
                    }
                }
                // Start new line
                currentLine = new StringBuilder(processedLine);
                currentLineNumber = lineNumber;
            }
        }

        // Process last line
        if (!currentLine.isEmpty()) {
            FieldDefinition field = parseLine(currentLine.toString(), currentLineNumber);
            if (field != null) {
                fields.add(field);
            }
        }

        // Link 88-level condition names to their parent fields
        linkConditionNames(fields);

        // Calculate offsets for fields
        calculateOffsets(fields);

        return new CopybookDefinition(fields);
    }

    /**
     * Link 88-level condition names to their parent fields.
     * An 88-level immediately follows its parent field.
     */
    private void linkConditionNames(List<FieldDefinition> fields) {
        FieldDefinition currentParent = null;

        for (FieldDefinition field : fields) {
            if (field.getLevel() == 88) {
                // This is a condition name - link it to the most recent non-88 field
                if (currentParent != null && !currentParent.getName().startsWith("FILLER")) {
                    // Get condition name from field name and value
                    ConditionName condition = new ConditionName();
                    condition.setName(field.getName());
                    condition.setParentFieldName(currentParent.getName());
                    condition.setLineNumber(field.getLineNumber());

                    // Parse values from the field's value attribute
                    if (field.getValue() != null && !field.getValue().isEmpty()) {
                        condition.setValues(parseConditionValues(field.getValue()));
                    }

                    currentParent.addConditionName(condition);
                }
            } else {
                // Regular field - could be parent for next 88-level
                currentParent = field;
            }
        }
    }

    /**
     * Parse multiple values from VALUE/VALUES clause.
     * Examples: 'AC', 'AC' 'IN' 'PE', "AC", etc.
     */
    private String[] parseConditionValues(String valueClause) {
        List<String> values = new ArrayList<>();

        // Match quoted strings: 'value' or "value"
        Pattern quotedPattern = Pattern.compile("(['\"])([^'\"]*?)\\1");
        Matcher matcher = quotedPattern.matcher(valueClause);

        while (matcher.find()) {
            values.add(matcher.group(2));
        }

        // If no quoted values found, treat the whole thing as one value
        if (values.isEmpty() && !valueClause.isEmpty()) {
            values.add(valueClause.trim());
        }

        return values.toArray(new String[0]);
    }

    /**
     * Parse a single line of copybook definition with full COBOL language support.
     */
    private FieldDefinition parseLine(String line, int lineNumber) {
        // Check for 88-level condition first
        Matcher conditionMatcher = CONDITION_LEVEL_PATTERN.matcher(line);
        if (conditionMatcher.find()) {
            // This is an 88-level condition name
            return getFieldDefinition(lineNumber, conditionMatcher);
        }

        // Regular field (non-88-level)
        Matcher matcher = FIELD_PATTERN.matcher(line);
        if (!matcher.find()) {
            return null;
        }

        int level = Integer.parseInt(matcher.group(1));
        String name = matcher.group(2);

        FieldDefinition field = new FieldDefinition();
        field.setLevel(level);
        field.setLineNumber(lineNumber);

        // Check for FILLER
        if ("FILLER".equalsIgnoreCase(name)) {
            field.setFiller(true);
            field.setName("FILLER-" + lineNumber);
        } else {
            field.setName(name);
        }

        // Look for PICTURE clause anywhere in the line
        Matcher pictureMatcher = PICTURE_PATTERN.matcher(line);
        if (pictureMatcher.find()) {
            String picture = pictureMatcher.group(1);
            if (picture != null && !picture.isEmpty()) {
                field.setPicture(picture);
            }
        }

        // Parse REDEFINES clause
        Matcher redefinesMatcher = REDEFINES_PATTERN.matcher(line);
        if (redefinesMatcher.find()) {
            field.setRedefines(redefinesMatcher.group(1));
        }

        // Parse OCCURS clause (all variations)
        Matcher occursMatcher = OCCURS_PATTERN.matcher(line);
        if (occursMatcher.find()) {
            String minOccurs = occursMatcher.group(1);
            String maxOccurs = occursMatcher.group(2);
            String dependingOn = occursMatcher.group(3);

            if (maxOccurs != null) {
                // OCCURS min TO max DEPENDING ON
                field.setMinOccurs(Integer.parseInt(minOccurs));
                field.setMaxOccurs(Integer.parseInt(maxOccurs));
                field.setOccurs(Integer.parseInt(maxOccurs)); // Default to max
            } else {
                // OCCURS n or OCCURS n DEPENDING ON
                field.setOccurs(Integer.parseInt(minOccurs));
            }

            if (dependingOn != null) {
                field.setDependingOn(dependingOn);
            }
        }

        // Parse USAGE/data type
        Matcher usageMatcher = USAGE_PATTERN.matcher(line);
        if (usageMatcher.find()) {
            field.setTypeFromString(usageMatcher.group(1));
        }

        // Parse VALUE clause
        Matcher valueMatcher = VALUE_PATTERN.matcher(line);
        if (valueMatcher.find()) {
            String value = valueMatcher.group(1);
            // Remove quotes if present
            if (value.startsWith("'") || value.startsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            field.setValue(value);
        }

        // Parse SYNC/SYNCHRONIZED
        Matcher syncMatcher = SYNC_PATTERN.matcher(line);
        if (syncMatcher.find()) {
            String syncType = syncMatcher.group(1);
            field.setSync(syncType != null ? syncType : "");
        }

        // Parse INDEXED BY
        Matcher indexedByMatcher = INDEXED_BY_PATTERN.matcher(line);
        if (indexedByMatcher.find()) {
            String[] indexes = indexedByMatcher.group(1).trim().split("\\s+");
            field.setIndexedBy(indexes);
        }

        // Parse ASCENDING/DESCENDING KEY
        Matcher keyMatcher = KEY_PATTERN.matcher(line);
        if (keyMatcher.find()) {
            String keyType = keyMatcher.group(1);
            String[] keys = keyMatcher.group(2).trim().split("\\s+");
            field.setKeys(keys);
            if ("ASCENDING".equalsIgnoreCase(keyType)) {
                field.setAscendingKey(true);
            } else {
                field.setDescendingKey(true);
            }
        }

        // Parse SIGN clause (SIGN IS LEADING/TRAILING [SEPARATE CHARACTER])
        Matcher signMatcher = SIGN_PATTERN.matcher(line);
        if (signMatcher.find()) {
            String position = signMatcher.group(1); // LEADING or TRAILING
            field.setSignPosition(position.toUpperCase());

            // Check if SEPARATE keyword is present
            String fullMatch = signMatcher.group(0);
            if (fullMatch.toUpperCase().contains("SEPARATE")) {
                field.setSignSeparate(true);
            }
        }

        // Parse JUSTIFIED RIGHT
        if (JUSTIFIED_PATTERN.matcher(line).find()) {
            field.setJustifiedRight(true);
        }

        // Parse BLANK WHEN ZERO
        if (BLANK_WHEN_ZERO_PATTERN.matcher(line).find()) {
            field.setBlankWhenZero(true);
        }

        return field;
    }

    private static FieldDefinition getFieldDefinition(int lineNumber, Matcher conditionMatcher) {
        FieldDefinition field = new FieldDefinition();
        field.setLevel(88);
        field.setName(conditionMatcher.group(1));
        field.setLineNumber(lineNumber);

        // Store the VALUE/VALUES clause content for later parsing
        String valueContent = conditionMatcher.group(2).trim();
        // Remove trailing period if present
        if (valueContent.endsWith(".")) {
            valueContent = valueContent.substring(0, valueContent.length() - 1).trim();
        }
        field.setValue(valueContent);
        return field;
    }

    /**
     * Calculate byte offsets for all fields, handling REDEFINES.
     */
    private void calculateOffsets(List<FieldDefinition> fields) {
        int currentOffset = 0;

        for (FieldDefinition field : fields) {
            if (field.getRedefines() != null) {
                // REDEFINES: use the same offset as the redefined field
                for (FieldDefinition f : fields) {
                    if (f.getName().equals(field.getRedefines())) {
                        field.setOffset(f.getOffset());
                        break;
                    }
                }
            } else {
                // Normal field: use current offset
                field.setOffset(currentOffset);
                if (!field.isGroup()) {
                    currentOffset += field.getByteLength();
                }
            }
        }
    }

    /**
     * Parse a copybook from a string.
     */
    public CopybookDefinition parseString(String copybook) throws IOException {
        try (BufferedReader reader = new BufferedReader(new java.io.StringReader(copybook))) {
            return parse(reader);
        }
    }
}

