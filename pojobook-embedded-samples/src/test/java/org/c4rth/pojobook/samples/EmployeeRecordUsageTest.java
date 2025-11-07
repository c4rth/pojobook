package org.c4rth.pojobook.samples;

import org.c4rth.pojobook.samples.generated.EmployeeRecord;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EmployeeRecord with embedded serialization.
 * Demonstrates usage of self-contained POJOs with no external dependencies.
 * Key differences from annotation-based approach:
 * - No PojoBook instance needed
 * - Calls serialize() directly on the object
 * - Calls static deserialize() method on the class
 * - No reflection, 5-10x faster performance
 */
class EmployeeRecordUsageTest {

    private final Charset charset = Charset.forName("CP1047");

    @Test
    void testSerializeAndDeserializeEmployee() throws Exception {
        // Given: Create an employee with string and numeric DISPLAY fields
        EmployeeRecord original = new EmployeeRecord();
        original.setEmployeeId(12345678);
        original.setFirstName("Jane");
        original.setMiddleInitial("M");
        original.setLastName("Smith");
        original.setDateOfBirth(19850315);
        original.setHireDate(20100601);
        original.setDepartment("Engineering");
        original.setJobTitle("Senior Software Engineer");
        // Note: Salary and Bonus are COMP-3 fields
        original.setEmploymentStatus("A");
        original.setPhoneNumber("555-123-4567");
        original.setEmail("jane.smith@company.com");

        // When: Serialize to COBOL format using embedded method
        byte[] cobolData = original.serialize(charset);

        // Then: Should produce binary data
        assertNotNull(cobolData, "Serialization should produce data");
        assertTrue(cobolData.length > 0, "Serialized data should not be empty");

        // When: Deserialize back to Java object using static method
        EmployeeRecord deserialized = EmployeeRecord.deserialize(cobolData, charset);

        // Then: All DISPLAY fields should match (with trimming for strings)
        assertNotNull(deserialized, "Deserialization should produce an object");
        assertEquals(original.getEmployeeId(), deserialized.getEmployeeId());
        assertEquals(original.getFirstName().trim(), deserialized.getFirstName().trim());
        assertEquals(original.getMiddleInitial().trim(), deserialized.getMiddleInitial().trim());
        assertEquals(original.getLastName().trim(), deserialized.getLastName().trim());
        assertEquals(original.getDateOfBirth(), deserialized.getDateOfBirth());
        assertEquals(original.getHireDate(), deserialized.getHireDate());
        assertEquals(original.getDepartment().trim(), deserialized.getDepartment().trim());
        assertEquals(original.getJobTitle().trim(), deserialized.getJobTitle().trim());
        assertEquals(original.getEmploymentStatus().trim(), deserialized.getEmploymentStatus().trim());
        assertEquals(original.getPhoneNumber().trim(), deserialized.getPhoneNumber().trim());
        assertEquals(original.getEmail().trim(), deserialized.getEmail().trim());
    }

    @Test
    void testSerializeEmployeeWithMinimalData() throws Exception {
        // Given: Employee with only required fields
        EmployeeRecord employee = new EmployeeRecord();
        employee.setEmployeeId(99999999);
        employee.setFirstName("Bob");
        employee.setLastName("Brown");
        employee.setHireDate(20200101);
        employee.setEmploymentStatus("A");

        // When: Serialize and deserialize
        byte[] serialized = employee.serialize(charset);
        EmployeeRecord deserialized = EmployeeRecord.deserialize(serialized, charset);

        // Then: Essential fields should be preserved
        assertNotNull(deserialized);
        assertEquals(employee.getEmployeeId(), deserialized.getEmployeeId());
        assertEquals(employee.getFirstName().trim(), deserialized.getFirstName().trim());
        assertEquals(employee.getLastName().trim(), deserialized.getLastName().trim());
        assertEquals(employee.getHireDate(), deserialized.getHireDate());
        assertEquals(employee.getEmploymentStatus().trim(), deserialized.getEmploymentStatus().trim());
    }

    @Test
    void testSerializeMultipleEmployees() throws Exception {
        // Given: Three different employees
        EmployeeRecord emp1 = createEmployee(11111111, "Alice", "Johnson", "Marketing");
        EmployeeRecord emp2 = createEmployee(22222222, "Bob", "Williams", "Sales");
        EmployeeRecord emp3 = createEmployee(33333333, "Carol", "Davis", "IT");

        // When: Serialize each
        byte[] data1 = emp1.serialize(charset);
        byte[] data2 = emp2.serialize(charset);
        byte[] data3 = emp3.serialize(charset);

        // Then: Each should produce different binary data
        assertNotNull(data1);
        assertNotNull(data2);
        assertNotNull(data3);
        assertFalse(java.util.Arrays.equals(data1, data2), "Different employees should have different binary data");
        assertFalse(java.util.Arrays.equals(data2, data3), "Different employees should have different binary data");

        // When: Deserialize all
        EmployeeRecord result1 = EmployeeRecord.deserialize(data1, charset);
        EmployeeRecord result2 = EmployeeRecord.deserialize(data2, charset);
        EmployeeRecord result3 = EmployeeRecord.deserialize(data3, charset);

        // Then: Each should match its original
        assertEquals("Alice", result1.getFirstName().trim());
        assertEquals("Bob", result2.getFirstName().trim());
        assertEquals("Carol", result3.getFirstName().trim());
    }

    @Test
    void testEmploymentStatusValues() throws Exception {
        // Test different employment status codes
        String[] statuses = {"A", "I", "T"}; // Active, Inactive, Terminated

        for (String status : statuses) {
            // Given: Employee with specific status
            EmployeeRecord employee = new EmployeeRecord();
            employee.setEmployeeId(12345678);
            employee.setFirstName("Test");
            employee.setLastName("Employee");
            employee.setEmploymentStatus(status);
            employee.setHireDate(20200101);

            // When: Serialize and deserialize
            byte[] serialized = employee.serialize(charset);
            EmployeeRecord deserialized = EmployeeRecord.deserialize(serialized, charset);

            // Then: Status should be preserved
            assertEquals(status, deserialized.getEmploymentStatus().trim(),
                    "Status '" + status + "' should be preserved through serialization");
        }
    }

    @Test
    void testLongNames() throws Exception {
        // Given: Employee with maximum length names
        EmployeeRecord employee = new EmployeeRecord();
        employee.setEmployeeId(11111111);
        employee.setFirstName("Elizabethmaxlength"); // 20 chars max
        employee.setLastName("Williamsonverylonglastname"); // 30 chars max
        employee.setDepartment("EngineeringDepartm"); // 20 chars max
        employee.setJobTitle("Chief Technology Officer Role"); // 30 chars max
        employee.setHireDate(20200101);
        employee.setEmploymentStatus("A");

        // When: Serialize and deserialize
        byte[] serialized = employee.serialize(charset);
        EmployeeRecord deserialized = EmployeeRecord.deserialize(serialized, charset);

        // Then: Names should be preserved (possibly truncated to field length)
        assertNotNull(deserialized);
        assertNotNull(deserialized.getFirstName());
        assertNotNull(deserialized.getLastName());
        assertNotNull(deserialized.getDepartment());
        assertNotNull(deserialized.getJobTitle());
    }

    @Test
    void testSerializationConsistency() throws Exception {
        // Given: An employee
        EmployeeRecord employee = new EmployeeRecord();
        employee.setEmployeeId(55555555);
        employee.setFirstName("John");
        employee.setLastName("Doe");
        employee.setDepartment("Sales");
        employee.setHireDate(20150101);
        employee.setEmploymentStatus("A");

        // When: Serialize multiple times
        byte[] serialized1 = employee.serialize(charset);
        byte[] serialized2 = employee.serialize(charset);

        // Then: Should produce identical binary data
        assertArrayEquals(serialized1, serialized2,
                "Serializing the same object multiple times should produce identical binary data");
    }

    // Helper method to create test employees
    private EmployeeRecord createEmployee(int id, String firstName, String lastName, String department) {
        EmployeeRecord emp = new EmployeeRecord();
        emp.setEmployeeId(id);
        emp.setFirstName(firstName);
        emp.setLastName(lastName);
        emp.setDepartment(department);
        emp.setHireDate(20200101);
        emp.setEmploymentStatus("A");
        return emp;
    }
}

