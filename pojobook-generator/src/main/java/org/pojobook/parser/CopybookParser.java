package org.pojobook.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Enhanced parser for COBOL copybook definitions supporting REDEFINES, OCCURS variations,
 * and comprehensive COBOL language features.
 */
public class CopybookParser {

    private static final Pattern FIELD_PATTERN = Pattern.compile("^\\s*(\\d{2})\\s+([A-Z0-9\\-]+|FILLER)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONDITION_LEVEL_PATTERN = Pattern.compile("^\\s*88\\s+([A-Z0-9\\-]+)\\s+VALUES?\\s+(.+)$", Pattern.CASE_INSENSITIVE);

    private enum Clause {
        PICTURE("(?:PIC|PICTURE)\\s+([A-Z0-9()V\\-+S]+)", (field, matcher) -> field.setPicture(matcher.group(1))),
        REDEFINES("REDEFINES\\s+([A-Z0-9\\-]+)", (field, matcher) -> field.setRedefines(matcher.group(1))),
        OCCURS("OCCURS\\s+(\\d+)(?:\\s+TO\\s+(\\d+))?(?:\\s+TIMES)?(?:\\s+DEPENDING\\s+ON\\s+([A-Z0-9\\-]+))?", (field, matcher) -> {
            String minOccurs = matcher.group(1);
            String maxOccurs = matcher.group(2);
            String dependingOn = matcher.group(3);

            if (maxOccurs != null) {
                field.setMinOccurs(Integer.parseInt(minOccurs));
                field.setMaxOccurs(Integer.parseInt(maxOccurs));
                field.setOccurs(Integer.parseInt(maxOccurs));
            } else {
                field.setOccurs(Integer.parseInt(minOccurs));
            }
            if (dependingOn != null) {
                field.setDependingOn(dependingOn);
            }
        }),
        VALUE("VALUE\\s+(?:IS\\s+)?(['\"][^'\"]*['\"]|[A-Z0-9\\-]+)", (field, matcher) -> {
            String value = matcher.group(1);
            if (value.startsWith("'") || value.startsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            field.setValue(value);
        }),
        USAGE("(?:USAGE\\s+(?:IS\\s+)?)?(\\bCOMP(?:-[1-5])?\\b|\\bCOMPUTATIONAL(?:-[1-5])?\\b|\\bPACKED-DECIMAL\\b|\\bBINARY\\b|\\bDISPLAY\\b)", (field, matcher) -> field.setTypeFromString(matcher.group(1))),
        SYNC("SYNC(?:HRONIZED)?(?:\\s+(LEFT|RIGHT))?", (field, matcher) -> field.setSync(Objects.requireNonNullElse(matcher.group(1), ""))),
        INDEXED_BY("INDEXED\\s+BY\\s+([A-Z0-9\\-]+(?:\\s+[A-Z0-9\\-]+)*)", (field, matcher) -> field.setIndexedBy(matcher.group(1).trim().split("\\s+"))),
        KEY("(ASCENDING|DESCENDING)\\s+KEY\\s+(?:IS\\s+)?([A-Z0-9\\-]+(?:\\s+[A-Z0-9\\-]+)*)", (field, matcher) -> {
            field.setKeys(matcher.group(2).trim().split("\\s+"));
            field.setAscendingKey("ASCENDING".equalsIgnoreCase(matcher.group(1)));
            field.setDescendingKey("DESCENDING".equalsIgnoreCase(matcher.group(1)));
        }),
        SIGN("SIGN\\s+(?:IS\\s+)?(LEADING|TRAILING)(?:\\s+SEPARATE(?:\\s+CHARACTER)?)?", (field, matcher) -> {
            field.setSignPosition(matcher.group(1).toUpperCase());
            field.setSignSeparate(matcher.group(0).toUpperCase().contains("SEPARATE"));
        }),
        JUSTIFIED("JUST(?:IFIED)?(?:\\s+RIGHT)?", (field, matcher) -> field.setJustifiedRight(true)),
        BLANK_WHEN_ZERO("BLANK\\s+WHEN\\s+ZERO", (field, matcher) -> field.setBlankWhenZero(true));

        final Pattern pattern;
        final BiConsumer<FieldDefinition, Matcher> processor;

        Clause(String regex, BiConsumer<FieldDefinition, Matcher> processor) {
            this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            this.processor = processor;
        }
    }

    private static final Pattern QUOTED_VALUE_PATTERN = Pattern.compile("(['\"])([^'\"]*?)\\1");
    private static final Clause[] CLAUSES = Clause.values();

    /**
     * Parse a COBOL copybook from a string.
     */
    public CopybookDefinition parse(String copybook) throws ParseException {
        return parse(new StringReader(copybook));
    }

    /**
     * Parse a copybook from a file.
     */
    public CopybookDefinition parse(Path path) throws ParseException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return parse(reader);
        } catch (IOException e) {
            throw new ParseException("Error reading copybook file: " + e.getMessage(), -1, e);
        }
    }

    /**
     * Parse a copybook from a Reader.
     */
    public CopybookDefinition parse(Reader reader) throws ParseException {
        List<String> logicalLines = joinContinuationLines(new BufferedReader(reader).lines());
        List<FieldDefinition> fields = new ArrayList<>(logicalLines.size());

        for (int i = 0; i < logicalLines.size(); i++) {
            FieldDefinition field = parseLine(logicalLines.get(i), i + 1);
            if (field != null) {
                fields.add(field);
            }
        }

        linkConditionNames(fields);
        calculateOffsets(fields);

        return new CopybookDefinition(fields);
    }

    private List<String> joinContinuationLines(Stream<String> lines) {
        List<String> result = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();

        lines.forEach(line -> {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("*")) {
                return;
            }

            String content = line.length() > 6 ? line.substring(6) : line;
            boolean isContinuation = (line.length() > 6 && line.charAt(6) == '-') ||
                    (!content.trim().isEmpty() && !Character.isDigit(content.trim().charAt(0)));

            if (!isContinuation && !currentLine.isEmpty()) {
                result.add(currentLine.toString());
                currentLine.setLength(0);
            }

            currentLine.append(" ").append(content.trim());
        });

        if (!currentLine.isEmpty()) {
            result.add(currentLine.toString());
        }
        return result;
    }

    private FieldDefinition parseLine(String line, int lineNumber) {
        Matcher conditionMatcher = CONDITION_LEVEL_PATTERN.matcher(line);
        if (conditionMatcher.find()) {
            return createConditionField(lineNumber, conditionMatcher);
        }

        Matcher fieldMatcher = FIELD_PATTERN.matcher(line);
        if (!fieldMatcher.find()) {
            return null;
        }

        FieldDefinition field = new FieldDefinition();
        field.setLevel(Integer.parseInt(fieldMatcher.group(1)));
        field.setName(fieldMatcher.group(2));
        field.setLineNumber(lineNumber);
        field.setFiller("FILLER".equalsIgnoreCase(field.getName()));

        String normalizedLine = line.toUpperCase(Locale.ROOT);
        for (Clause clause : CLAUSES) {
            if (!mightContainClause(normalizedLine, clause)) {
                continue;
            }
            Matcher clauseMatcher = clause.pattern.matcher(line);
            if (clauseMatcher.find()) {
                clause.processor.accept(field, clauseMatcher);
            }
        }

        return field;
    }

    private boolean mightContainClause(String normalizedLine, Clause clause) {
        return switch (clause) {
            case PICTURE -> normalizedLine.contains("PIC");
            case REDEFINES -> normalizedLine.contains("REDEFINES");
            case OCCURS -> normalizedLine.contains("OCCURS");
            case VALUE -> normalizedLine.contains("VALUE");
            case USAGE -> containsUsageToken(normalizedLine);
            case SYNC -> normalizedLine.contains("SYNC");
            case INDEXED_BY -> normalizedLine.contains("INDEXED");
            case KEY -> normalizedLine.contains("KEY");
            case SIGN -> normalizedLine.contains("SIGN");
            case JUSTIFIED -> normalizedLine.contains("JUST");
            case BLANK_WHEN_ZERO -> normalizedLine.contains("BLANK");
        };
    }

    private boolean containsUsageToken(String normalizedLine) {
        return normalizedLine.contains("USAGE")
                || normalizedLine.contains("COMP")
                || normalizedLine.contains("COMPUTATIONAL")
                || normalizedLine.contains("PACKED-DECIMAL")
                || normalizedLine.contains("BINARY")
                || normalizedLine.contains("DISPLAY");
    }

    private FieldDefinition createConditionField(int lineNumber, Matcher conditionMatcher) {
        FieldDefinition field = new FieldDefinition();
        field.setLevel(88);
        field.setName(conditionMatcher.group(1));
        field.setLineNumber(lineNumber);

        String valueContent = conditionMatcher.group(2).trim();
        if (valueContent.endsWith(".")) {
            valueContent = valueContent.substring(0, valueContent.length() - 1).trim();
        }
        field.setValue(valueContent);
        return field;
    }

    private void linkConditionNames(List<FieldDefinition> fields) {
        FieldDefinition currentParent = null;
        for (FieldDefinition field : fields) {
            if (field.getLevel() == 88) {
                if (currentParent != null && !currentParent.isFiller()) {
                    currentParent.addConditionName(createConditionName(field, currentParent.getName()));
                }
            } else {
                currentParent = field;
            }
        }
    }

    private ConditionName createConditionName(FieldDefinition field, String parentName) {
        ConditionName condition = new ConditionName();
        condition.setName(field.getName());
        condition.setParentFieldName(parentName);
        condition.setLineNumber(field.getLineNumber());
        if (field.getValue() != null && !field.getValue().isEmpty()) {
            condition.setValues(parseConditionValues(field.getValue()));
        }
        return condition;
    }

    private String[] parseConditionValues(String valueClause) {
        List<String> values = QUOTED_VALUE_PATTERN.matcher(valueClause)
                .results()
                .map(match -> match.group(2))
                .toList();

        if (values.isEmpty() && !valueClause.trim().isEmpty()) {
            return new String[]{valueClause.trim()};
        }
        return values.toArray(String[]::new);
    }

    private void calculateOffsets(List<FieldDefinition> fields) {
        Map<String, Integer> nameToOffset = new HashMap<>();
        int currentOffset = 0;

        for (FieldDefinition field : fields) {
            if (field.getRedefines() != null) {
                field.setOffset(nameToOffset.getOrDefault(field.getRedefines(), currentOffset));
            } else {
                field.setOffset(currentOffset);
                if (!field.isFiller()) {
                    nameToOffset.put(field.getName(), currentOffset);
                }
                if (!field.isGroup()) {
                    currentOffset += field.getByteLength();
                }
            }
        }
    }
}
