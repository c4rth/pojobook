package org.pojobook.generator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NamingUtilsTest {

    @Test
    void testToCamelCase() {
        assertEquals("customerId", NamingUtils.toCamelCase("CUSTOMER-ID"));
        assertEquals("customerName", NamingUtils.toCamelCase("CUSTOMER_NAME"));
        assertEquals("employeeRecord", NamingUtils.toCamelCase("EMPLOYEE-RECORD"));
        assertEquals("orderDetails", NamingUtils.toCamelCase("ORDER_DETAILS"));
        assertEquals("customerId", NamingUtils.toCamelCase("CUSTOMER_ID"));
        assertEquals("orderDetails", NamingUtils.toCamelCase("ORDER_DETAILS"));
        assertEquals("customerId", NamingUtils.toCamelCase("-CUSTOMER-ID"));
    }

    @Test
    void testToPascalCase() {
        assertEquals("CustomerId", NamingUtils.toPascalCase("CUSTOMER-ID"));
        assertEquals("CustomerName", NamingUtils.toPascalCase("CUSTOMER_NAME"));
        assertEquals("EmployeeRecord", NamingUtils.toPascalCase("EMPLOYEE-RECORD"));
        assertEquals("OrderDetails", NamingUtils.toPascalCase("ORDER_DETAILS"));
        assertEquals("CustomerId", NamingUtils.toPascalCase("CUSTOMER_ID"));
        assertEquals("OrderDetails", NamingUtils.toPascalCase("ORDER_DETAILS"));
        assertEquals("CustomerId", NamingUtils.toPascalCase("-CUSTOMER-ID"));
    }

}
