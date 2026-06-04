package org.pojobook.generator.standalone;

import org.junit.jupiter.api.Test;
import org.pojobook.CobolDataType;
import org.pojobook.generator.FieldNameTracker;
import org.pojobook.parser.FieldDefinition;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OffsetCalculatorTest {

    @Test
    void shouldUseCharScanFallbackWhenDisplayDigitsAreMissing() throws Exception {
        FieldDefinition field = new FieldDefinition();
        field.setType(CobolDataType.DISPLAY);
        setPrivateField(field, "picture", "A-X9(3).B");
        setPrivateField(field, "integerDigits", 0);
        setPrivateField(field, "decimalDigits", 0);

        OffsetCalculator calculator = new OffsetCalculator(new FieldNameTracker());

        assertEquals(2, calculator.calculateFieldSize(field));
    }

    @Test
    void shouldPreferParsedDigitsOverPictureFallback() throws Exception {
        FieldDefinition field = new FieldDefinition();
        field.setType(CobolDataType.DISPLAY);
        setPrivateField(field, "picture", "X9X9");
        setPrivateField(field, "integerDigits", 5);
        setPrivateField(field, "decimalDigits", 2);

        OffsetCalculator calculator = new OffsetCalculator(new FieldNameTracker());

        assertEquals(7, calculator.calculateFieldSize(field));
    }

    private static void setPrivateField(FieldDefinition field, String name, Object value) throws Exception {
        Field privateField = FieldDefinition.class.getDeclaredField(name);
        privateField.setAccessible(true);
        privateField.set(field, value);
    }
}

