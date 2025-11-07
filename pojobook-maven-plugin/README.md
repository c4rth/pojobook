# PojoBook Maven Plugin

A Maven plugin for generating Java POJO classes from COBOL copybook definitions.

## Features

- Generates Java POJOs from COBOL copybook files
- **Supports wildcard patterns** to process multiple copybooks with a single execution
- Supports all COBOL copybook features:
    - REDEFINES clauses
    - OCCURS (fixed and variable length arrays)
    - All PIC types (numeric, alphanumeric, etc.)
    - USAGE clauses (COMP, COMP-3, PACKED-DECIMAL, etc.)
    - Condition names (88 levels)
    - Group items and nested structures
- Generates proper Java types based on COBOL data types
- Two generation modes: annotation-based or embedded serialization
- Optional getters/setters, toString(), equals(), and hashCode() methods
- Automatically adds generated sources to Maven compile classpath

## Usage

Add the plugin to your `pom.xml`:

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
                        <copybookFile>src/main/resources/copybooks/customer-record.cpy</copybookFile>
                        <packageName>com.example.generated</packageName>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

## Wildcard Support

Process multiple copybook files with a single execution using wildcards:

```xml

<execution>
    <id>generate-all-cpy-files</id>
    <goals>
        <goal>generate</goal>
    </goals>
    <configuration>
        <!-- Generate from all .cpy files -->
        <copybookFile>src/main/resources/copybooks/*.cpy</copybookFile>
        <packageName>com.example.generated</packageName>
        <generatorType>embedded</generatorType>
    </configuration>
</execution>
```

**Supported wildcard patterns:**

- `*.cpy` - All .cpy files in the directory
- `*.cbl` - All .cbl files in the directory
- `**/*.cpy` - All .cpy files in the directory tree (recursive)
- `customer-*.cpy` - Files starting with "customer-"
- `test?.cpy` - Single character wildcard (e.g., test1.cpy, test2.cpy)

## Configuration Parameters

### Required Parameters

| Parameter      | Type   | Description                                                                                                                     |
|----------------|--------|---------------------------------------------------------------------------------------------------------------------------------|
| `copybookFile` | String | Path to COBOL copybook file(s). Supports wildcards (e.g., `*.cpy`, `**/*.cbl`). Can be absolute or relative to project basedir. |
| `packageName`  | String | Java package name for the generated POJO classes.                                                                               |

### Optional Parameters

| Parameter                | Type    | Default                                                 | Description                                                                                             |
|--------------------------|---------|---------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `outputDirectory`        | File    | `${project.build.directory}/generated-sources/pojobook` | Output directory for generated sources.                                                                 |
| `generatorType`          | String  | `annotation`                                            | Generator type: `annotation` (annotation-based with reflection) or `embedded` (embedded serialization). |
| `encoding`               | String  | `EBCDIC`                                                | Character encoding for COBOL data: `EBCDIC` or `ASCII`. Only used with embedded generator.              |
| `generateGettersSetters` | boolean | `true`                                                  | Whether to generate getter and setter methods.                                                          |
| `generateToString`       | boolean | `true`                                                  | Whether to generate a toString() method.                                                                |
| `pojobook.skip`          | boolean | `false`                                                 | Skip plugin execution.                                                                                  |

## Examples

### Basic Example

Generate a POJO from a single copybook:

```xml

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
                <copybookFile>src/main/resources/employee-record.cpy</copybookFile>
                <packageName>com.mycompany.model</packageName>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Multiple Copybooks

Generate POJOs from multiple copybooks:

```xml

<plugin>
    <groupId>org.c4rth</groupId>
    <artifactId>pojobook-maven-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
    <executions>
        <execution>
            <id>generate-customer</id>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <copybookFile>src/main/resources/customer-record.cpy</copybookFile>
                <packageName>com.mycompany.model.customer</packageName>
            </configuration>
        </execution>
        <execution>
            <id>generate-transaction</id>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <copybookFile>src/main/resources/transaction-redefines.cpy</copybookFile>
                <packageName>com.mycompany.model.transaction</packageName>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Without Annotations

Generate simple POJOs without COBOL annotations:

```xml

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
                <copybookFile>src/main/resources/simple-record.cpy</copybookFile>
                <packageName>com.mycompany.model</packageName>
                <generateAnnotations>false</generateAnnotations>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Custom Output Directory

Specify a custom output directory:

```xml

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
                <copybookFile>src/main/resources/record.cpy</copybookFile>
                <packageName>com.mycompany.model</packageName>
                <outputDirectory>${project.build.directory}/generated/pojos</outputDirectory>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Command Line Usage

You can also run the plugin from the command line:

```bash
mvn org.c4rth:pojobook-maven-plugin:1.0-SNAPSHOT:generate \
  -DcopybookFile=src/main/resources/my-record.cpy \
  -DpackageName=com.example.model
```

## Example Copybook

Given a COBOL copybook `employee-record.cpy`:

```cobol
       01  EMPLOYEE-RECORD.
           05  EMP-ID              PIC 9(6).
           05  EMP-NAME            PIC X(30).
           05  EMP-SALARY          PIC 9(7)V99 COMP-3.
           05  EMP-HIRE-DATE.
               10  HIRE-YEAR       PIC 9(4).
               10  HIRE-MONTH      PIC 9(2).
               10  HIRE-DAY        PIC 9(2).
           05  EMP-DEPT            PIC X(10).
```

The plugin generates:

```java
package com.example.model;

import org.c4rth.pojobook.annotation.CobolField;
import org.c4rth.pojobook.annotation.CobolRecord;
import org.c4rth.pojobook.CobolDataType;
import org.c4rth.pojobook.CharacterEncoding;

@CobolRecord(encoding = CharacterEncoding.CP1047)
public class EmployeeRecord {

    @CobolField(level = 5, name = "EMP-ID", picture = "9(6)", type = CobolDataType.DISPLAY)
    private Integer empId;

    @CobolField(level = 5, name = "EMP-NAME", picture = "X(30)", type = CobolDataType.DISPLAY)
    private String empName;

    @CobolField(level = 5, name = "EMP-SALARY", picture = "9(7)V99", type = CobolDataType.COMP_3)
    private java.math.BigDecimal empSalary;

    @CobolField(level = 5, name = "EMP-DEPT", picture = "X(10)", type = CobolDataType.DISPLAY)
    private String empDept;

    // ... getters and setters ...
}
```

## Integration with PojoBook Serializer

The generated POJOs work seamlessly with the PojoBook serializer for runtime serialization and deserialization:

```java
// Deserialize from COBOL bytes
CopybookSerializer<EmployeeRecord> serializer =
        new CopybookSerializer<>(EmployeeRecord.class);
EmployeeRecord employee = serializer.deserialize(cobolBytes);

// Serialize back to COBOL bytes
byte[] cobolBytes = serializer.serialize(employee);
```

## Dependencies

The plugin depends on:

- `pojobook-generator` - POJO code generation
- `pojobook-serializer` - Copybook parsing

Generated code depends on:

- `pojobook-serializer` (if generateAnnotations = true)

## License

Same as parent PojoBook project.

