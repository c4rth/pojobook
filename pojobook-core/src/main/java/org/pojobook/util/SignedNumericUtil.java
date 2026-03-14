package org.pojobook.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.Arrays;

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
        return parseSignedDisplay(decimalDigits, str);
    }

    private static String parseSignedDisplay(int decimalDigits, String str) {
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
        return parseSignedDisplay(decimalDigits, str);
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

    // EBCDIC overpunch byte values: positive digit d → 0xC0+d, negative digit d → 0xD0+d
    private static final byte[] EBCDIC_POSITIVE_OVERPUNCH = {
            (byte) 0xC0, (byte) 0xC1, (byte) 0xC2, (byte) 0xC3, (byte) 0xC4,
            (byte) 0xC5, (byte) 0xC6, (byte) 0xC7, (byte) 0xC8, (byte) 0xC9
    };
    private static final byte[] EBCDIC_NEGATIVE_OVERPUNCH = {
            (byte) 0xD0, (byte) 0xD1, (byte) 0xD2, (byte) 0xD3, (byte) 0xD4,
            (byte) 0xD5, (byte) 0xD6, (byte) 0xD7, (byte) 0xD8, (byte) 0xD9
    };

    /**
     * Format a numeric value with overpunched sign and write it directly into a
     * destination buffer.
     * <p>
     * For known single-byte charsets (EBCDIC / ASCII) all digit bytes and the
     * trailing overpunch byte are written directly without any intermediate
     * String or byte-array allocation.  For unknown charsets the method falls
     * back to the standard {@code getBytes} encoding path.
     *
     * @param buffer        destination byte array
     * @param offset        starting position in the buffer
     * @param value         the numeric value to format
     * @param length        the total field length (number of digits)
     * @param decimalDigits number of implied decimal digits
     * @param charset       the character encoding
     */
    public static void formatSignedNumericDirect(byte[] buffer, int offset,
                                                 Object value, int length,
                                                 int decimalDigits, Charset charset) {
        int mode = CharsetMode.detect(charset);

        if (value == null) {
            if (mode != CharsetMode.UNKNOWN) {
                byte zeroByte = CharsetMode.zeroByte(mode);
                Arrays.fill(buffer, offset, offset + length, zeroByte);
            } else {
                byte[] bytes = padWithZeros("0", length).getBytes(charset);
                System.arraycopy(bytes, 0, buffer, offset, bytes.length);
            }
            return;
        }

        BigDecimal bdValue = toBigDecimal(value);
        boolean isNegative = bdValue.signum() < 0;
        String digits = formatAsUnscaledDigits(bdValue.abs(), length, decimalDigits);
        int lastDigitValue = digits.charAt(digits.length() - 1) - '0';

        if (mode != CharsetMode.UNKNOWN) {
            // Single-byte charset fast path – write digit bytes directly
            byte digitBase = CharsetMode.digitBase(mode);
            for (int i = 0; i < digits.length() - 1; i++) {
                buffer[offset + i] = (byte) (digits.charAt(i) - '0' + digitBase);
            }

            // Write overpunch byte for the last digit
            if (mode == CharsetMode.EBCDIC) {
                buffer[offset + length - 1] = isNegative
                        ? EBCDIC_NEGATIVE_OVERPUNCH[lastDigitValue]
                        : EBCDIC_POSITIVE_OVERPUNCH[lastDigitValue];
            } else {
                // ASCII: overpunch chars are in the 7-bit ASCII range, cast directly
                buffer[offset + length - 1] = (byte) (isNegative
                        ? NEGATIVE_OVERPUNCH[lastDigitValue]
                        : POSITIVE_OVERPUNCH[lastDigitValue]);
            }
        } else {
            // Unknown / multi-byte charset – fall back to standard encoding
            char overpunch = getOverpunchChar(lastDigitValue, isNegative);
            String formatted = digits.substring(0, digits.length() - 1) + overpunch;
            byte[] bytes = formatted.getBytes(charset);
            System.arraycopy(bytes, 0, buffer, offset, bytes.length);
        }
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
        final boolean negative = numStr.charAt(0) == '-';
        final int start = negative ? 1 : 0;
        final int len = numStr.length() - start;

        // Ensure enough digits by computing padding count directly
        final int padCount = Math.max(0, decimalDigits - len + 1);
        final int totalDigits = len + padCount;

        // Final string length: sign + intPart + '.' + decPart
        final int resultLength = (negative ? 1 : 0) + totalDigits + 1;
        final char[] out = new char[resultLength];

        int idx = 0;
        if (negative) {
            out[idx++] = '-';
        }

        // Write padded zeros + original digits
        int i = 0;

        // zeros before integer part
        for (; i < padCount; i++) {
            out[idx++] = '0';
        }

        // copy digits from input
        for (int j = start; j < numStr.length(); j++) {
            out[idx++] = numStr.charAt(j);
        }

        // Now insert the decimal point by shifting right
        // Move last decimalDigits chars right to make space for '.'
        System.arraycopy(out, idx - decimalDigits, out, idx - decimalDigits + 1, decimalDigits);

        out[idx - decimalDigits] = '.';  // insert decimal point

        return new String(out);
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

    private record OverpunchResult(char digit, boolean isNegative) {
    }
}
