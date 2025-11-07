package org.c4rth.pojobook.samples;

import org.c4rth.pojobook.PojoBook;
import org.c4rth.pojobook.samples.generated.EmployeeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EmployeeRecord serialization and deserialization.
 * Demonstrates real-world usage of the generated POJO with PojoBook.
 * <p>
 * Note: These tests focus on DISPLAY fields. COMP-3 field serialization
 * is demonstrated in the pojobook-serializer module tests.
 */
class EmployeeRecordUsageTest {

    private PojoBook pojoBook;
    private final Charset charset = Charset.forName("CP1047");

    @BeforeEach
    void setUp() {
        pojoBook = new PojoBook();
    }

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
        // Note: Salary and Bonus are COMP-3 fields - handled separately
        original.setEmploymentStatus("A");
        original.setPhoneNumber("555-123-4567");
        original.setEmail("jane.smith@company.com");

        // When: Serialize to COBOL format
        byte[] cobolData = pojoBook.serialize(original, charset);

        // Then: Should produce binary data
        assertNotNull(cobolData, "Serialization should produce data");
        assertTrue(cobolData.length > 0, "Serialized data should not be empty");

        // When: Deserialize back to Java object
        EmployeeRecord deserialized = pojoBook.deserialize(cobolData, EmployeeRecord.class, charset);

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
        byte[] serialized = pojoBook.serialize(employee, charset);
        EmployeeRecord deserialized = pojoBook.deserialize(serialized, EmployeeRecord.class, charset);

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
        byte[] data1 = pojoBook.serialize(emp1, charset);
        byte[] data2 = pojoBook.serialize(emp2, charset);
        byte[] data3 = pojoBook.serialize(emp3, charset);

        // Then: Each should produce different binary data
        assertNotNull(data1);
        assertNotNull(data2);
        assertNotNull(data3);
        assertFalse(Arrays.equals(data1, data2), "Different employees should have different binary data");
        assertFalse(Arrays.equals(data2, data3), "Different employees should have different binary data");

        // When: Deserialize all
        EmployeeRecord result1 = pojoBook.deserialize(data1, EmployeeRecord.class, charset);
        EmployeeRecord result2 = pojoBook.deserialize(data2, EmployeeRecord.class, charset);
        EmployeeRecord result3 = pojoBook.deserialize(data3, EmployeeRecord.class, charset);

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
            byte[] serialized = pojoBook.serialize(employee, charset);
            EmployeeRecord deserialized = pojoBook.deserialize(serialized, EmployeeRecord.class, charset);

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
        byte[] serialized = pojoBook.serialize(employee, charset);
        EmployeeRecord deserialized = pojoBook.deserialize(serialized, EmployeeRecord.class, charset);

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
        byte[] serialized1 = pojoBook.serialize(employee, charset);
        byte[] serialized2 = pojoBook.serialize(employee, charset);

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

