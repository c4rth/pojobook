package org.pojobook.serializer;

import org.pojobook.util.CharsetMode;
import org.pojobook.util.DisplayNumericUtil;
import org.pojobook.util.SignedNumericUtil;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * Helper class containing COBOL field serialization methods.
 * This class is used by both CobolSerializer (for runtime serialization)
 * and EmbeddedSerializationPojoGenerator (for code generation).
 */
public class CobolFieldSerializer {

    private static final BigInteger[] BI_MAX_VALUES = new BigInteger[100];
    static {
        BigInteger val = BigInteger.ZERO;
        for (int i = 1; i < BI_MAX_VALUES.length; i++) {
            val = val.multiply(BigInteger.TEN).add(BigInteger.valueOf(9));
            BI_MAX_VALUES[i] = val;
        }
    }

    private static void checkDigitLimit(BigInteger absValue, int totalDigits) {
        if (totalDigits < BI_MAX_VALUES.length) {
            if (absValue.compareTo(BI_MAX_VALUES[totalDigits]) > 0) {
                throw new IllegalArgumentException("Numeric value " + absValue + " overflows representation of " + totalDigits + " digits");
            }
        } else {
            BigInteger maxLimit = BigInteger.TEN.pow(totalDigits).subtract(BigInteger.ONE);
            if (absValue.compareTo(maxLimit) > 0) {
                throw new IllegalArgumentException("Numeric value " + absValue + " overflows representation of " + totalDigits + " digits");
            }
        }
    }

    /**
     * Serialize DISPLAY string.
     * <p>
     * For known single-byte charsets (ASCII/EBCDIC), builds the result directly
     * in a {@code byte[]} with no intermediate String allocations for padding.
     * For unknown charsets, uses a {@code char[]} to avoid string concatenation.
     */
    public static byte[] serializeDisplayString(Object value, int length, boolean isNumeric, Charset charset) {
        String strValue = value != null ? value.toString() : "";
        int strLen = strValue.length();

        // Fast path: if exact length, avoid allocations
        if (strLen == length) {
            return strValue.getBytes(charset);
        }

        int effectiveLen = Math.min(strLen, length);
        int mode = CharsetMode.detect(charset);

        if (mode != CharsetMode.UNKNOWN) {
            byte[] result = new byte[length];
            if (isNumeric) {
                int padding = length - effectiveLen;
                if (padding > 0) {
                    byte zeroByte = CharsetMode.zeroByte(mode);
                    Arrays.fill(result, 0, padding, zeroByte);
                }
                byte digitBase = CharsetMode.digitBase(mode);
                for (int i = 0; i < effectiveLen; i++) {
                    char c = strValue.charAt(i);
                    if (c >= '0' && c <= '9') {
                        result[padding + i] = (byte) (c - '0' + digitBase);
                    } else {
                        // Non-digit fallback: encode via charset
                        byte[] valueBytes = strValue.substring(0, effectiveLen).getBytes(charset);
                        System.arraycopy(valueBytes, 0, result, padding,
                                Math.min(valueBytes.length, effectiveLen));
                        return result;
                    }
                }
            } else {
                if (effectiveLen > 0) {
                    byte[] valueBytes = (effectiveLen < strLen)
                            ? strValue.substring(0, effectiveLen).getBytes(charset)
                            : strValue.getBytes(charset);
                    System.arraycopy(valueBytes, 0, result, 0, Math.min(valueBytes.length, effectiveLen));
                }
                int remaining = length - effectiveLen;
                if (remaining > 0) {
                    byte spaceByte = CharsetMode.spaceByte(mode);
                    Arrays.fill(result, effectiveLen, length, spaceByte);
                }
            }
            return result;
        }

        // Unknown charset fallback: build in char[] to avoid string concatenation
        char[] chars = new char[length];
        if (strLen < length) {
            if (isNumeric) {
                int padding = length - strLen;
                Arrays.fill(chars, 0, padding, '0');
                strValue.getChars(0, strLen, chars, padding);
            } else {
                strValue.getChars(0, strLen, chars, 0);
                Arrays.fill(chars, strLen, length, ' ');
            }
        } else {
            strValue.getChars(0, length, chars, 0);
        }
        return new String(chars).getBytes(charset);
    }

    /**
     * Serialize DISPLAY field with implied decimal (V in PIC clause).
     */
    public static byte[] serializeDisplayWithImpliedDecimal(Number value, int length, int decimalDigits, Charset charset) {
        return DisplayNumericUtil.formatUnsignedWithImpliedDecimal(value, length, decimalDigits, charset);
    }

    /**
     * Serialize DISPLAY field with embedded sign (overpunch notation).
     */
    public static byte[] serializeDisplayWithEmbeddedSign(Number value, int length, int decimalDigits, Charset charset) {
        String formatted = SignedNumericUtil.formatSignedNumeric(value, length, decimalDigits);
        return formatted.getBytes(charset);
    }

    /**
     * Serialize DISPLAY field with SIGN LEADING/TRAILING SEPARATE.
     */
    public static byte[] serializeDisplayWithSeparateSign(Number value, int length, int decimalDigits,
                                                          boolean isSignLeading, Charset charset) {
        BigDecimal decimal = toBigDecimal(value);
        if (decimalDigits > 0) {
            decimal = decimal.setScale(decimalDigits, RoundingMode.HALF_UP);
        }

        // length here is just the total digits (integerDigits + decimalDigits), not including sign
        String unscaledValue = formatUnscaledValueForDigits(decimal.abs(), length);
        byte signByte = getSignByte(decimal, charset);
        byte[] digitBytes = unscaledValue.getBytes(charset);

        return buildSignedResult(digitBytes, signByte, isSignLeading);
    }

    /**
     * Serialize COMP/BINARY field.
     */
    public static byte[] serializeComp(Object value, int totalDigits) {
        long longValue = value != null ? ((Number) value).longValue() : 0L;

        // Inline ByteBuffer operations for performance
        if (totalDigits <= 4) {
            return ByteBuffer.allocate(2).putShort((short) longValue).array();
        }
        if (totalDigits <= 9) {
            return ByteBuffer.allocate(4).putInt((int) longValue).array();
        }
        return ByteBuffer.allocate(8).putLong(longValue).array();
    }

    /**
     * Serialize COMP-1 (float) field.
     */
    public static byte[] serializeComp1(Object value) {
        float floatValue = value != null ? ((Number) value).floatValue() : 0.0f;
        return ByteBuffer.allocate(4).putFloat(floatValue).array();
    }

    /**
     * Serialize COMP-2 (double) field.
     */
    public static byte[] serializeComp2(Object value) {
        double doubleValue = value != null ? ((Number) value).doubleValue() : 0.0;
        return ByteBuffer.allocate(8).putDouble(doubleValue).array();
    }

    /**
     * Serialize COMP-3 (packed decimal) field.
     */
    public static byte[] serializeComp3(Object value, int totalDigits, int decimalDigits) {
        BigDecimal bdValue = value != null ? objectToBigDecimal(value) : BigDecimal.ZERO;

        if (decimalDigits > 0) {
            bdValue = bdValue.multiply(BigDecimal.TEN.pow(decimalDigits));
        }

        BigInteger biValue = bdValue.setScale(0, RoundingMode.HALF_UP).toBigInteger();
        BigInteger absValue = biValue.abs();
        checkDigitLimit(absValue, totalDigits);

        char[] digits;
        if (totalDigits <= 18) {
            digits = toZeroPaddedDigits(absValue.longValue(), totalDigits);
        } else {
            digits = toZeroPaddedDigits(absValue, totalDigits);
        }

        int byteLength = (totalDigits / 2) + 1;
        byte[] packed = new byte[byteLength];

        int digitIndex = 0;
        for (int i = 0; i < byteLength - 1; i++) {
            int high = digits[digitIndex++] - '0';
            int low = digits[digitIndex++] - '0';
            packed[i] = (byte) ((high << 4) | low);
        }

        int lastDigit = (totalDigits % 2 != 0) ? (digits[digitIndex] - '0') : 0;
        int sign = biValue.signum() < 0 ? 0x0D : 0x0C;
        packed[byteLength - 1] = (byte) ((lastDigit << 4) | sign);

        return packed;
    }

    /**
     * Serialize ZONED-DECIMAL field.
     */
    public static byte[] serializeZonedDecimal(Object value, int totalDigits, int decimalDigits, boolean signed) {
        BigDecimal bdValue = value != null ? objectToBigDecimal(value) : BigDecimal.ZERO;

        if (decimalDigits > 0) {
            bdValue = bdValue.multiply(BigDecimal.TEN.pow(decimalDigits));
        }

        BigInteger biValue = bdValue.setScale(0, RoundingMode.HALF_UP).toBigInteger();
        BigInteger absValue = biValue.abs();
        checkDigitLimit(absValue, totalDigits);

        char[] digits;
        if (totalDigits <= 18) {
            digits = toZeroPaddedDigits(absValue.longValue(), totalDigits);
        } else {
            digits = toZeroPaddedDigits(absValue, totalDigits);
        }
        byte[] zoned = new byte[totalDigits];

        for (int i = 0; i < totalDigits; i++) {
            byte digit = (byte) (digits[i] - '0');
            if (i == totalDigits - 1 && signed) {
                zoned[i] = (byte) ((biValue.signum() < 0 ? 0xD0 : 0xC0) | digit);
            } else {
                zoned[i] = (byte) (0xF0 | digit);
            }
        }

        return zoned;
    }

    // Helper methods

    private static BigDecimal toBigDecimal(Number value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Long l) {
            return BigDecimal.valueOf(l);
        }
        if (value instanceof Integer i) {
            return BigDecimal.valueOf(i);
        }
        if (value instanceof Short s) {
            return BigDecimal.valueOf(s);
        }
        if (value instanceof Byte b) {
            return BigDecimal.valueOf(b);
        }
        if (value instanceof BigInteger bi) {
            return new BigDecimal(bi);
        }
        if (value instanceof Double d) {
            return BigDecimal.valueOf(d);
        }
        if (value instanceof Float f) {
            return BigDecimal.valueOf(f);
        }
        return new BigDecimal(value.toString());
    }

    /**
     * Convert an Object value to BigDecimal, avoiding the toString() roundtrip
     * when the value is already a BigDecimal or a primitive-wrapper Number.
     */
    private static BigDecimal objectToBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Long l) {
            return BigDecimal.valueOf(l);
        }
        if (value instanceof Integer i) {
            return BigDecimal.valueOf(i);
        }
        if (value instanceof Short s) {
            return BigDecimal.valueOf(s);
        }
        if (value instanceof Byte b) {
            return BigDecimal.valueOf(b);
        }
        if (value instanceof BigInteger bi) {
            return new BigDecimal(bi);
        }
        if (value instanceof Double d) {
            return BigDecimal.valueOf(d);
        }
        if (value instanceof Float f) {
            return BigDecimal.valueOf(f);
        }
        return new BigDecimal(value.toString());
    }

    private static String formatUnscaledValueForDigits(BigDecimal absValue, int totalDigits) {
        String digits = absValue.unscaledValue().toString();
        int padding = totalDigits - digits.length();
        if (padding <= 0) {
            return digits;
        }
        char[] result = new char[totalDigits];
        int i = 0;
        for (; i < padding; i++) {
            result[i] = '0';
        }
        digits.getChars(0, digits.length(), result, i);
        return new String(result);
    }

    /**
     * Convert a non-negative long value into a zero-padded char array of the given length.
     * Uses modular arithmetic instead of {@code String.format}, avoiding format-string
     * parsing, varargs boxing, and intermediate String allocation.
     */
    private static char[] toZeroPaddedDigits(long absValue, int length) {
        char[] digits = new char[length];
        for (int i = length - 1; i >= 0; i--) {
            digits[i] = (char) ('0' + (int) (absValue % 10));
            absValue /= 10;
        }
        return digits;
    }

    /**
     * Convert a non-negative BigInteger value into a zero-padded char array of the given length.
     */
    private static char[] toZeroPaddedDigits(BigInteger absValue, int length) {
        char[] digits = new char[length];
        String str = absValue.toString();
        int strLen = str.length();
        int padding = length - strLen;
        if (padding > 0) {
            Arrays.fill(digits, 0, padding, '0');
            str.getChars(0, strLen, digits, padding);
        } else {
            str.getChars(strLen - length, strLen, digits, 0);
        }
        return digits;
    }

    // Sign byte constants for known charsets
    private static final byte ASCII_PLUS  = 0x2B;  // '+'
    private static final byte ASCII_MINUS = 0x2D;  // '-'
    private static final byte EBCDIC_PLUS  = 0x4E;  // '+' in EBCDIC
    private static final byte EBCDIC_MINUS = 0x60;  // '-' in EBCDIC

    private static byte getSignByte(BigDecimal decimal, Charset charset) {
        boolean isNegative = decimal.signum() < 0;
        int mode = CharsetMode.detect(charset);
        if (mode == CharsetMode.ASCII) {
            return isNegative ? ASCII_MINUS : ASCII_PLUS;
        }
        if (mode == CharsetMode.EBCDIC) {
            return isNegative ? EBCDIC_MINUS : EBCDIC_PLUS;
        }
        // Unknown charset fallback
        char signChar = isNegative ? '-' : '+';
        return String.valueOf(signChar).getBytes(charset)[0];
    }

    private static byte[] buildSignedResult(byte[] digitBytes, byte signByte, boolean isSignLeading) {
        byte[] result = new byte[digitBytes.length + 1];

        if (isSignLeading) {
            result[0] = signByte;
            System.arraycopy(digitBytes, 0, result, 1, digitBytes.length);
        } else {
            System.arraycopy(digitBytes, 0, result, 0, digitBytes.length);
            result[digitBytes.length] = signByte;
        }

        return result;
    }

    // ========================================================================
    // Direct-write methods for optimized serialization (zero-allocation)
    // ========================================================================

    /**
     * Serialize DISPLAY string directly to buffer at specified offset.
     * <p>
     * For known single-byte charsets (EBCDIC / ASCII), padding bytes and numeric
     * digit bytes are written directly into the destination buffer without any
     * intermediate String or byte-array allocation.  Alphanumeric values still
     * require a {@code getBytes} call for the value portion, but the padding is
     * written allocation-free.
     * <p>
     * For unknown / multi-byte charsets the method falls back to the standard
     * String-based encoding path.
     */
    public static void serializeDisplayStringDirect(byte[] buffer, int offset, Object value, int length,
                                                    boolean isNumeric, Charset charset) {
        String strValue = value != null ? value.toString() : "";
        int strLen = strValue.length();
        int effectiveLen = Math.min(strLen, length);

        int mode = CharsetMode.detect(charset);
        if (mode != CharsetMode.UNKNOWN) {
            if (isNumeric) {
                // Numeric: pad left with '0', then write digits directly – zero allocations
                int padding = length - effectiveLen;
                byte digitBase = CharsetMode.digitBase(mode);

                // Leading zero-pad bytes
                if (padding > 0) {
                    byte zeroByte = CharsetMode.zeroByte(mode);
                    Arrays.fill(buffer, offset, offset + padding, zeroByte);
                }

                // Encode each digit char directly
                int pos = offset + padding;
                for (int i = 0; i < effectiveLen; i++) {
                    char c = strValue.charAt(i);
                    if (c >= '0' && c <= '9') {
                        buffer[pos++] = (byte) (c - '0' + digitBase);
                    } else {
                        // Non-digit in supposedly numeric field – fall back to charset encoding
                        byte[] valueBytes = strValue.substring(0, effectiveLen).getBytes(charset);
                        System.arraycopy(valueBytes, 0, buffer, offset + padding,
                                Math.min(valueBytes.length, effectiveLen));
                        return;
                    }
                }
            } else {
                // Alphanumeric: write value bytes, then pad right with spaces
                if (effectiveLen > 0) {
                    if (effectiveLen < strLen) {
                        // Need to truncate
                        byte[] valueBytes = strValue.substring(0, effectiveLen).getBytes(charset);
                        System.arraycopy(valueBytes, 0, buffer, offset, Math.min(valueBytes.length, effectiveLen));
                    } else {
                        byte[] valueBytes = strValue.getBytes(charset);
                        System.arraycopy(valueBytes, 0, buffer, offset, Math.min(valueBytes.length, effectiveLen));
                    }
                }

                // Trailing space-pad bytes – no String allocation
                int remaining = length - effectiveLen;
                if (remaining > 0) {
                    byte spaceByte = CharsetMode.spaceByte(mode);
                    Arrays.fill(buffer, offset + effectiveLen, offset + length, spaceByte);
                }
            }
        } else {
            // Unknown / multi-byte charset – build in char[] to avoid string concatenation
            char[] chars = new char[length];
            if (strLen < length) {
                if (isNumeric) {
                    int padding = length - strLen;
                    Arrays.fill(chars, 0, padding, '0');
                    strValue.getChars(0, strLen, chars, padding);
                } else {
                    strValue.getChars(0, strLen, chars, 0);
                    Arrays.fill(chars, strLen, length, ' ');
                }
            } else if (strLen > length) {
                strValue.getChars(0, length, chars, 0);
            } else {
                strValue.getChars(0, length, chars, 0);
            }

            byte[] bytes = new String(chars).getBytes(charset);
            System.arraycopy(bytes, 0, buffer, offset, Math.min(bytes.length, length));
        }
    }

    /**
     * Serialize DISPLAY field with implied decimal directly to buffer.
     * Delegates to the direct-write overload, avoiding intermediate byte[] allocation
     * for known single-byte charsets.
     */
    public static void serializeDisplayWithImpliedDecimalDirect(byte[] buffer, int offset, Number value,
                                                                int length, int decimalDigits, Charset charset) {
        DisplayNumericUtil.formatUnsignedWithImpliedDecimalDirect(buffer, offset, value, length, decimalDigits, charset);
    }

    /**
     * Serialize DISPLAY field with embedded sign directly to buffer.
     * Delegates to the direct-write overload, avoiding intermediate String and byte[]
     * allocation for known single-byte charsets.
     */
    public static void serializeDisplayWithEmbeddedSignDirect(byte[] buffer, int offset, Number value,
                                                              int length, int decimalDigits, Charset charset) {
        SignedNumericUtil.formatSignedNumericDirect(buffer, offset, value, length, decimalDigits, charset);
    }

    /**
     * Serialize DISPLAY field with SIGN LEADING/TRAILING SEPARATE directly to buffer.
     */
    public static void serializeDisplayWithSeparateSignDirect(byte[] buffer, int offset, Number value,
                                                              int length, int decimalDigits,
                                                              boolean isSignLeading, Charset charset) {
        BigDecimal decimal = toBigDecimal(value);
        if (decimalDigits > 0) {
            decimal = decimal.setScale(decimalDigits, RoundingMode.HALF_UP);
        }

        String unscaledValue = formatUnscaledValueForDigits(decimal.abs(), length);
        byte signByte = getSignByte(decimal, charset);
        byte[] digitBytes = unscaledValue.getBytes(charset);

        if (isSignLeading) {
            buffer[offset] = signByte;
            System.arraycopy(digitBytes, 0, buffer, offset + 1, digitBytes.length);
        } else {
            System.arraycopy(digitBytes, 0, buffer, offset, digitBytes.length);
            buffer[offset + digitBytes.length] = signByte;
        }
    }

    /**
     * Serialize COMP/BINARY field directly to buffer.
     */
    public static void serializeCompDirect(byte[] buffer, int offset, Object value, int totalDigits) {
        long longValue = value != null ? ((Number) value).longValue() : 0L;

        if (totalDigits <= 4) {
            short shortValue = (short) longValue;
            buffer[offset] = (byte) (shortValue >> 8);
            buffer[offset + 1] = (byte) shortValue;
        } else if (totalDigits <= 9) {
            int intValue = (int) longValue;
            buffer[offset] = (byte) (intValue >> 24);
            buffer[offset + 1] = (byte) (intValue >> 16);
            buffer[offset + 2] = (byte) (intValue >> 8);
            buffer[offset + 3] = (byte) intValue;
        } else {
            buffer[offset] = (byte) (longValue >> 56);
            buffer[offset + 1] = (byte) (longValue >> 48);
            buffer[offset + 2] = (byte) (longValue >> 40);
            buffer[offset + 3] = (byte) (longValue >> 32);
            buffer[offset + 4] = (byte) (longValue >> 24);
            buffer[offset + 5] = (byte) (longValue >> 16);
            buffer[offset + 6] = (byte) (longValue >> 8);
            buffer[offset + 7] = (byte) longValue;
        }
    }

    /**
     * Serialize COMP-1 (float) field directly to buffer.
     */
    public static void serializeComp1Direct(byte[] buffer, int offset, Object value) {
        float floatValue = value != null ? ((Number) value).floatValue() : 0.0f;
        int intBits = Float.floatToRawIntBits(floatValue);
        buffer[offset] = (byte) (intBits >> 24);
        buffer[offset + 1] = (byte) (intBits >> 16);
        buffer[offset + 2] = (byte) (intBits >> 8);
        buffer[offset + 3] = (byte) intBits;
    }

    /**
     * Serialize COMP-2 (double) field directly to buffer.
     */
    public static void serializeComp2Direct(byte[] buffer, int offset, Object value) {
        double doubleValue = value != null ? ((Number) value).doubleValue() : 0.0;
        long longBits = Double.doubleToRawLongBits(doubleValue);
        buffer[offset] = (byte) (longBits >> 56);
        buffer[offset + 1] = (byte) (longBits >> 48);
        buffer[offset + 2] = (byte) (longBits >> 40);
        buffer[offset + 3] = (byte) (longBits >> 32);
        buffer[offset + 4] = (byte) (longBits >> 24);
        buffer[offset + 5] = (byte) (longBits >> 16);
        buffer[offset + 6] = (byte) (longBits >> 8);
        buffer[offset + 7] = (byte) longBits;
    }

    /**
     * Serialize COMP-3 (packed decimal) field directly to buffer.
     */
    public static void serializeComp3Direct(byte[] buffer, int offset, Object value, int totalDigits, int decimalDigits) {
        BigDecimal bdValue = value != null ? objectToBigDecimal(value) : BigDecimal.ZERO;

        if (decimalDigits > 0) {
            bdValue = bdValue.multiply(BigDecimal.TEN.pow(decimalDigits));
        }

        packComp3IntoBuffer(buffer, offset, bdValue, totalDigits);
    }

    /**
     * Serialize COMP-3 (packed decimal) field directly to buffer using a pre-computed scale factor.
     * This avoids recomputing {@code BigDecimal.TEN.pow(decimalDigits)} on every call.
     *
     * @param scaleFactor pre-computed {@code BigDecimal.TEN.pow(decimalDigits)}, or {@code null} if no scaling is needed
     */
    public static void serializeComp3Direct(byte[] buffer, int offset, Object value, int totalDigits, BigDecimal scaleFactor) {
        BigDecimal bdValue = value != null ? objectToBigDecimal(value) : BigDecimal.ZERO;

        if (scaleFactor != null) {
            bdValue = bdValue.multiply(scaleFactor);
        }

        packComp3IntoBuffer(buffer, offset, bdValue, totalDigits);
    }

    private static void packComp3IntoBuffer(byte[] buffer, int offset, BigDecimal bdValue, int totalDigits) {
        BigInteger biValue = bdValue.setScale(0, RoundingMode.HALF_UP).toBigInteger();
        BigInteger absValue = biValue.abs();
        checkDigitLimit(absValue, totalDigits);

        char[] digits;
        if (totalDigits <= 18) {
            digits = toZeroPaddedDigits(absValue.longValue(), totalDigits);
        } else {
            digits = toZeroPaddedDigits(absValue, totalDigits);
        }

        int byteLength = (totalDigits / 2) + 1;
        int digitIndex = 0;

        for (int i = 0; i < byteLength - 1; i++) {
            int high = digits[digitIndex++] - '0';
            int low = digits[digitIndex++] - '0';
            buffer[offset + i] = (byte) ((high << 4) | low);
        }

        int lastDigit = (totalDigits % 2 != 0) ? (digits[digitIndex] - '0') : 0;
        int sign = biValue.signum() < 0 ? 0x0D : 0x0C;
        buffer[offset + byteLength - 1] = (byte) ((lastDigit << 4) | sign);
    }

    /**
     * Serialize ZONED-DECIMAL field directly to buffer.
     */
    public static void serializeZonedDecimalDirect(byte[] buffer, int offset, Object value,
                                                    int totalDigits, int decimalDigits, boolean signed) {
        BigDecimal bdValue = value != null ? objectToBigDecimal(value) : BigDecimal.ZERO;

        if (decimalDigits > 0) {
            bdValue = bdValue.multiply(BigDecimal.TEN.pow(decimalDigits));
        }

        writeZonedDecimalToBuffer(buffer, offset, bdValue, totalDigits, signed);
    }

    /**
     * Serialize ZONED-DECIMAL field directly to buffer using a pre-computed scale factor.
     * This avoids recomputing {@code BigDecimal.TEN.pow(decimalDigits)} on every call.
     *
     * @param scaleFactor pre-computed {@code BigDecimal.TEN.pow(decimalDigits)}, or {@code null} if no scaling is needed
     */
    public static void serializeZonedDecimalDirect(byte[] buffer, int offset, Object value,
                                                    int totalDigits, BigDecimal scaleFactor, boolean signed) {
        BigDecimal bdValue = value != null ? objectToBigDecimal(value) : BigDecimal.ZERO;

        if (scaleFactor != null) {
            bdValue = bdValue.multiply(scaleFactor);
        }

        writeZonedDecimalToBuffer(buffer, offset, bdValue, totalDigits, signed);
    }

    private static void writeZonedDecimalToBuffer(byte[] buffer, int offset, BigDecimal bdValue,
                                                   int totalDigits, boolean signed) {
        BigInteger biValue = bdValue.setScale(0, RoundingMode.HALF_UP).toBigInteger();
        BigInteger absValue = biValue.abs();
        checkDigitLimit(absValue, totalDigits);

        char[] digits;
        if (totalDigits <= 18) {
            digits = toZeroPaddedDigits(absValue.longValue(), totalDigits);
        } else {
            digits = toZeroPaddedDigits(absValue, totalDigits);
        }

        for (int i = 0; i < totalDigits; i++) {
            byte digit = (byte) (digits[i] - '0');
            if (i == totalDigits - 1 && signed) {
                buffer[offset + i] = (byte) ((biValue.signum() < 0 ? 0xD0 : 0xC0) | digit);
            } else {
                buffer[offset + i] = (byte) (0xF0 | digit);
            }
        }
    }
}
