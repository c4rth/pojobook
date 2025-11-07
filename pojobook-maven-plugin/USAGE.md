# Example Usage of PojoBook Maven Plugin

This directory contains examples of how to use the pojobook-maven-plugin in your Maven project.

## Basic Usage

Create a Maven project with the following structure:

```
my-project/
├── pom.xml
└── src/
    └── main/
        └── resources/
            └── copybooks/
                └── employee-record.cpy
```

### pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>my-cobol-project</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <!-- Required for generated POJOs with annotations -->
        <dependency>
            <groupId>org.c4rth</groupId>
            <artifactId>pojobook-serializer</artifactId>
            <version>1.0-SNAPSHOT</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- PojoBook Maven Plugin -->
            <plugin>
                <groupId>org.c4rth</groupId>
                <artifactId>pojobook-maven-plugin</artifactId>
                <version>1.0-SNAPSHOT</version>
                <executions>
                    <execution>
                        <id>generate-employee-pojo</id>
                        <goals>
                            <goal>generate</goal>
                        </goals>
                        <configuration>
                            <copybookFile>src/main/resources/copybooks/employee-record.cpy</copybookFile>
                            <packageName>com.example.model</packageName>
                            <!-- Optional: Generator type (annotation or embedded) -->
                            <!-- <generatorType>annotation</generatorType> -->
                            <!-- Optional: Encoding (EBCDIC or ASCII), only for embedded generator -->
                            <!-- <encoding>EBCDIC</encoding> -->
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

### employee-record.cpy

```cobol
       01  EMPLOYEE-RECORD.
           05  EMP-ID              PIC 9(6).
           05  EMP-NAME            PIC X(30).
           05  EMP-DEPT            PIC X(10).
           05  EMP-SALARY          PIC 9(7)V99 COMP-3.
           05  EMP-HIRE-DATE       PIC 9(8).
```

## Running the Plugin

### During Maven Build

The plugin runs automatically during the `generate-sources` phase:

```bash
mvn clean compile
```

This will:

1. Parse the copybook file
2. Generate Java POJO in `target/generated-sources/pojobook/com/example/model/EmployeeRecord.java`
3. Add the generated sources to the compile classpath
4. Compile everything together

### Standalone Execution

You can also run the plugin directly:

```bash
mvn org.c4rth:pojobook-maven-plugin:generate \
  -DcopybookFile=src/main/resources/copybooks/employee-record.cpy \
  -DpackageName=com.example.model
```

## Generator Types

The plugin supports two generator types that produce different styles of POJOs:

### 1. Annotation-Based Generator (Default)

**Configuration:**

```xml

<configuration>
    <copybookFile>src/main/resources/copybooks/employee-record.cpy</copybookFile>
    <packageName>com.example.model</packageName>
    <generatorType>annotation</generatorType>
</configuration>
```

**Characteristics:**

- ✅ Smaller POJO classes with annotations
- ✅ Requires `pojobook-serializer` library at runtime
- ✅ Uses reflection for serialization/deserialization
- ✅ Flexible - can change serialization logic without regenerating
- ❌ Slower performance due to reflection
- ❌ Requires external serializer/deserializer classes

**Dependencies Required:**

```xml

<dependency>
    <groupId>org.c4rth</groupId>
    <artifactId>pojobook-serializer</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

**Usage:**

```java
import org.c4rth.pojobook.serializer.CobolSerializer;
import org.c4rth.pojobook.deserializer.CobolDeserializer;

CobolSerializer serializer = new CobolSerializer();
CobolDeserializer deserializer = new CobolDeserializer();

// Serialize
byte[] data = serializer.serialize(employee);

// Deserialize
EmployeeRecord emp = deserializer.deserialize(data, EmployeeRecord.class);
```

### 2. Embedded Serialization Generator

**Configuration:**

```xml

<configuration>
    <copybookFile>src/main/resources/copybooks/employee-record.cpy</copybookFile>
    <packageName>com.example.model</packageName>
    <generatorType>embedded</generatorType>
    <encoding>EBCDIC</encoding> <!-- Optional: EBCDIC (default) or ASCII -->
</configuration>
```

**Characteristics:**

- ✅ **5-10x faster** - no reflection overhead
- ✅ Self-contained - no external dependencies needed
- ✅ GraalVM/AOT compatible out-of-the-box
- ✅ Easier debugging - serialization logic is visible
- ✅ Better IDE support - can navigate serialization code
- ❌ Larger POJO classes (embedded logic)
- ❌ Requires regeneration if serialization logic changes

**Dependencies Required:**

```xml
<!-- No runtime dependencies needed! -->
<!-- Only standard Java classes are used -->
```

**Usage:**

```java
// Serialize - directly on the object
byte[] data = employee.serialize();

// Deserialize - static method on the class
EmployeeRecord emp = EmployeeRecord.deserialize(data);
```

### Which Generator to Choose?

| Use Case                              | Recommended Generator |
|---------------------------------------|-----------------------|
| High-performance applications         | `embedded`            |
| Many copybooks, code size matters     | `annotation`          |
| GraalVM/native-image                  | `embedded`            |
| Need to debug serialization           | `embedded`            |
| Copybook structure changes frequently | `annotation`          |
| Minimize dependencies                 | `embedded`            |
| General purpose                       | `annotation`          |

## Generated Output

The plugin will generate a Java POJO like this:

```java
package com.example.model;

import org.c4rth.pojobook.annotation.CobolField;
import org.c4rth.pojobook.annotation.CobolRecord;
import org.c4rth.pojobook.CobolDataType;
import org.c4rth.pojobook.CharacterEncoding;

@CobolRecord(encoding = CharacterEncoding.CP1047)
public class Employeerecord {

    @CobolField(level = 5, name = "EMP-ID", picture = "9(6)", type = CobolDataType.DISPLAY)
    private Integer empId;

    @CobolField(level = 5, name = "EMP-NAME", picture = "X(30)", type = CobolDataType.DISPLAY)
    private String empName;

    @CobolField(level = 5, name = "EMP-DEPT", picture = "X(10)", type = CobolDataType.DISPLAY)
    private String empDept;

    @CobolField(level = 5, name = "EMP-SALARY", picture = "9(7)V99", type = CobolDataType.COMP_3)
    private java.math.BigDecimal empSalary;

    @CobolField(level = 5, name = "EMP-HIRE-DATE", picture = "9(8)", type = CobolDataType.DISPLAY)
    private Integer empHireDate;

    // Getters and setters...

    @Override
    public String toString() {
        // toString implementation...
    }
}
```

## Using the Generated POJOs

### With PojoBook Serializer

```java
import com.example.model.Employeerecord;
import org.c4rth.pojobook.CopybookSerializer;

public class Example {
    public static void main(String[] args) throws Exception {
        // Create serializer
        CopybookSerializer<Employeerecord> serializer =
                new CopybookSerializer<>(Employeerecord.class);

        // Deserialize from COBOL bytes
        byte[] cobolData = readCobolFile();
        Employeerecord employee = serializer.deserialize(cobolData);

        // Work with the POJO
        System.out.println("Employee ID: " + employee.getEmpId());
        System.out.println("Name: " + employee.getEmpName());
        System.out.println("Salary: " + employee.getEmpSalary());

        // Modify and serialize back
        employee.setEmpSalary(new BigDecimal("75000.00"));
        byte[] newCobolData = serializer.serialize(employee);
    }
}
```

## Multiple Copybooks

To generate POJOs from multiple copybooks, add multiple executions:

```xml

<plugin>
    <groupId>org.c4rth</groupId>
    <artifactId>pojobook-maven-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
    <executions>
        <execution>
            <id>generate-employee</id>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <copybookFile>src/main/resources/copybooks/employee-record.cpy</copybookFile>
                <packageName>com.example.model.employee</packageName>
            </configuration>
        </execution>
        <execution>
            <id>generate-customer</id>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <copybookFile>src/main/resources/copybooks/customer-record.cpy</copybookFile>
                <packageName>com.example.model.customer</packageName>
            </configuration>
        </execution>
        <execution>
            <id>generate-transaction</id>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <copybookFile>src/main/resources/copybooks/transaction-record.cpy</copybookFile>
                <packageName>com.example.model.transaction</packageName>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Advanced Configuration

### Without Annotations

If you don't need the COBOL annotations (for serialization/deserialization), you can generate plain POJOs:

```xml

<configuration>
    <copybookFile>src/main/resources/copybooks/simple-record.cpy</copybookFile>
    <packageName>com.example.model</packageName>
    <generateAnnotations>false</generateAnnotations>
</configuration>
```

### Custom Output Directory

```xml

<configuration>
    <copybookFile>src/main/resources/copybooks/record.cpy</copybookFile>
    <packageName>com.example.model</packageName>
    <outputDirectory>${project.build.directory}/custom-generated-sources</outputDirectory>
</configuration>
```

### Skip Plugin Execution

Temporarily disable the plugin:

```xml

<configuration>
    <copybookFile>src/main/resources/copybooks/record.cpy</copybookFile>
    <packageName>com.example.model</packageName>
    <skip>true</skip>
</configuration>
```

Or via command line:

```bash
mvn clean compile -Dpojobook.skip=true
```

## IDE Integration

### IntelliJ IDEA

After running Maven generate-sources, IntelliJ will automatically recognize the generated sources. If not:

1. Right-click on `target/generated-sources/pojobook`
2. Select "Mark Directory as" → "Generated Sources Root"

### Eclipse

1. Right-click project → Properties
2. Java Build Path → Source
3. Add Folder → select `target/generated-sources/pojobook`

### VS Code

The generated sources should be automatically recognized if you're using the Maven for Java extension.

## Troubleshooting

### Generated file not found during compilation

Make sure the `generate` goal runs before compilation:

```bash
mvn clean generate-sources compile
```

### Class name doesn't match expectations

The plugin generates class names based on:

1. The record name in the copybook (if present)
2. The copybook filename (if no record name)

The names are converted to PascalCase Java class names.

### Plugin not found

Make sure the plugin is installed in your local Maven repository:

```bash
cd /path/to/pojobook
./mvnw clean install
```

