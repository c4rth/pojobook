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

PojoBook handles complex COBOL constructs including OCCURS clauses, REDEFINES, nested structures, and various data
types (COMP, COMP-3, PACKED-DECIMAL, etc.).

## Modules

The project consists of five modules:

- **pojobook-serializer**: Core serialization/deserialization engine (annotation-based approach)
- **pojobook-generator**: POJO code generators from copybook definitions
- **pojobook-maven-plugin**: Maven plugin for build-time code generation
- **pojobook-annotation-samples**: Example usage with annotation-based approach
- **pojobook-embedded-samples**: Example usage with embedded serialization approach

## Code Generation Approaches

PojoBook offers two distinct code generation strategies, each with different trade-offs:

### 1. Annotation-Based Generation

Generates lightweight POJOs with `@CobolRecord` and `@CobolField` annotations. Serialization and deserialization are
handled at runtime using reflection via the `CobolSerializerFacade` class.

**Characteristics:**

- ✅ Simple, clean POJOs with minimal code
- ✅ Flexible - easy to modify field mappings via annotations
- ✅ Uses runtime reflection for serialization/deserialization
- ✅ Requires `pojobook-serializer` dependency at runtime
- ❌ Slight runtime overhead due to reflection

**Generated POJO Example:**

```java

@CobolRecord
public class EmployeeRecord {
    @CobolField(name = "EMPLOYEE-ID", picture = "9(8)", type = CobolDataType.DISPLAY,
            position = 0, length = 8)
    private Integer employeeId = 0;

    @CobolField(name = "FIRST-NAME", picture = "X(20)", type = CobolDataType.DISPLAY,
            position = 8, length = 20)
    private String firstName = "";

    // Getters, setters, equals, hashCode, toString...
}
```

**Usage:**

```java
// Serialize
CobolSerializerFacade facade = new CobolSerializerFacade(CharacterEncoding.CP1047);
EmployeeRecord employee = new EmployeeRecord();
employee.

setEmployeeId(12345);
employee.

setFirstName("JOHN");

byte[] cobolData = facade.serialize(employee);

// Deserialize
EmployeeRecord deserialized = facade.deserialize(cobolData, EmployeeRecord.class);
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
employee.

setEmployeeId(12345);
employee.

setFirstName("JOHN");

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

| COBOL Usage                  | Java Type                                          | Description                     | Encoding          |
|------------------------------|----------------------------------------------------|---------------------------------|-------------------|
| `DISPLAY`                    | `Integer`, `BigInteger`, `BigDecimal`, or `String` | Character representation        | ASCII or EBCDIC   |
| `COMP` / `BINARY` / `COMP-4` | `Integer` or `BigInteger`                          | Binary integer                  | Binary            |
| `COMP-1`                     | `Float`                                            | Single precision floating point | Binary (IEEE 754) |
| `COMP-2`                     | `Double`                                           | Double precision floating point | Binary (IEEE 754) |
| `COMP-3` / `PACKED-DECIMAL`  | `BigDecimal`                                       | Packed decimal (BCD)            | Binary (BCD)      |
| `COMP-5`                     | `Integer` or `BigInteger`                          | Native binary                   | Binary            |

### Complex Structures

| COBOL Feature                      | Java Representation             | Description                       |
|------------------------------------|---------------------------------|-----------------------------------|
| `OCCURS n TIMES`                   | `Array` or `List`               | Fixed-size arrays                 |
| `OCCURS n TO m TIMES DEPENDING ON` | `List`                          | Variable-size arrays              |
| `REDEFINES`                        | Multiple fields (same position) | Alternative field interpretations |
| `88 level` (Conditions)            | `boolean` methods               | Condition name checks             |
| Group items                        | Nested classes                  | Hierarchical structures           |

### Character Encodings

- **EBCDIC** (`Cp037`): IBM mainframe encoding (default for embedded serialization)
- **ASCII** (`US-ASCII`): Standard ASCII encoding
- **UTF-8**: Unicode encoding

Character encoding is used for:

- DISPLAY data types with picture clauses `X(n)` and `A(n)`
- Converting between Java strings and COBOL character data

Binary data types (COMP, COMP-3, etc.) are not affected by character encoding.

## Maven Plugin

The Maven plugin generates Java POJOs from COBOL copybook files during the build process.

### Basic Configuration

```xml

<build>
    <plugins>
        <plugin>
            <groupId>org.c4rth</groupId>
            <artifactId>pojobook-maven-plugin</artifactId>
            <version>1.0-SNAPSHOT</version>
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

**Supported wildcard patterns:**

- `*.cpy` - All files ending with .cpy in the specified directory
- `*.cbl` - All files ending with .cbl in the specified directory
- `**/*.cpy` - All .cpy files in the directory and all subdirectories (recursive)
- `customer-*.cpy` - All files starting with "customer-" and ending with .cpy
- `test?.cpy` - Files like test1.cpy, test2.cpy, etc. (? matches single character)

### Configuration Parameters

#### Required Parameters

| Parameter      | Type   | Description                                                                    |
|----------------|--------|--------------------------------------------------------------------------------|
| `copybookFile` | String | Path to COBOL copybook file(s). Supports wildcards (e.g., `*.cpy`, `**/*.cbl`) |
| `packageName`  | String | Java package name for generated classes                                        |

#### Optional Parameters

| Parameter                   | Type    | Default                                                 | Description                                                      |
|-----------------------------|---------|---------------------------------------------------------|------------------------------------------------------------------|
| `outputDirectory`           | File    | `${project.build.directory}/generated-sources/pojobook` | Output directory for generated sources                           |
| `generatorType`             | String  | `ANNOTATION`                                            | Generator type: `ANNOTATION` or `EMBEDDED`                       |
| `generateGettersSetters`    | boolean | `true`                                                  | Generate getter and setter methods                               |
| `generateToString`          | boolean | `true`                                                  | Generate toString() method                                       |
| `generateEqualsAndHashCode` | boolean | `true`                                                  | Generate equals() and hashCode() methods                         |
| `defaultEncoding`           | String  | -                                                       | Character encoding: `EBCDIC`, `ASCII`, or `UTF8` (embedded only) |

### Multiple Copybooks Example

Using wildcards to process multiple copybooks efficiently:

```xml

<plugin>
    <groupId>org.c4rth</groupId>
    <artifactId>pojobook-maven-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
    <executions>
        <!-- Generate from all .cpy files with embedded serialization -->
        <execution>
            <id>generate-cpy-files</id>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <copybookFile>src/main/resources/copybooks/*.cpy</copybookFile>
                <packageName>com.example.model</packageName>
                <generatorType>embedded</generatorType>
                <encoding>EBCDIC</encoding>
            </configuration>
        </execution>

        <!-- Generate from all .cbl files with annotation-based approach -->
        <execution>
            <id>generate-cbl-files</id>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <copybookFile>src/main/resources/copybooks/*.cbl</copybookFile>
                <packageName>com.example.model</packageName>
                <generatorType>annotation</generatorType>
            </configuration>
        </execution>

        <!-- Recursive: all copybooks in subdirectories -->
        <execution>
            <id>generate-all-recursive</id>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <copybookFile>src/main/resources/copybooks/**/*.cpy</copybookFile>
                <packageName>com.example.model</packageName>
                <generatorType>embedded</generatorType>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Or generate specific files individually:

```xml

<plugin>
    <groupId>org.c4rth</groupId>
    <artifactId>pojobook-maven-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
    <executions>
        <!-- Employee Record with Annotations -->
        <execution>
            <id>generate-employee</id>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <copybookFile>src/main/resources/copybooks/employee-record.cpy</copybookFile>
                <packageName>com.example.model</packageName>
                <generatorType>annotation</generatorType>
            </configuration>
        </execution>

        <!-- Customer Record with Embedded Serialization -->
        <execution>
            <id>generate-customer</id>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <copybookFile>src/main/resources/copybooks/customer-record.cpy</copybookFile>
                <packageName>com.example.model</packageName>
                <generatorType>embedded</generatorType>
                <defaultEncoding>EBCDIC</defaultEncoding>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Dependencies

### For Annotation-Based POJOs

Add the serializer dependency to your project:

```xml

<dependency>
    <groupId>org.c4rth</groupId>
    <artifactId>pojobook-serializer</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### For Embedded Serialization POJOs

Minimal dependencies required (only for CharacterEncoding enum):

```xml

<dependency>
    <groupId>org.c4rth</groupId>
    <artifactId>pojobook-serializer</artifactId>
    <version>1.0-SNAPSHOT</version>
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
            <groupId>org.c4rth</groupId>
            <artifactId>pojobook-maven-plugin</artifactId>
            <version>1.0-SNAPSHOT</version>
            <executions>
                <execution>
                    <goals>
                        <goal>generate</goal>
                    </goals>
                    <configuration>
                        <copybookFile>src/main/resources/copybooks/employee.cpy</copybookFile>
                        <packageName>com.example.model</packageName>
                        <generatorType>EMBEDDED</generatorType>
                        <defaultEncoding>EBCDIC</defaultEncoding>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### 3. Generate and Use

```bash
mvn clean compile
```

The generated class will be in `target/generated-sources/pojobook/com/example/model/EmployeeRecord.java`

### 4. Use in Your Code

**With Embedded Serialization:**

```java
// Create and populate
EmployeeRecord employee = new EmployeeRecord();
employee.

setEmployeeId(12345678);
employee.

setFirstName("JOHN");
employee.

setLastName("DOE");
employee.

setSalary(new BigDecimal("75000.50"));
        employee.

setHireDate(20241106);

// Serialize to COBOL format
byte[] cobolData = employee.serialize();

// Write to file or send to mainframe...
Files.

write(Paths.get("employee.dat"),cobolData);

// Deserialize from COBOL format
byte[] data = Files.readAllBytes(Paths.get("employee.dat"));
EmployeeRecord deserialized = EmployeeRecord.deserialize(data);
```

**With Annotation-Based Serialization:**

```java
// Create facade
CobolSerializerFacade facade = new CobolSerializerFacade(CharacterEncoding.CP1047);

// Create and populate
EmployeeRecord employee = new EmployeeRecord();
employee.

setEmployeeId(12345678);
employee.

setFirstName("JOHN");

// Serialize
byte[] cobolData = facade.serialize(employee);

// Deserialize
EmployeeRecord deserialized = facade.deserialize(cobolData, EmployeeRecord.class);
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

## Features

✅ **Comprehensive COBOL Support**

- All picture clause formats
- All usage clauses (COMP, COMP-3, PACKED-DECIMAL, etc.)
- OCCURS with fixed and variable lengths
- REDEFINES for alternative field interpretations
- Nested group structures
- Condition names (88 level)

✅ **Flexible Code Generation**

- Annotation-based (reflection) approach
- Embedded serialization (no reflection) approach
- Customizable package names and options
- Optional getters/setters, toString, equals/hashCode

✅ **Data Validation**

- Field length validation in setters
- Array size validation
- Type-safe conversions

✅ **Character Encoding Support**

- EBCDIC (IBM mainframe)
- ASCII
- UTF-8

✅ **Maven Integration**

- Automatic code generation during build
- Generated sources added to classpath
- Support for multiple copybooks

## Requirements

- Java 21 or higher
- Maven 3.6 or higher

## License

This project is licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Please feel free to submit pull requests or open issues.

## Support

For questions, issues, or feature requests, please open an issue on the GitHub repository.

