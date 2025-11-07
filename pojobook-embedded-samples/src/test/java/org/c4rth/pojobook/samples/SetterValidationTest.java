package org.c4rth.pojobook.samples;

import org.c4rth.pojobook.samples.generated.Stru04;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that setter validation works correctly for String fields.
 * Embedded POJOs include the same validation as annotation-based POJOs.
 */
class SetterValidationTest {

    private final Charset charset = Charset.forName("CP1047");

    @Test
    void testStringFieldValidation() {
        Stru04 stru04 = new Stru04();
        Stru04.ComArray1 comArray1 = new Stru04.ComArray1();
        Stru04.ComArray1.ComArray2 comArray2 = new Stru04.ComArray1.ComArray2();

        // COM-ITEM4 has PIC X (length 1) - should accept 1 character
        assertDoesNotThrow(() -> comArray2.setComItem4("A"));

        // Should throw exception for string longer than 1 character
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> comArray2.setComItem4("AB")
        );
        assertTrue(exception.getMessage().contains("COM-ITEM4"));
        assertTrue(exception.getMessage().contains("maximum length of 1"));

        // Should accept null
        assertDoesNotThrow(() -> comArray2.setComItem4(null));

        // Should accept empty string
        assertDoesNotThrow(() -> comArray2.setComItem4(""));
    }

    @Test
    void testArrayElementValidation() {
        Stru04 stru04 = new Stru04();

        // Test that we can set the array
        stru04.getComArray1()[0].getComArray2()[0].setComItem4("X");
        assertEquals("X", stru04.getComArray1()[0].getComArray2()[0].getComItem4());

        // Test that validation works for array elements
        assertThrows(
                IllegalArgumentException.class,
                () -> stru04.getComArray1()[0].getComArray2()[0].setComItem4("TOO_LONG")
        );
    }

    @Test
    void testStringArrayValidation() {
        Stru04.ComArray1.ComArray2 comArray2 = new Stru04.ComArray1.ComArray2();

        // Valid: all elements have length <= 1
        String[] validArray = {"A", "B", "C", "D", "E"};
        assertDoesNotThrow(() -> comArray2.setComArray3(validArray));

        // Valid: some elements are null
        String[] nullArray = {"A", null, "C", null, "E"};
        assertDoesNotThrow(() -> comArray2.setComArray3(nullArray));

        // Valid: empty strings
        String[] emptyArray = {"", "", "", "", ""};
        assertDoesNotThrow(() -> comArray2.setComArray3(emptyArray));

        // Invalid: one element is too long
        String[] invalidArray = {"A", "B", "TOO_LONG", "D", "E"};
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> comArray2.setComArray3(invalidArray)
        );
        assertTrue(exception.getMessage().contains("COM-ARRAY3"));
        assertTrue(exception.getMessage().contains("[2]")); // Index of invalid element
        assertTrue(exception.getMessage().contains("maximum length of 1"));

        // Invalid: first element is too long
        String[] invalidFirst = {"TOO_LONG", "B", "C", "D", "E"};
        exception = assertThrows(
                IllegalArgumentException.class,
                () -> comArray2.setComArray3(invalidFirst)
        );
        assertTrue(exception.getMessage().contains("[0]")); // Index 0

        // Valid: null array
        assertDoesNotThrow(() -> comArray2.setComArray3(null));
    }
}

