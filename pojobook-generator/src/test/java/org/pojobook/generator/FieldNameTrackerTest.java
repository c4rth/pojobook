package org.pojobook.generator;

import org.junit.jupiter.api.Test;
import org.pojobook.parser.FieldDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FieldNameTrackerTest {

    @Test
    void pushPopShouldIsolateNestedStateAndRestoreParentState() {
        FieldNameTracker tracker = new FieldNameTracker();

        assertEquals("customerId", tracker.toUniqueFieldName(field("CUSTOMER-ID", 1)));
        assertEquals("customerId2", tracker.toUniqueFieldName(field("CUSTOMER-ID", 2)));

        tracker.push();
        assertEquals("customerId", tracker.toUniqueFieldName(field("CUSTOMER-ID", 3)));
        tracker.pop();

        assertEquals("customerId3", tracker.toUniqueFieldName(field("CUSTOMER-ID", 4)));
    }

    @Test
    void nestedPushPopShouldRestoreStatesInLifoOrder() {
        FieldNameTracker tracker = new FieldNameTracker();

        assertEquals("orderId", tracker.toUniqueFieldName(field("ORDER-ID", 1)));

        tracker.push();
        assertEquals("orderId", tracker.toUniqueFieldName(field("ORDER-ID", 2)));

        tracker.push();
        assertEquals("orderId", tracker.toUniqueFieldName(field("ORDER-ID", 3)));
        tracker.pop();

        assertEquals("orderId2", tracker.toUniqueFieldName(field("ORDER-ID", 4)));
        tracker.pop();

        assertEquals("orderId2", tracker.toUniqueFieldName(field("ORDER-ID", 5)));
    }

    @Test
    void resetShouldClearCurrentAndNestedHistoryState() {
        FieldNameTracker tracker = new FieldNameTracker();

        assertEquals("lineItem", tracker.toUniqueFieldName(field("LINE-ITEM", 1)));
        tracker.push();
        assertEquals("lineItem", tracker.toUniqueFieldName(field("LINE-ITEM", 2)));

        tracker.reset();
        tracker.pop();

        assertEquals("lineItem", tracker.toUniqueFieldName(field("LINE-ITEM", 3)));
    }

    private static FieldDefinition field(String name, int lineNumber) {
        FieldDefinition field = new FieldDefinition();
        field.setName(name);
        field.setLineNumber(lineNumber);
        return field;
    }
}

