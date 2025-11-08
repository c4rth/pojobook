# PojoBook

A modern Java library for converting between Java POJOs and COBOL Copybook formats, supporting both annotation-based and
embedded serialization approaches.

## Overview

PojoBook simplifies the integration between Java applications and COBOL systems by:

- **Parsing COBOL Copybooks**: Reads and interprets COBOL copybook definitions
- **Generating Java POJOs**: Automatically creates Java classes from copybook specifications
- **Serialization/Deserialization**: Converts between Java objects and binary COBOL data formats
- **Multiple Approaches**: Choose between annotation-based (reflection) or embedded (no reflection) serialization
- **Maven Integration**: Seamless code generation via Maven plugin

PojoBook handles COBOL constructs including OCCURS clauses, REDEFINES, nested structures, and various data
types (COMP, COMP-3, PACKED-DECIMAL, etc.).

## Modules

The project consists of five modules:

- **pojobook-core**: Core parser and serialization/deserialization engine (annotation-based approach)
- **pojobook-generator**: POJO code generators from copybook definitions
- **pojobook-maven-plugin**: Maven plugin for build-time code generation
- **pojobook-annotation-samples**: Example usage with annotation-based approach
- **pojobook-embedded-samples**: Example usage with embedded serialization approach

The **private-samples** modules contain internal tests and is not part of the public distribution.

## Code Generation Approaches

PojoBook offers two distinct code generation strategies, each with different trade-offs:

### 1. Annotation-Based Generation

Generates lightweight POJOs with `@CobolRecord` and `@CobolField` annotations. Serialization and deserialization are
handled at runtime using reflection via the `CobolSerializer` class.

**Characteristics:**

- ✅ Simple, clean POJOs with minimal code
- ✅ Flexible - easy to modify field mappings via annotations
- ✅ Uses runtime reflection for serialization/deserialization
- ✅ Requires `pojobook-core` dependency at runtime
- ❌ Slight runtime overhead due to reflection

**Generated POJO Example:**

```java

@CobolRecord
public class EmployeeRecord {
    @CobolField(name = "EMPLOYEE-ID", picture = "9(8)", type = CobolDataType.DISPLAY, position = 0, length = 8)
    private Integer employeeId = 0;

    @CobolField(name = "FIRST-NAME", picture = "X(20)", type = CobolDataType.DISPLAY, position = 8, length = 20)
    private String firstName = "";

    // Getters, setters, equals, hashCode, toString...
}
```

**Usage:**

```java
// Serialize
PojoBook pojoBook = new PojoBook();
EmployeeRecord employee = new EmployeeRecord();
employee.setEmployeeId(12345);
employee.setFirstName("JOHN");

byte[] cobolData = pojoBook.serialize(employee);

// Deserialize
EmployeeRecord deserialized = pojoBook.deserialize(cobolData, EmployeeRecord.class);
```

### 2. Embedded Serialization Generation

Generates self-contained POJOs with embedded `serialize()` and `deserialize()` methods. No runtime dependencies on
reflection or external serialization libraries.

**Characteristics:**

- ✅ No reflection - better performance
- ✅ Self-contained - POJOs include all serialization logic
- ✅ Minimal runtime dependencies
- ✅ Easier to debug - all logic is in generated code
- ❌ Larger generated code files
- ❌ Less flexible - changes require regeneration

**Generated POJO Example:**

```java
public class EmployeeRecord {
    // PIC 9(8) - 8 bytes
    private Integer employeeId = 0;

    // PIC X(20) - 20 bytes
    private String firstName = "";

    // Getters, setters, equals, hashCode, toString...

    public byte[] serialize() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // ... embedded serialization logic ...
        return baos.toByteArray();
    }

    public static EmployeeRecord deserialize(byte[] data) throws IOException {
        EmployeeRecord record = new EmployeeRecord();
        // ... embedded deserialization logic ...
        return record;
    }
}
```

**Usage:**

```java
// Serialize
EmployeeRecord employee = new EmployeeRecord();
employee.setEmployeeId(12345);
employee.setFirstName("JOHN");

byte[] cobolData = employee.serialize();

// Deserialize
EmployeeRecord deserialized = EmployeeRecord.deserialize(cobolData);
```

## Supported COBOL Types

### Picture Clauses

| COBOL Picture | Java Type                 | Description                      |
|---------------|---------------------------|----------------------------------|
| `9(n)`        | `Integer` or `BigInteger` | Numeric (unsigned)               |
| `S9(n)`       | `Integer` or `BigInteger` | Numeric (signed)                 |
| `9(n)V9(m)`   | `BigDecimal`              | Numeric with decimals (unsigned) |
| `S9(n)V9(m)`  | `BigDecimal`              | Numeric with decimals (signed)   |
| `X(n)`        | `String`                  | Alphanumeric                     |
| `A(n)`        | `String`                  | Alphabetic                       |

### Usage Clauses (Data Types)

| COBOL Usage                  | Java Type / Mapping (used by generators)                                                                     | Description                     | Encoding          |
|------------------------------|--------------------------------------------------------------------------------------------------------------|---------------------------------|-------------------|
| `DISPLAY`                    | `String` (for X/A), or numeric mapped according to picture (see rules below)                                 | Character representation        | ASCII or EBCDIC   |
| `9(n)` / `S9(n)` (no V)      | `Short` (<=4 digits), `Integer` (5-9 digits), `Long` (10-18 digits), `BigInteger` (>18 digits)               | Integer numeric                 | Text or Binary    |
| `9(n)V9(m)` / `S9(n)V9(m)`   | `BigDecimal` (scale = m, precision = n+m)                                                                    | Numeric with decimals           | Text or Binary    |
| `X(n)` / `A(n)`              | `String`                                                                                                     | Alphanumeric / alphabetic       | ASCII or EBCDIC   |
| `COMP` / `BINARY` / `COMP-4` | Binary integer mapped by digit-length: `Short` / `Integer` / `Long` / `BigInteger` (same thresholds as 9(n)) | Binary integer                  | Binary            |
| `COMP-5`                     | Same as `COMP` (native binary): `Short` / `Integer` / `Long` / `BigInteger`                                  | Native binary                   | Binary            |
| `COMP-1`                     | `Float`                                                                                                      | Single precision floating point | IEEE 754 (binary) |
| `COMP-2`                     | `Double`                                                                                                     | Double precision floating point | IEEE 754 (binary) |
| `COMP-3` / `PACKED-DECIMAL`  | `BigDecimal` (scale inferred from picture if `V` present)                                                    | Packed decimal (BCD)            | Packed BCD        |

Notes and rules used by the generators:

- Numeric size thresholds: the generators pick Java integer types based on the total integer digits (n):
    - n <= 4 -> `Short`
    - 5 <= n <= 9 -> `Integer`
    - 10 <= n <= 18 -> `Long`
    - n > 18 -> `BigInteger`
- For signed pictures (`S9(...)`) the same type selection applies; sign is handled at conversion time.
- For pictures containing an implied decimal point (`V`), the generators always use `BigDecimal` with scale equal to the
  number of digits after `V`.
- `DISPLAY` fields with numeric pictures (e.g., `9(5)`) are mapped to the numeric Java types above, but purely
  alphanumeric `X(...)`/`A(...)` are `String`.
- `COMP-3` (packed decimal) is mapped to `BigDecimal` to preserve precision and scale.
- Floating point usages `COMP-1`/`COMP-2` map to `Float`/`Double` respectively.

### Complex Structures

| COBOL Feature                      | Java Representation             | Description                       |
|------------------------------------|---------------------------------|-----------------------------------|
| `OCCURS n TIMES`                   | `Array` or `List`               | Fixed-size arrays                 |
| `OCCURS n TO m TIMES DEPENDING ON` | `List`                          | Variable-size arrays              |
| `REDEFINES`                        | Multiple fields (same position) | Alternative field interpretations |
| `88 level` (Conditions)            | `boolean` methods               | Condition name checks             |
| Group items                        | Nested classes                  | Hierarchical structures           |

## Maven Plugin

The Maven plugin generates Java POJOs from COBOL copybook files during the build process.

### Basic Configuration

```xml

<build>
    <plugins>
        <plugin>
            <groupId>org.pojobook</groupId>
            <artifactId>pojobook-maven-plugin</artifactId>
            <version>LAST_VERSION</version>
            <executions>
                <execution>
                    <goals>
                        <goal>generate</goal>
                    </goals>
                    <configuration>
                        <copybookFile>src/main/resources/copybooks/employee-record.cpy</copybookFile>
                        <packageName>com.example.generated</packageName>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Wildcard Support

The `copybookFile` parameter supports wildcards, allowing you to generate POJOs from multiple copybook files with a
single execution:

```xml

<execution>
    <id>generate-all-copybooks</id>
    <goals>
        <goal>generate</goal>
    </goals>
    <configuration>
        <!-- Generate from all .cpy files in the copybooks directory -->
        <copybookFile>src/main/resources/copybooks/*.cpy</copybookFile>
        <packageName>com.example.generated</packageName>
        <generatorType>embedded</generatorType>
    </configuration>
</execution>
```

### Configuration Parameters

#### Required Parameters

| Parameter      | Type   | Description                                                                    |
|----------------|--------|--------------------------------------------------------------------------------|
| `copybookFile` | String | Path to COBOL copybook file(s). Supports wildcards (e.g., `*.cpy`, `**/*.cbl`) |
| `packageName`  | String | Java package name for generated classes                                        |

#### Optional Parameters

| Parameter         | Type   | Default                                                 | Description                                |
|-------------------|--------|---------------------------------------------------------|--------------------------------------------|
| `outputDirectory` | File   | `${project.build.directory}/generated-sources/pojobook` | Output directory for generated sources     |
| `generatorType`   | String | `EMBEDDED`                                              | Generator type: `ANNOTATION` or `EMBEDDED` |

## Dependencies

Add the core dependency to your project:

```xml

<dependency>
    <groupId>org.pojobook</groupId>
    <artifactId>pojobook-core</artifactId>
    <version>LAST_VERSION</version>
</dependency>
```

## Quick Start

### 1. Create a COBOL Copybook File

Create `src/main/resources/copybooks/employee.cpy`:

```cobol
       01  EMPLOYEE-RECORD.
           05  EMPLOYEE-ID         PIC 9(8).
           05  FIRST-NAME          PIC X(20).
           05  LAST-NAME           PIC X(30).
           05  SALARY              PIC S9(7)V99 COMP-3.
           05  HIRE-DATE           PIC 9(8).
```

### 2. Configure Maven Plugin

Add to your `pom.xml`:

```xml

<build>
    <plugins>
        <plugin>
            <groupId>org.pojobook</groupId>
            <artifactId>pojobook-maven-plugin</artifactId>
            <version>LAST_VERSION</version>
            <executions>
                <execution>
                    <goals>
                        <goal>generate</goal>
                    </goals>
                    <configuration>
                        <copybookFile>src/main/resources/copybooks/employee.cpy</copybookFile>
                        <packageName>com.example.model</packageName>
                        <generatorType>EMBEDDED</generatorType>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

## Examples

The project includes two sample modules:

- **pojobook-annotation-samples**: Demonstrates annotation-based approach with various copybook scenarios
- **pojobook-embedded-samples**: Demonstrates embedded serialization with the same scenarios

Each module includes test cases showing:

- Basic field types (numeric, alphanumeric)
- OCCURS clauses (arrays)
- Nested OCCURS (multi-dimensional arrays)
- REDEFINES clauses
- COMP-3 (packed decimal) fields
- Complex hierarchical structures
- Round-trip serialization/deserialization
- Reading binary COBOL data files

## License

This project is licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Please feel free to submit pull requests or open issues.

## Support

For questions, issues, or feature requests, please open an issue on the GitHub repository.
