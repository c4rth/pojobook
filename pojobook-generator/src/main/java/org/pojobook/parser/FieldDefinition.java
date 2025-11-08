package org.pojobook.parser;

import org.pojobook.CobolDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a field definition from a COBOL copybook.
 */
public class FieldDefinition {
    private int level;
    private String name;
    private String picture;
    private CobolDataType type = CobolDataType.DISPLAY;
    private int occurs = 1;
    private int minOccurs = -1;
    private int maxOccurs = -1;
    private String dependingOn;
    private int lineNumber;
    private int integerDigits;
    private int decimalDigits;
    private boolean signed;
    private String signPosition; // "LEADING" or "TRAILING"
    private boolean signSeparate; // true if SEPARATE CHARACTER
    private String redefines;
    private boolean filler;
    private String value;
    private boolean justifiedRight;
    private boolean blankWhenZero;
    private String sync;
    private String[] indexedBy;
    private String[] keys;
    private boolean ascendingKey;
    private boolean descendingKey;
    private int offset; // Byte offset in record
    private List<ConditionName> conditionNames = new ArrayList<>(); // 88-level items

    private static final Pattern PICTURE_PATTERN = Pattern.compile(
            "([S\\+\\-])?(9\\((\\d+)\\)|X\\((\\d+)\\)|[9X]+)(?:V(9\\((\\d+)\\)|[9]+))?",
            Pattern.CASE_INSENSITIVE
    );

    public FieldDefinition() {
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPicture() {
        return picture;
    }

    public void setPicture(String picture) {
        this.picture = picture;
        parsePicture(picture);
    }

    public CobolDataType getType() {
        return type;
    }

    public void setType(CobolDataType type) {
        this.type = type;
    }

    public void setTypeFromString(String typeStr) {
        if (typeStr == null) {
            this.type = CobolDataType.DISPLAY;
            return;
        }

        this.type = switch (typeStr.toUpperCase()) {
            case "COMP", "COMP-4", "BINARY" -> CobolDataType.COMP;
            case "COMP-1" -> CobolDataType.COMP_1;
            case "COMP-2" -> CobolDataType.COMP_2;
            case "COMP-3", "PACKED-DECIMAL" -> CobolDataType.COMP_3;
            case "COMP-5" -> CobolDataType.COMP_5;
            default -> CobolDataType.DISPLAY;
        };
    }

    public int getOccurs() {
        return occurs;
    }

    public void setOccurs(int occurs) {
        this.occurs = occurs;
    }

    public String getDependingOn() {
        return dependingOn;
    }

    public void setDependingOn(String dependingOn) {
        this.dependingOn = dependingOn;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getRedefines() {
        return redefines;
    }

    public void setRedefines(String redefines) {
        this.redefines = redefines;
    }

    public boolean isFiller() {
        return filler;
    }

    public void setFiller(boolean filler) {
        this.filler = filler;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public int getMinOccurs() {
        return minOccurs;
    }

    public void setMinOccurs(int minOccurs) {
        this.minOccurs = minOccurs;
    }

    public int getMaxOccurs() {
        return maxOccurs;
    }

    public void setMaxOccurs(int maxOccurs) {
        this.maxOccurs = maxOccurs;
    }

    public boolean isJustifiedRight() {
        return justifiedRight;
    }

    public void setJustifiedRight(boolean justifiedRight) {
        this.justifiedRight = justifiedRight;
    }

    public boolean isBlankWhenZero() {
        return blankWhenZero;
    }

    public void setBlankWhenZero(boolean blankWhenZero) {
        this.blankWhenZero = blankWhenZero;
    }

    public String getSync() {
        return sync;
    }

    public void setSync(String sync) {
        this.sync = sync;
    }

    public String[] getIndexedBy() {
        return indexedBy;
    }

    public void setIndexedBy(String[] indexedBy) {
        this.indexedBy = indexedBy;
    }

    public String[] getKeys() {
        return keys;
    }

    public void setKeys(String[] keys) {
        this.keys = keys;
    }

    public boolean isAscendingKey() {
        return ascendingKey;
    }

    public void setAscendingKey(boolean ascendingKey) {
        this.ascendingKey = ascendingKey;
    }

    public void setDescendingKey(boolean descendingKey) {
        this.descendingKey = descendingKey;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public List<ConditionName> getConditionNames() {
        return conditionNames;
    }

    public void addConditionName(ConditionName conditionName) {
        if (this.conditionNames == null) {
            this.conditionNames = new ArrayList<>();
        }
        this.conditionNames.add(conditionName);
    }

    public boolean hasConditionNames() {
        return conditionNames != null && !conditionNames.isEmpty();
    }

    public int getIntegerDigits() {
        return integerDigits;
    }

    public int getDecimalDigits() {
        return decimalDigits;
    }

    public boolean isSigned() {
        return signed;
    }

    public String getSignPosition() {
        return signPosition;
    }

    public void setSignPosition(String signPosition) {
        this.signPosition = signPosition;
    }

    public boolean isSignSeparate() {
        return signSeparate;
    }

    public void setSignSeparate(boolean signSeparate) {
        this.signSeparate = signSeparate;
    }

    /**
     * Parse the PICTURE clause to extract field attributes.
     */
    private void parsePicture(String pic) {
        if (pic == null || pic.isEmpty()) {
            return;
        }

        // Check for sign
        this.signed = pic.startsWith("S") || pic.contains("+") || pic.contains("-");

        // Extract digits
        Matcher matcher = PICTURE_PATTERN.matcher(pic);
        if (matcher.find()) {
            // Group 2: integer part (9(n) or X(n) or 9+ or X+)
            String intPart = matcher.group(2);
            if (intPart != null) {
                if (intPart.startsWith("9(") || intPart.startsWith("X(")) {
                    // Extract number from 9(n) or X(n)
                    String digits = matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
                    if (digits != null) {
                        this.integerDigits = Integer.parseInt(digits);
                    }
                } else {
                    // Count 9s or Xs
                    this.integerDigits = intPart.length();
                }
            }

            // Group 5: decimal part after V (9(n) or 9+)
            String decPart = matcher.group(5);
            if (decPart != null) {
                if (decPart.startsWith("9(")) {
                    // Extract number from 9(n)
                    String digits = matcher.group(6);
                    if (digits != null) {
                        this.decimalDigits = Integer.parseInt(digits);
                    }
                } else {
                    // Count 9s
                    this.decimalDigits = decPart.length();
                }
            }
        } else {
            // Fallback: handle simple cases like "X" or "9"
            // Count X and 9 characters directly
            int xCount = pic.length() - pic.replace("X", "").replace("x", "").length();
            int nineCount = pic.length() - pic.replace("9", "").length();
            this.integerDigits = Math.max(xCount, nineCount);
            if (this.integerDigits == 0) {
                this.integerDigits = 1; // Default to at least 1 if we have a picture
            }
        }
    }

    /**
     * Calculate the byte length of this field.
     */
    public int getByteLength() {
        int totalDigits = integerDigits + decimalDigits;

        int baseLength = switch (type) {
            case DISPLAY, ZONED_DECIMAL -> totalDigits;
            case COMP, COMP_5 -> {
                if (totalDigits <= 4) yield 2;
                if (totalDigits <= 9) yield 4;
                yield 8;
            }
            case COMP_1 -> 4;
            case COMP_2 -> 8;
            case COMP_3, PACKED_DECIMAL -> (totalDigits / 2) + 1;
        };

        // Add extra byte for SIGN SEPARATE CHARACTER
        if (type == CobolDataType.DISPLAY && signed && signSeparate) {
            baseLength += 1;
        }

        return baseLength * occurs;
    }

    /**
     * Check if this is a group field (no PICTURE clause).
     */
    public boolean isGroup() {
        return picture == null || picture.isEmpty();
    }
}

