package org.pojobook.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public class FieldLengthUtil {

    private FieldLengthUtil() {
        // Prevent instantiation
    }

    /**
     * Validate and return a string value, checking it doesn't exceed maximum length.
     */
    public static String checkStringLength(String value, int max, String fieldName) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException("Field " + fieldName + " exceeds maximum length of " + max);
        }
        return value;
    }

    /**
     * Validate and return an Integer value, checking it's within the range for the specified number of digits.
     */
    public static Integer checkIntegerRange(Integer value, int digits, String fieldName) {
        if (value != null) {
            long maxValue = calculateMaxValue(digits);
            long minValue = -maxValue;
            if (value > maxValue || value < minValue) {
                throw new IllegalArgumentException("Field " + fieldName + " value out of range for " + digits + " digits");
            }
        }
        return value;
    }

    /**
     * Validate and return a Long value, checking it's within the range for the specified number of digits.
     */
    public static Long checkLongRange(Long value, int digits, String fieldName) {
        if (value != null) {
            long maxValue = calculateMaxValue(digits);
            long minValue = -maxValue;
            if (value > maxValue || value < minValue) {
                throw new IllegalArgumentException("Field " + fieldName + " value out of range for " + digits + " digits");
            }
        }
        return value;
    }

    /**
     * Validate and return a Short value, checking it's within the range for the specified number of digits.
     */
    public static Short checkShortRange(Short value, int digits, String fieldName) {
        if (value != null) {
            long maxValue = calculateMaxValue(digits);
            long minValue = -maxValue;
            if (value > maxValue || value < minValue) {
                throw new IllegalArgumentException("Field " + fieldName + " value out of range for " + digits + " digits");
            }
        }
        return value;
    }

    /**
     * Validate and return a BigDecimal value, checking the integer part doesn't exceed the specified number of digits.
     */
    public static BigDecimal checkBigDecimalRange(BigDecimal value, int integerDigits, String fieldName) {
        if (value != null) {
            BigDecimal integerPart = value.abs().setScale(0, RoundingMode.DOWN);
            BigDecimal maxAllowed = new BigDecimal(String.valueOf(calculateMaxValue(integerDigits)));
            BigDecimal minAllowed = maxAllowed.negate();
            if (value.compareTo(maxAllowed) > 0 || value.compareTo(minAllowed) < 0) {
                throw new IllegalArgumentException("Field " + fieldName + " exceeds maximum integer digits of " + integerDigits);
            }
        }
        return value;
    }

    /**
     * Validate and return a BigInteger value, checking it's within the range for the specified number of digits.
     */
    public static BigInteger checkBigIntegerRange(BigInteger value, int digits, String fieldName) {
        if (value != null) {
            BigInteger maxAllowed = new BigInteger(String.valueOf(calculateMaxValue(digits)));
            BigInteger minAllowed = maxAllowed.negate();
            if (value.compareTo(maxAllowed) > 0 || value.compareTo(minAllowed) < 0) {
                throw new IllegalArgumentException("Field " + fieldName + " value out of range for " + digits + " digits");
            }
        }
        return value;
    }

    /**
     * Validate and return a string array, checking each element doesn't exceed maximum length.
     */
    public static String[] checkStringArrayLength(String[] values, int max, String fieldName) {
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                if (values[i] != null && values[i].length() > max) {
                    throw new IllegalArgumentException("Field " + fieldName + "[" + i + "] exceeds maximum length of " + max);
                }
            }
        }
        return values;
    }

    /**
     * Validate and return an Integer array, checking each element is within range.
     */
    public static Integer[] checkIntegerArrayRange(Integer[] values, int digits, String fieldName) {
        if (values != null) {
            long maxValue = calculateMaxValue(digits);
            long minValue = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i] != null && (values[i] > maxValue || values[i] < minValue)) {
                    throw new IllegalArgumentException("Field " + fieldName + "[" + i + "] value out of range for " + digits + " digits");
                }
            }
        }
        return values;
    }

    /**
     * Validate and return a Long array, checking each element is within range.
     */
    public static Long[] checkLongArrayRange(Long[] values, int digits, String fieldName) {
        if (values != null) {
            long maxValue = calculateMaxValue(digits);
            long minValue = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i] != null && (values[i] > maxValue || values[i] < minValue)) {
                    throw new IllegalArgumentException("Field " + fieldName + "[" + i + "] value out of range for " + digits + " digits");
                }
            }
        }
        return values;
    }

    /**
     * Validate and return a Short array, checking each element is within range.
     */
    public static Short[] checkShortArrayRange(Short[] values, int digits, String fieldName) {
        if (values != null) {
            long maxValue = calculateMaxValue(digits);
            long minValue = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i] != null && (values[i] > maxValue || values[i] < minValue)) {
                    throw new IllegalArgumentException("Field " + fieldName + "[" + i + "] value out of range for " + digits + " digits");
                }
            }
        }
        return values;
    }

    /**
     * Validate and return a BigDecimal array, checking each element's integer part doesn't exceed specified digits.
     */
    public static BigDecimal[] checkBigDecimalArrayRange(BigDecimal[] values, int integerDigits, String fieldName) {
        if (values != null) {
            BigDecimal maxAllowed = new BigDecimal(String.valueOf(calculateMaxValue(integerDigits)));
            for (int i = 0; i < values.length; i++) {
                if (values[i] != null) {
                    BigDecimal integerPart = values[i].abs().setScale(0, RoundingMode.DOWN);
                    if (integerPart.compareTo(maxAllowed) > 0) {
                        throw new IllegalArgumentException("Field " + fieldName + "[" + i + "] exceeds maximum integer digits of " + integerDigits);
                    }
                }
            }
        }
        return values;
    }

    /**
     * Validate and return a BigInteger array, checking each element is within range.
     */
    public static BigInteger[] checkBigIntegerArrayRange(BigInteger[] values, int digits, String fieldName) {
        if (values != null) {
            BigInteger maxAllowed = new BigInteger(String.valueOf(calculateMaxValue(digits)));
            BigInteger minAllowed = BigInteger.ZERO;
            for (int i = 0; i < values.length; i++) {
                if (values[i] != null && (values[i].compareTo(maxAllowed) > 0 || values[i].compareTo(minAllowed) < 0)) {
                    throw new IllegalArgumentException("Field " + fieldName + "[" + i + "] value out of range for " + digits + " digits");
                }
            }
        }
        return values;
    }

    /**
     * Validate and return an array, checking its length matches the expected occurs value.
     */
    public static <T> T[] checkArrayLength(T[] values, int expectedLength, String fieldName) {
        if (values != null && values.length != expectedLength) {
            throw new IllegalArgumentException("Field " + fieldName + " array length must be exactly " + expectedLength + " but was " + values.length);
        }
        return values;
    }

    /**
     * Calculate the maximum value for a given number of digits.
     * For example: 3 digits -> 999, 5 digits -> 99999
     */
    private static long calculateMaxValue(int digits) {
        if (digits == 0) return 0;
        long max = 1;
        for (int i = 0; i < digits; i++) {
            max *= 10;
        }
        return max - 1;
    }
}
