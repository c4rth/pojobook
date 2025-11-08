package org.pojobook.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;

/**
 * Utility class for handling signed numeric DISPLAY fields with embedded signs (overpunch).
 * In COBOL, when a DISPLAY field has the SIGN clause without SEPARATE, the sign is encoded
 * in the zone bits of the last digit (overpunched).
 */
public final class SignedNumericUtil {

    private static final char[] POSITIVE_OVERPUNCH = {'{', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I'};
    private static final char[] NEGATIVE_OVERPUNCH = {'}', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R'};

    private SignedNumericUtil() {
        // Prevent instantiation
    }

    /**
     * Parse a signed numeric string from COBOL DISPLAY format with overpunched sign.
     *
     * @param data          the byte array containing the field
     * @param charset       the character encoding
     * @param decimalDigits number of decimal digits
     * @return the parsed value as a string with proper sign
     */
    public static String parseSignedDisplay(byte[] data, Charset charset, int decimalDigits) {
        if (data == null || data.length == 0) {
            return "0";
        }

        String str = new String(data, charset).trim();
        if (str.isEmpty()) {
            return "0";
        }

        char lastChar = str.charAt(str.length() - 1);
        String prefix = str.substring(0, str.length() - 1).trim();

        OverpunchResult decoded = decodeOverpunch(lastChar);
        String numericStr = buildNumericString(prefix, decoded);

        return decimalDigits > 0
                ? insertDecimalPoint(numericStr, decimalDigits)
                : numericStr;
    }

    /**
     * Parse a signed numeric string from COBOL DISPLAY format with overpunched sign.
     *
     * @param data          the byte array containing the field
     * @param offset        the offset in the array
     * @param length        the length of the field
     * @param charset       the character encoding
     * @param decimalDigits number of decimal digits
     * @return the parsed value as a string with proper sign
     */
    public static String parseSignedDisplay(byte[] data, int offset, int length, Charset charset, int decimalDigits) {
        if (data == null || length == 0) {
            return "0";
        }

        String str = new String(data, offset, length, charset).trim();
        if (str.isEmpty()) {
            return "0";
        }

        char lastChar = str.charAt(str.length() - 1);
        String prefix = str.substring(0, str.length() - 1).trim();

        OverpunchResult decoded = decodeOverpunch(lastChar);
        String numericStr = buildNumericString(prefix, decoded);

        return decimalDigits > 0
                ? insertDecimalPoint(numericStr, decimalDigits)
                : numericStr;
    }

    /**
     * Parse a signed integer from COBOL DISPLAY format with overpunched sign.
     */
    public static int parseSignedInt(byte[] data, Charset charset) {
        return parseNumber(data, charset, 0, Integer::parseInt, 0);
    }

    /**
     * Parse a signed integer from COBOL DISPLAY format with overpunched sign.
     */
    public static int parseSignedInt(byte[] data, int offset, int length, Charset charset) {
        return parseNumber(data, offset, length, charset, 0, Integer::parseInt, 0);
    }

    /**
     * Parse a signed long from COBOL DISPLAY format with overpunched sign.
     */
    public static long parseSignedLong(byte[] data, Charset charset) {
        return parseNumber(data, charset, 0, Long::parseLong, 0L);
    }

    /**
     * Parse a signed long from COBOL DISPLAY format with overpunched sign.
     */
    public static long parseSignedLong(byte[] data, int offset, int length, Charset charset) {
        return parseNumber(data, offset, length, charset, 0, Long::parseLong, 0L);
    }

    /**
     * Parse a signed BigDecimal from COBOL DISPLAY format with overpunched sign.
     */
    public static BigDecimal parseSignedBigDecimal(byte[] data, Charset charset, int decimalDigits) {
        return parseNumber(data, charset, decimalDigits, BigDecimal::new, BigDecimal.ZERO);
    }

    /**
     * Parse a signed BigDecimal from COBOL DISPLAY format with overpunched sign.
     */
    public static BigDecimal parseSignedBigDecimal(byte[] data, int offset, int length, Charset charset, int decimalDigits) {
        return parseNumber(data, offset, length, charset, decimalDigits, BigDecimal::new, BigDecimal.ZERO);
    }

    /**
     * Parse a signed BigInteger from COBOL DISPLAY format with overpunched sign.
     */
    public static BigInteger parseSignedBigInteger(byte[] data, Charset charset) {
        return parseNumber(data, charset, 0, BigInteger::new, BigInteger.ZERO);
    }

    /**
     * Format a numeric value to COBOL DISPLAY format with overpunched sign.
     */
    public static byte[] formatSignedDisplay(Number value, int length, int decimalDigits, Charset charset) {
        String formatted = formatSignedNumeric(value, length, decimalDigits);
        return formatted.getBytes(charset);
    }

    /**
     * Format a numeric value to string with overpunched sign (for generated code).
     */
    public static String formatSignedNumeric(Object value, int length, int decimalDigits) {
        if (value == null) {
            return padWithZeros("0", length);
        }

        BigDecimal bdValue = toBigDecimal(value);
        boolean isNegative = bdValue.signum() < 0;

        String digits = formatAsUnscaledDigits(bdValue.abs(), length, decimalDigits);
        char lastDigit = digits.charAt(digits.length() - 1);
        char overpunch = getOverpunchChar(lastDigit - '0', isNegative);

        return digits.substring(0, digits.length() - 1) + overpunch;
    }

    private static <T> T parseNumber(byte[] data, Charset charset, int decimalDigits,
                                     Parser<T> parser, T defaultValue) {
        String strValue = parseSignedDisplay(data, charset, decimalDigits);
        return strValue.isEmpty() || strValue.equals("0") ? defaultValue : parser.parse(strValue);
    }

    private static <T> T parseNumber(byte[] data, int offset, int length, Charset charset, int decimalDigits,
                                     Parser<T> parser, T defaultValue) {
        String strValue = parseSignedDisplay(data, offset, length, charset, decimalDigits);
        return strValue.isEmpty() || strValue.equals("0") ? defaultValue : parser.parse(strValue);
    }

    private static OverpunchResult decodeOverpunch(char ch) {
        for (int digit = 0; digit <= 9; digit++) {
            if (ch == POSITIVE_OVERPUNCH[digit] || ch == Character.forDigit(digit, 10)) {
                return new OverpunchResult((char) ('0' + digit), false);
            }
            if (ch == NEGATIVE_OVERPUNCH[digit]) {
                return new OverpunchResult((char) ('0' + digit), true);
            }
        }

        // Fallback: extract digit portion from zone bits
        return new OverpunchResult((char) ('0' + (ch & 0x0F)), false);
    }

    private static String buildNumericString(String prefix, OverpunchResult decoded) {
        StringBuilder result = new StringBuilder();
        if (decoded.isNegative) {
            result.append('-');
        }
        return result.append(prefix).append(decoded.digit).toString();
    }

    private static String insertDecimalPoint(String numStr, int decimalDigits) {
        boolean hasSign = numStr.startsWith("-");
        String absValue = hasSign ? numStr.substring(1) : numStr;

        // Pad with leading zeros if needed
        while (absValue.length() <= decimalDigits) {
            absValue = "0" + absValue;
        }

        int decimalPos = absValue.length() - decimalDigits;
        String intPart = absValue.substring(0, decimalPos);
        String decPart = absValue.substring(decimalPos);

        return (hasSign ? "-" : "") + intPart + "." + decPart;
    }

    private static BigDecimal toBigDecimal(Object value) {
        return value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
    }

    private static String formatAsUnscaledDigits(BigDecimal value, int length, int decimalDigits) {
        if (decimalDigits > 0) {
            value = value.scaleByPowerOfTen(decimalDigits);
        }
        return padWithZeros(value.toBigInteger().toString(), length);
    }

    private static String padWithZeros(String value, int length) {
        return String.format("%0" + length + "d", new BigInteger(value));
    }

    private static char getOverpunchChar(int digit, boolean isNegative) {
        return isNegative ? NEGATIVE_OVERPUNCH[digit] : POSITIVE_OVERPUNCH[digit];
    }

    @FunctionalInterface
    private interface Parser<T> {
        T parse(String value);
    }

    private record OverpunchResult(char digit, boolean isNegative) {}
}
