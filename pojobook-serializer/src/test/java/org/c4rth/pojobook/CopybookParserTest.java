package org.c4rth.pojobook;

import org.c4rth.pojobook.parser.CopybookDefinition;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class CopybookParserTest {

    @Test
    void testParseSimpleCopybook() throws IOException {
        String copybook = """
                   01  CUSTOMER-RECORD.
                       05  CUSTOMER-ID         PIC 9(10).
                       05  CUSTOMER-NAME       PIC X(50).
                       05  CUSTOMER-BALANCE    PIC S9(7)V99 COMP-3.
                """;

        PojoBook pojoBook = new PojoBook();
        CopybookDefinition definition = pojoBook.parseCopybookString(copybook);

        assertNotNull(definition);
        assertEquals(3, definition.getFields().size());
    }

    @Test
    void testParseWithOccurs() throws IOException {
        String copybook = """
                   01  ORDER-RECORD.
                       05  ORDER-ID            PIC 9(8).
                       05  LINE-ITEMS          OCCURS 10 TIMES.
                           10  ITEM-ID         PIC 9(6).
                           10  QUANTITY        PIC 9(3).
                """;

        PojoBook pojoBook = new PojoBook();
        CopybookDefinition definition = pojoBook.parseCopybookString(copybook);

        assertNotNull(definition);
        assertTrue(definition.getFields().size() >= 2);
    }
}

