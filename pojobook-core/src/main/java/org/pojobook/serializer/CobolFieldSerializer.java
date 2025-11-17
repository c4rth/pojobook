package org.pojobook.serializer;

import org.pojobook.util.DisplayNumericUtil;
import org.pojobook.util.SignedNumericUtil;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * Helper class containing COBOL field serialization methods.
 * This class is used by both CobolSerializer (for runtime serialization)
 * and EmbeddedSerializationPojoGenerator (for code generation).
 */
public class CobolFieldSerializer {

    /**
     * Serialize a DISPLAY field.
     */
    public static byte[] serializeDisplay(Object value, int length, int decimalDigits, boolean isNumeric,
                                          boolean signed, boolean signSeparate, boolean isSignLeading,
                                          Charset charset) {
        if (signed && signSeparate && value instanceof Number) {
            return serializeDisplayWithSeparateSign((Number) value, length, decimalDigits, isSignLeading, charset);
        }

        if (signed && !signSeparate && value instanceof Number && isNumeric) {
            return serializeDisplayWithEmbeddedSign((Number) value, length, decimalDigits, charset);
        }

        if (!signed && decimalDigits > 0 && value instanceof Number && isNumeric) {
            return serializeDisplayWithImpliedDecimal((Number) value, length, decimalDigits, charset);
        }

        return serializeDisplayString(value, length, isNumeric, charset);
    }

    /**
     * Serialize DISPLAY string.
     */
    public static byte[] serializeDisplayString(Object value, int length, boolean isNumeric, Charset charset) {
        String strValue = value != null ? value.toString() : "";
        int strLen = strValue.length();

        // Fast path: if exact length, avoid allocations
        if (strLen == length) {
            return strValue.getBytes(charset);
        }

        if (strLen < length) {
            int padding = length - strLen;
            if (isNumeric) {
                // Numeric: pad left with zeros
                strValue = "0".repeat(padding) + strValue;
            } else {
                // Alphanumeric: pad right with spaces
                strValue = strValue + " ".repeat(padding);
            }
        } else {
            strValue = strValue.substring(0, length);
        }

        return strValue.getBytes(charset);
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
        return SignedNumericUtil.formatSignedDisplay(value, length, decimalDigits, charset);
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
        BigDecimal bdValue = value != null ? new BigDecimal(value.toString()) : BigDecimal.ZERO;

        if (decimalDigits > 0) {
            bdValue = bdValue.multiply(BigDecimal.TEN.pow(decimalDigits));
        }

        BigInteger biValue = bdValue.setScale(0, RoundingMode.HALF_UP).toBigInteger();
        String digits = String.format("%0" + totalDigits + "d", biValue.abs().longValue());

        int byteLength = (totalDigits / 2) + 1;
        byte[] packed = new byte[byteLength];

        int digitIndex = 0;
        for (int i = 0; i < byteLength - 1; i++) {
            int high = digits.charAt(digitIndex++) - '0';
            int low = digits.charAt(digitIndex++) - '0';
            packed[i] = (byte) ((high << 4) | low);
        }

        int lastDigit = (totalDigits % 2 != 0) ? (digits.charAt(digitIndex) - '0') : 0;
        int sign = biValue.signum() < 0 ? 0x0D : 0x0C;
        packed[byteLength - 1] = (byte) ((lastDigit << 4) | sign);

        return packed;
    }

    /**
     * Serialize ZONED-DECIMAL field.
     */
    public static byte[] serializeZonedDecimal(Object value, int totalDigits, int decimalDigits, boolean signed) {
        BigDecimal bdValue = value != null ? new BigDecimal(value.toString()) : BigDecimal.ZERO;

        if (decimalDigits > 0) {
            bdValue = bdValue.multiply(BigDecimal.TEN.pow(decimalDigits));
        }

        long longValue = bdValue.setScale(0, RoundingMode.HALF_UP).longValue();
        String digits = String.format("%0" + totalDigits + "d", Math.abs(longValue));
        byte[] zoned = new byte[totalDigits];

        for (int i = 0; i < totalDigits; i++) {
            byte digit = (byte) (digits.charAt(i) - '0');
            if (i == totalDigits - 1 && signed) {
                zoned[i] = (byte) ((longValue < 0 ? 0xD0 : 0xC0) | digit);
            } else {
                zoned[i] = (byte) (0xF0 | digit);
            }
        }

        return zoned;
    }

    // Helper methods

    private static BigDecimal toBigDecimal(Number value) {
        return value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
    }

    private static String formatUnscaledValueForDigits(BigDecimal absValue, int totalDigits) {
        return String.format("%0" + totalDigits + "d", absValue.unscaledValue());
    }

    private static byte getSignByte(BigDecimal decimal, Charset charset) {
        char signChar = decimal.signum() >= 0 ? '+' : '-';
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
}

