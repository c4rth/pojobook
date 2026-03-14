package org.pojobook.deserializer;

import org.pojobook.util.CharsetMode;
import org.pojobook.util.DisplayNumericUtil;
import org.pojobook.util.SignedNumericUtil;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.Charset;

/**
 * Helper class containing COBOL field deserialization methods.
 * This class is used by both CobolDeserializer (for runtime deserialization)
 * and EmbeddedSerializationPojoGenerator (for code generation).
 */
public class CobolFieldDeserializer {

    /**
     * Deserialize a DISPLAY string field.
     * <p>
     * For known single-byte charsets (ASCII/EBCDIC), trailing and leading space
     * bytes are trimmed on the raw byte array before constructing the String,
     * avoiding the double allocation of {@code new String(...).trim()}.
     */
    public static String deserializeDisplayString(byte[] data, int offset, int length, Charset charset) {
        if (length <= 0) {
            return "";
        }

        int mode = CharsetMode.detect(charset);
        if (mode == CharsetMode.UNKNOWN) {
            return new String(data, offset, length, charset).trim();
        }

        int spaceByte = mode == CharsetMode.ASCII ? 0x20 : 0x40;

        // Trim leading spaces
        int start = offset;
        int end = offset + length;
        while (start < end && (data[start] & 0xFF) == spaceByte) {
            start++;
        }
        // Trim trailing spaces
        while (end > start && (data[end - 1] & 0xFF) == spaceByte) {
            end--;
        }

        int trimmedLength = end - start;
        if (trimmedLength <= 0) {
            return "";
        }

        return new String(data, start, trimmedLength, charset);
    }

    /**
     * Deserialize a simple DISPLAY numeric field (no decimals).
     */
    public static Integer deserializeDisplayInteger(byte[] data, int offset, int length, Charset charset) {
        long value = parseUnsignedDisplayLong(data, offset, length, charset);
        if (value > Integer.MAX_VALUE) {
            throw new NumberFormatException("For input string: \"" + new String(data, offset, length, charset).trim() + "\"");
        }
        return (int) value;
    }

    /**
     * Deserialize a simple DISPLAY numeric field (no decimals) to Long.
     */
    public static Long deserializeDisplayLong(byte[] data, int offset, int length, Charset charset) {
        return parseUnsignedDisplayLong(data, offset, length, charset);
    }

    /**
     * Deserialize a simple DISPLAY numeric field (no decimals) to Short.
     */
    public static Short deserializeDisplayShort(byte[] data, int offset, int length, Charset charset) {
        long value = parseUnsignedDisplayLong(data, offset, length, charset);
        if (value > Short.MAX_VALUE) {
            throw new NumberFormatException("For input string: \"" + new String(data, offset, length, charset).trim() + "\"");
        }
        return (short) value;
    }

    private static long parseUnsignedDisplayLong(byte[] data, int offset, int length, Charset charset) {
        if (length <= 0) {
            return 0L;
        }

        int mode = CharsetMode.detect(charset);
        if (mode == CharsetMode.UNKNOWN) {
            String strValue = new String(data, offset, length, charset).trim();
            return strValue.isEmpty() ? 0L : Long.parseLong(strValue);
        }

        int asciiSpace = mode == CharsetMode.ASCII ? 0x20 : 0x40;
        int digitBase = mode == CharsetMode.ASCII ? 0x30 : 0xF0;

        int start = offset;
        int end = offset + length - 1;
        while (start <= end && (data[start] & 0xFF) == asciiSpace) {
            start++;
        }
        while (end >= start && (data[end] & 0xFF) == asciiSpace) {
            end--;
        }
        if (start > end) {
            return 0L;
        }

        long value = 0L;
        for (int i = start; i <= end; i++) {
            int b = data[i] & 0xFF;
            int digit = b - digitBase;
            if (digit < 0 || digit > 9) {
                throw new NumberFormatException("For input string: \"" + new String(data, offset, length, charset).trim() + "\"");
            }
            if (value > (Long.MAX_VALUE - digit) / 10L) {
                throw new NumberFormatException("For input string: \"" + new String(data, offset, length, charset).trim() + "\"");
            }
            value = value * 10L + digit;
        }
        return value;
    }


    /**
     * Deserialize a simple DISPLAY numeric field (no decimals) to BigDecimal.
     */
    public static BigDecimal deserializeDisplayBigDecimal(byte[] data, int offset, int length, Charset charset) {
        String strValue = new String(data, offset, length, charset).trim();
        return strValue.isEmpty() ? BigDecimal.ZERO : new BigDecimal(strValue);
    }

    /**
     * Deserialize a simple DISPLAY numeric field (no decimals) to BigInteger.
     */
    public static BigInteger deserializeDisplayBigInteger(byte[] data, int offset, int length, Charset charset) {
        String strValue = new String(data, offset, length, charset).trim();
        return strValue.isEmpty() ? BigInteger.ZERO : new BigInteger(strValue);
    }

    /**
     * Deserialize DISPLAY field with implied decimal to Integer.
     */
    public static Integer deserializeDisplayIntegerWithDecimal(byte[] data, int offset, int length,
                                                               Charset charset, int decimalDigits) {
        return DisplayNumericUtil.parseUnsignedInt(data, offset, length, charset, decimalDigits);
    }

    /**
     * Deserialize DISPLAY field with implied decimal to Long.
     */
    public static Long deserializeDisplayLongWithDecimal(byte[] data, int offset, int length,
                                                         Charset charset, int decimalDigits) {
        return DisplayNumericUtil.parseUnsignedLong(data, offset, length, charset, decimalDigits);
    }

    /**
     * Deserialize DISPLAY field with implied decimal to BigDecimal.
     */
    public static BigDecimal deserializeDisplayBigDecimalWithDecimal(byte[] data, int offset, int length,
                                                                     Charset charset, int decimalDigits) {
        return DisplayNumericUtil.parseUnsignedWithImpliedDecimal(data, offset, length, charset, decimalDigits);
    }

    /**
     * Deserialize DISPLAY field with embedded sign to Integer.
     */
    public static Integer deserializeDisplaySignedInteger(byte[] data, int offset, int length, Charset charset) {
        return SignedNumericUtil.parseSignedInt(data, offset, length, charset);
    }

    /**
     * Deserialize DISPLAY field with embedded sign to Long.
     */
    public static Long deserializeDisplaySignedLong(byte[] data, int offset, int length, Charset charset) {
        return SignedNumericUtil.parseSignedLong(data, offset, length, charset);
    }

    /**
     * Deserialize DISPLAY field with embedded sign to BigDecimal.
     */
    public static BigDecimal deserializeDisplaySignedBigDecimal(byte[] data, int offset, int length,
                                                                Charset charset, int decimalDigits) {
        return SignedNumericUtil.parseSignedBigDecimal(data, offset, length, charset, decimalDigits);
    }

    /**
     * Deserialize DISPLAY field with embedded sign to BigInteger.
     */
    public static BigInteger deserializeDisplaySignedBigInteger(byte[] data, int offset, int length, Charset charset) {
        return SignedNumericUtil.parseSignedBigDecimal(data, offset, length, charset, 0).toBigInteger();
    }

    /**
     * Deserialize COMP/BINARY field to Short.
     */
    public static Short deserializeCompShort(byte[] data, int offset, int size) {
        return (short) ((data[offset] << 8) | (data[offset + 1] & 0xFF));
    }

    /**
     * Deserialize COMP/BINARY field to Integer.
     */
    public static Integer deserializeCompInteger(byte[] data, int offset, int size) {
        if (size == 2) {
            return (int) (short) ((data[offset] << 8) | (data[offset + 1] & 0xFF));
        }
        return (data[offset] << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    /**
     * Deserialize COMP/BINARY field to Long.
     */
    public static Long deserializeCompLong(byte[] data, int offset, int size) {
        if (size == 8) {
            return ((long) data[offset] << 56)
                    | ((long) (data[offset + 1] & 0xFF) << 48)
                    | ((long) (data[offset + 2] & 0xFF) << 40)
                    | ((long) (data[offset + 3] & 0xFF) << 32)
                    | ((long) (data[offset + 4] & 0xFF) << 24)
                    | ((long) (data[offset + 5] & 0xFF) << 16)
                    | ((long) (data[offset + 6] & 0xFF) << 8)
                    | ((long) (data[offset + 7] & 0xFF));
        }
        return (long) ((data[offset] << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF));
    }

    /**
     * Deserialize COMP/BINARY field to BigInteger.
     */
    public static BigInteger deserializeCompBigInteger(byte[] data, int offset, int size) {
        if (size == 8) {
            long value = ((long) data[offset] << 56)
                    | ((long) (data[offset + 1] & 0xFF) << 48)
                    | ((long) (data[offset + 2] & 0xFF) << 40)
                    | ((long) (data[offset + 3] & 0xFF) << 32)
                    | ((long) (data[offset + 4] & 0xFF) << 24)
                    | ((long) (data[offset + 5] & 0xFF) << 16)
                    | ((long) (data[offset + 6] & 0xFF) << 8)
                    | ((long) (data[offset + 7] & 0xFF));
            return BigInteger.valueOf(value);
        }
        int value = (data[offset] << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
        return BigInteger.valueOf(value);
    }

    /**
     * Deserialize COMP-1 (float) field.
     */
    public static Float deserializeComp1(byte[] data, int offset) {
        int intBits = (data[offset] << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
        return Float.intBitsToFloat(intBits);
    }

    /**
     * Deserialize COMP-2 (double) field.
     */
    public static Double deserializeComp2(byte[] data, int offset) {
        long longBits = ((long) data[offset] << 56)
                | ((long) (data[offset + 1] & 0xFF) << 48)
                | ((long) (data[offset + 2] & 0xFF) << 40)
                | ((long) (data[offset + 3] & 0xFF) << 32)
                | ((long) (data[offset + 4] & 0xFF) << 24)
                | ((long) (data[offset + 5] & 0xFF) << 16)
                | ((long) (data[offset + 6] & 0xFF) << 8)
                | ((long) (data[offset + 7] & 0xFF));
        return Double.longBitsToDouble(longBits);
    }

    /**
     * Deserialize COMP-3 (packed decimal) field to BigDecimal with decimals.
     */
    public static BigDecimal deserializeComp3BigDecimal(byte[] data, int offset, int length,
                                                        int totalDigits, int decimalDigits) {
        if (totalDigits <= 18) {
            long longValue = deserializeComp3ToLong(data, offset, length, totalDigits);
            return BigDecimal.valueOf(longValue, decimalDigits).stripTrailingZeros();
        }
        BigInteger biValue = deserializeComp3ToBigInteger(data, offset, length, totalDigits);
        BigDecimal bdValue = new BigDecimal(biValue);
        return bdValue.divide(BigDecimal.TEN.pow(decimalDigits), decimalDigits, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    /**
     * Deserialize COMP-3 (packed decimal) field to Integer (no decimals).
     */
    public static Integer deserializeComp3Integer(byte[] data, int offset, int length, int totalDigits) {
        if (totalDigits <= 18) {
            return (int) deserializeComp3ToLong(data, offset, length, totalDigits);
        }
        return deserializeComp3ToBigInteger(data, offset, length, totalDigits).intValue();
    }

    /**
     * Deserialize COMP-3 (packed decimal) field to Long (no decimals).
     */
    public static Long deserializeComp3Long(byte[] data, int offset, int length, int totalDigits) {
        if (totalDigits <= 18) {
            return deserializeComp3ToLong(data, offset, length, totalDigits);
        }
        return deserializeComp3ToBigInteger(data, offset, length, totalDigits).longValue();
    }

    /**
     * Deserialize COMP-3 (packed decimal) field to BigInteger (no decimals).
     */
    public static BigInteger deserializeComp3BigInteger(byte[] data, int offset, int length, int totalDigits) {
        if (totalDigits <= 18) {
            return BigInteger.valueOf(deserializeComp3ToLong(data, offset, length, totalDigits));
        }
        return deserializeComp3ToBigInteger(data, offset, length, totalDigits);
    }

    /**
     * Fast-path helper to deserialize COMP-3 to long.
     * Accumulates digits directly into a long value using arithmetic,
     * avoiding StringBuilder and BigInteger allocations.
     * Only valid for totalDigits &lt;= 18 (value fits in a long).
     */
    private static long deserializeComp3ToLong(byte[] data, int offset, int length, int totalDigits) {
        long value = 0L;

        for (int i = 0; i < length - 1; i++) {
            int highNibble = (data[offset + i] >> 4) & 0x0F;
            int lowNibble = data[offset + i] & 0x0F;
            value = value * 100L + highNibble * 10L + lowNibble;
        }

        int lastDigit = (data[offset + length - 1] >> 4) & 0x0F;
        int sign = data[offset + length - 1] & 0x0F;

        if (totalDigits % 2 != 0) {
            value = value * 10L + lastDigit;
        }

        boolean isNegative = (sign == 0x0D || sign == 0x0B);
        return isNegative ? -value : value;
    }

    /**
     * Fallback helper to deserialize COMP-3 to BigInteger for totalDigits &gt; 18.
     */
    private static BigInteger deserializeComp3ToBigInteger(byte[] data, int offset, int length, int totalDigits) {
        StringBuilder digits = new StringBuilder(totalDigits);

        for (int i = 0; i < length - 1; i++) {
            int highNibble = (data[offset + i] >> 4) & 0x0F;
            int lowNibble = data[offset + i] & 0x0F;
            digits.append(highNibble).append(lowNibble);
        }

        int lastDigit = (data[offset + length - 1] >> 4) & 0x0F;
        int sign = data[offset + length - 1] & 0x0F;

        if (totalDigits % 2 != 0) {
            digits.append(lastDigit);
        }

        boolean isNegative = (sign == 0x0D || sign == 0x0B);
        BigInteger biValue = new BigInteger(digits.toString());
        if (isNegative) {
            biValue = biValue.negate();
        }

        return biValue;
    }

    /**
     * Deserialize ZONED-DECIMAL field to BigDecimal with decimals.
     */
    public static BigDecimal deserializeZonedDecimalBigDecimal(byte[] data, int offset, int length, int decimalDigits) {
        if (length <= 18) {
            long longValue = deserializeZonedDecimalToLong(data, offset, length);
            return BigDecimal.valueOf(longValue, decimalDigits).stripTrailingZeros();
        }
        BigInteger biValue = deserializeZonedDecimalToBigInteger(data, offset, length);
        BigDecimal bdValue = new BigDecimal(biValue);
        return bdValue.divide(BigDecimal.TEN.pow(decimalDigits), decimalDigits, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    /**
     * Deserialize ZONED-DECIMAL field to BigDecimal (no decimals).
     */
    public static BigDecimal deserializeZonedDecimalBigDecimalNoDecimals(byte[] data, int offset, int length) {
        if (length <= 18) {
            return BigDecimal.valueOf(deserializeZonedDecimalToLong(data, offset, length));
        }
        return new BigDecimal(deserializeZonedDecimalToBigInteger(data, offset, length));
    }

    /**
     * Fast-path helper to deserialize ZONED-DECIMAL to long.
     * Accumulates digits directly into a long value using arithmetic,
     * avoiding StringBuilder and BigInteger allocations.
     * Only valid for length &lt;= 18 (value fits in a long).
     */
    private static long deserializeZonedDecimalToLong(byte[] data, int offset, int length) {
        long value = 0L;

        for (int i = 0; i < length; i++) {
            int digit = data[offset + i] & 0x0F;
            value = value * 10L + digit;
        }

        int lastByte = data[offset + length - 1] & 0xF0;
        boolean isNegative = (lastByte == 0xD0 || lastByte == 0xB0);
        return isNegative ? -value : value;
    }

    /**
     * Fallback helper to deserialize ZONED-DECIMAL to BigInteger for length &gt; 18.
     */
    private static BigInteger deserializeZonedDecimalToBigInteger(byte[] data, int offset, int length) {
        StringBuilder digits = new StringBuilder(length);
        boolean isNegative = false;

        for (int i = 0; i < length; i++) {
            int digit = data[offset + i] & 0x0F;
            digits.append(digit);
            if (i == length - 1) {
                int zone = data[offset + i] & 0xF0;
                isNegative = (zone == 0xD0 || zone == 0xB0);
            }
        }

        BigInteger biValue = new BigInteger(digits.toString());
        if (isNegative) {
            biValue = biValue.negate();
        }

        return biValue;
    }
}

