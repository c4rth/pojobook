package org.pojobook.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;

/**
 * Utility class for handling unsigned numeric DISPLAY fields with implied decimal places.
 * In COBOL, when a DISPLAY field has the V notation (e.g., PIC 9(13)V9(2)), the decimal point
 * is implied and not stored in the data. This utility handles the conversion between
 * scaled values and unscaled byte representations.
 */
public final class DisplayNumericUtil {

    private DisplayNumericUtil() {
        // Prevent instantiation
    }

    /**
     * Parse an unsigned numeric value from COBOL DISPLAY format with implied decimal.
     * The decimal point is not present in the data and must be inserted at the correct position.
     *
     * @param data          the byte array containing the field
     * @param charset       the character encoding
     * @param decimalDigits number of implied decimal digits
     * @return the parsed BigDecimal value with proper scale
     */
    public static BigDecimal parseUnsignedWithImpliedDecimal(byte[] data, Charset charset, int decimalDigits) {
        if (data == null || data.length == 0) {
            return BigDecimal.ZERO;
        }

        String strValue = new String(data, charset).trim();
        if (strValue.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return new BigDecimal(new BigInteger(strValue), decimalDigits);
    }

    /**
     * Parse an unsigned numeric value from COBOL DISPLAY format with implied decimal.
     *
     * @param data          the byte array containing the field
     * @param offset        the offset in the array
     * @param length        the length of the field
     * @param charset       the character encoding
     * @param decimalDigits number of implied decimal digits
     * @return the parsed BigDecimal value with proper scale
     */
    public static BigDecimal parseUnsignedWithImpliedDecimal(byte[] data, int offset, int length, Charset charset, int decimalDigits) {
        if (data == null || length == 0) {
            return BigDecimal.ZERO;
        }

        String strValue = new String(data, offset, length, charset).trim();
        if (strValue.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return new BigDecimal(new BigInteger(strValue), decimalDigits);
    }

    /**
     * Parse an unsigned integer from COBOL DISPLAY format with implied decimal.
     *
     * @param data          the byte array containing the field
     * @param charset       the character encoding
     * @param decimalDigits number of implied decimal digits
     * @return the parsed integer value
     */
    public static int parseUnsignedInt(byte[] data, Charset charset, int decimalDigits) {
        return parseUnsignedWithImpliedDecimal(data, charset, decimalDigits).intValue();
    }

    /**
     * Parse an unsigned integer from COBOL DISPLAY format with implied decimal.
     *
     * @param data          the byte array containing the field
     * @param offset        the offset in the array
     * @param length        the length of the field
     * @param charset       the character encoding
     * @param decimalDigits number of implied decimal digits
     * @return the parsed integer value
     */
    public static int parseUnsignedInt(byte[] data, int offset, int length, Charset charset, int decimalDigits) {
        return parseUnsignedWithImpliedDecimal(data, offset, length, charset, decimalDigits).intValue();
    }

    /**
     * Parse an unsigned long from COBOL DISPLAY format with implied decimal.
     *
     * @param data          the byte array containing the field
     * @param charset       the character encoding
     * @param decimalDigits number of implied decimal digits
     * @return the parsed long value
     */
    public static long parseUnsignedLong(byte[] data, Charset charset, int decimalDigits) {
        return parseUnsignedWithImpliedDecimal(data, charset, decimalDigits).longValue();
    }

    /**
     * Parse an unsigned long from COBOL DISPLAY format with implied decimal.
     *
     * @param data          the byte array containing the field
     * @param offset        the offset in the array
     * @param length        the length of the field
     * @param charset       the character encoding
     * @param decimalDigits number of implied decimal digits
     * @return the parsed long value
     */
    public static long parseUnsignedLong(byte[] data, int offset, int length, Charset charset, int decimalDigits) {
        return parseUnsignedWithImpliedDecimal(data, offset, length, charset, decimalDigits).longValue();
    }

    /**
     * Format an unsigned numeric value to COBOL DISPLAY format with implied decimal.
     * The decimal point is removed and the value is stored as an unscaled integer.
     *
     * @param value         the numeric value to format
     * @param length        the total field length (integer digits + decimal digits)
     * @param decimalDigits number of decimal digits
     * @param charset       the character encoding
     * @return the formatted byte array without decimal point
     */
    public static byte[] formatUnsignedWithImpliedDecimal(Number value, int length, int decimalDigits, Charset charset) {
        String formatted = formatUnsignedNumericString(value, length, decimalDigits);
        return formatted.getBytes(charset);
    }

    /**
     * Format an unsigned numeric value and write it directly into a destination buffer
     * in COBOL DISPLAY format with implied decimal.
     * <p>
     * For known single-byte charsets (EBCDIC / ASCII) digits are written byte-by-byte
     * without any intermediate String or byte-array allocation.  For unknown charsets the
     * method falls back to the standard {@code getBytes} encoding path.
     *
     * @param buffer        destination byte array
     * @param offset        starting position in the buffer
     * @param value         the numeric value to format
     * @param length        the total field length (integer digits + decimal digits)
     * @param decimalDigits number of decimal digits
     * @param charset       the character encoding
     */
    public static void formatUnsignedWithImpliedDecimalDirect(byte[] buffer, int offset,
                                                              Number value, int length,
                                                              int decimalDigits, Charset charset) {
        String digits = formatUnsignedNumericString(value, length, decimalDigits);

        int mode = CharsetMode.detect(charset);
        if (mode != CharsetMode.UNKNOWN) {
            // Single-byte charset fast path – write digits directly, zero allocation
            byte digitBase = CharsetMode.digitBase(mode);
            for (int i = 0; i < digits.length(); i++) {
                buffer[offset + i] = (byte) (digits.charAt(i) - '0' + digitBase);
            }
        } else {
            // Unknown / multi-byte charset – fall back to standard encoding
            byte[] bytes = digits.getBytes(charset);
            System.arraycopy(bytes, 0, buffer, offset, bytes.length);
        }
    }

    /**
     * Format an unsigned numeric value to string with implied decimal (for generated code).
     *
     * @param value         the numeric value to format
     * @param length        the total field length
     * @param decimalDigits number of decimal digits
     * @return the formatted string without decimal point
     */
    public static String formatUnsignedNumericString(Object value, int length, int decimalDigits) {
        if (value == null) {
            return padWithZeros("0", length);
        }

        BigDecimal bdValue = toBigDecimal(value);
        if (decimalDigits > 0) {
            bdValue = bdValue.scaleByPowerOfTen(decimalDigits);
        }

        return padWithZeros(bdValue.toBigInteger().toString(), length);
    }

    private static BigDecimal toBigDecimal(Object value) {
        return value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
    }

    private static String padWithZeros(String value, int length) {
        return String.format("%0" + length + "d", new BigInteger(value));
    }
}
