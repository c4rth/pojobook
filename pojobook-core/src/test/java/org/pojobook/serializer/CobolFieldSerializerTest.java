package org.pojobook.serializer;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CobolFieldSerializerTest {

    @Test
    public void testObjectToBigDecimalConversion() throws Exception {
        // We will call serializeComp3 which uses objectToBigDecimal internally
        // Ensure that primitive wrappers are handled correctly without throwing exceptions.
        byte[] r1 = CobolFieldSerializer.serializeComp3((byte) 12, 3, 0);
        assertNotNull(r1);

        byte[] r2 = CobolFieldSerializer.serializeComp3(new BigInteger("1234567890123456789"), 19, 0);
        assertNotNull(r2);

        byte[] r3 = CobolFieldSerializer.serializeComp3(123.45d, 5, 2);
        assertNotNull(r3);

        byte[] r4 = CobolFieldSerializer.serializeComp3(12.34f, 4, 2);
        assertNotNull(r4);
    }

    @Test
    public void testComp3OverflowAndLargeNumbers() {
        // Fits in 18 digits or fewer (uses long value path)
        byte[] pr1 = CobolFieldSerializer.serializeComp3(new BigInteger("999999999999999999"), 18, 0);
        assertNotNull(pr1);

        // 19 digits (greater than 18 digits, uses BigInteger path)
        byte[] pr2 = CobolFieldSerializer.serializeComp3(new BigInteger("9999999999999999999"), 19, 0);
        assertNotNull(pr2);

        // Actual overflow - should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> CobolFieldSerializer.serializeComp3(new BigInteger("1000"), 3, 0));

        assertThrows(IllegalArgumentException.class, () -> CobolFieldSerializer.serializeComp3(new BigInteger("10000000000000000000"), 19, 0));
    }

    @Test
    public void testZonedDecimalOverflowAndLargeNumbers() {
        // Fits in 18 digits or fewer
        byte[] zr1 = CobolFieldSerializer.serializeZonedDecimal(new BigInteger("999999999999999999"), 18, 0, false);
        assertNotNull(zr1);

        // Greater than 18 digits
        byte[] zr2 = CobolFieldSerializer.serializeZonedDecimal(new BigInteger("9999999999999999999"), 19, 0, false);
        assertNotNull(zr2);

        // Actual overflow
        assertThrows(IllegalArgumentException.class, () -> CobolFieldSerializer.serializeZonedDecimal(new BigInteger("1000"), 3, 0, false));

        assertThrows(IllegalArgumentException.class, () -> CobolFieldSerializer.serializeZonedDecimal(new BigInteger("10000000000000000000"), 19, 0, false));
    }

    @Test
    public void testDirectWriteOverflowChecks() {
        byte[] buffer = new byte[20];
        // Comp3 Direct
        assertThrows(IllegalArgumentException.class, () -> CobolFieldSerializer.serializeComp3Direct(buffer, 0, new BigInteger("100"), 2, 0));

        // Zoned Direct
        assertThrows(IllegalArgumentException.class, () -> CobolFieldSerializer.serializeZonedDecimalDirect(buffer, 0, new BigInteger("123"), 2, 0, false));
    }
}

