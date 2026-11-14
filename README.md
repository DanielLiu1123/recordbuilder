# Record Builder

Annotation processor to generate builder classes for Java records, inspired by Protobuf.

## Background

After writing millions of lines of Java code, here are my findings:

1. Record can replace part of Lombok's capabilities, but before Java has named parameter constructors with default values, the Builder pattern remains the best solution for object construction.

2. Protobuf made many correct API design decisions:
   - One single way to build objects (builder)
   - Not null by default (does not accept or return null)
   - Builder class has getter/has/clear methods

Inspired by Protobuf, this project generates builder classes for records with setter/getter/has/clear methods, allowing you to always build objects in one way and support various complex use cases.

## Quick Start

### Requirements

- JDK 17 or later for compilation and annotation processing.
- JDK 21 or later when using `SequencedCollection`, `SequencedSet`, or `SequencedMap`.
- Annotation processing enabled in your build and IDE.

### Installation

Use the same pinned version for the annotation and processor dependencies:

```gradle
def recordBuilderVersion = "0.7.0"

dependencies {
    compileOnly "io.github.danielliu1123:record-builder:${recordBuilderVersion}"
    annotationProcessor "io.github.danielliu1123:record-builder:${recordBuilderVersion}"
    // Only needed if your source uses JSpecify annotations.
    compileOnly "org.jspecify:jspecify:1.0.0"
}
```

For Maven, declare the annotation dependency and configure the processor explicitly:

```xml
<properties>
    <maven.compiler.release>17</maven.compiler.release>
    <record-builder.version>0.7.0</record-builder.version>
</properties>

<dependencies>
    <dependency>
        <groupId>io.github.danielliu1123</groupId>
        <artifactId>record-builder</artifactId>
        <version>${record-builder.version}</version>
        <scope>provided</scope>
    </dependency>
    <!-- Only needed if your source uses JSpecify annotations. -->
    <dependency>
        <groupId>org.jspecify</groupId>
        <artifactId>jspecify</artifactId>
        <version>1.0.0</version>
        <scope>provided</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.14.1</version>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>io.github.danielliu1123</groupId>
                        <artifactId>record-builder</artifactId>
                        <version>${record-builder.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### Usage

Add `@RecordBuilder` annotation to your record:

```java
import recordbuilder.RecordBuilder;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;

@RecordBuilder
public record User(
    String name,
    Integer age,
    @Nullable String email,
    List<String> roles,
    Map<String, String> attributes
) {}
```

### Generated Code

After compilation, a `UserBuilder` class will be generated with the following structure:

```java
import org.jspecify.annotations.Nullable;

@Generated("recordbuilder.RecordBuilderProcessor")
public final class UserBuilder {
    private String _name;
    private Integer _age;
    private @Nullable String _email;
    private List<String> _roles;
    private Map<String, String> _attributes;
   
    private int _presenceMask0_;

    private UserBuilder() {}

    // Factory methods to create a new builder
    public static UserBuilder builder() { ... }
    public static UserBuilder builder(User prototype) { ... }

    // Merge method to merge non-null fields from another record
    public UserBuilder merge(User other) { ... }

    // Setter methods for singular fields
    public UserBuilder setName(String name) { ... }
    public UserBuilder setAge(Integer age) { ... }
    public UserBuilder setEmail(@Nullable String email) { ... }

    // Adder methods for Collection
    public UserBuilder addRoles(String value) { ... }
    public UserBuilder addAllRoles(Iterable<? extends String> values) { ... }

    // Putter methods for Map
    public UserBuilder putAttributes(String key, String value) { ... }
    public UserBuilder putAllAttributes(Map<String, String> values) { ... }

    // Has methods (check if the field was set)
    public boolean hasName() { ... }
    public boolean hasAge() { ... }
    public boolean hasEmail() { ... }
    public boolean hasRoles() { ... }
    public boolean hasAttributes() { ... }

    // Getter methods
    public String getName() { ... }
    public Integer getAge() { ... }
    public @Nullable String getEmail() { ... }
    public List<String> getRoles() { ... }
    public Map<String, String> getAttributes() { ... }

    // Clear methods to reset fields and mark as unset
    public UserBuilder clearName() { ... }
    public UserBuilder clearAge() { ... }
    public UserBuilder clearEmail() { ... }
    public UserBuilder clearRoles() { ... }
    public UserBuilder clearAttributes() { ... }
    public UserBuilder clear() { ... }

    // Build method to create the record
    public User build() { ... }

    // toString method to show only explicitly set fields
    @Override
    public String toString() { ... }
}
```

The generated builder includes:
- **Factory methods**: `builder()` to create new builder, `builder(prototype)` to copy from existing record
- **Merge method**: `merge(other)` to copy from existing record
- **Setter methods**: `setXxx()` with fluent API, non-null fields have null validation
- **Adder/Putter methods**: `addXxx()`/`addAllXxx()` for Collection, `putXxx()`/`putAllXxx()` for Map
- **Getter methods**: `getXxx()` to access current field values
- **Has methods**: `hasXxx()` to check if field was explicitly set (returns true even for default values)
- **Clear methods**: `clearXxx()` to reset field and mark as unset
- **Build method**: `build()` to construct the final record
- **toString**: Only shows fields that were explicitly set

## Behavior and limitations

The table below describes the current Builder contract. Null checks on inputs do not make the record constructor or every Builder getter non-null.

| Operation | Behavior |
| --- | --- |
| New Builder / `clearXxx()` | Primitive fields have their Java default value; reference fields, including collections, are `null`. Presence is false. |
| `setXxx(value)` | Rejects null for non-nullable reference fields. Explicitly setting a nullable field to null still sets presence. |
| `getXxx()` | Returns the current field value, including null for an unset reference field. Collections and arrays are exposed by reference. |
| `hasXxx()` | Tracks explicit assignments in this Builder, including default values and empty bulk inputs. Presence is not stored in the resulting record. |
| `build()` | Calls the record constructor with the current values. It does not validate required fields or defensively copy collections or arrays. Constructor validation still applies. |
| `addAllXxx()` / `putAllXxx()` | Reads and checks inputs for disallowed nulls before changing the field. Input iteration or null-validation failures leave values and presence unchanged. This buffering uses temporary memory proportional to the input size. |
| `merge(other)` | Overwrites primitive fields and non-null scalar references, appends collections, and inserts/overwrites map entries. Null reference fields are skipped, including nullable ones. |
| `builder(prototype)` | Creates an empty Builder and calls `merge(prototype)`. It cannot recover the source object's original assignment history. |

Bulk operations are not general transactions: collection-specific failures during insertion, such as incompatible sorted-set elements, are still subject to the underlying implementation's behavior. Successful bulk operations preserve existing collection references. Builders are not thread-safe.

Records provide shallow immutability. Mutating a Builder's collection after `build()` can change a previously built record. Use a record constructor to enforce your own required-field validation and defensive-copy policy when needed.

Builders are generated in the record's package as `<RecordSimpleName>Builder`, including for nested records. Annotated records must have unique simple names within a package and their generated Builder names must not conflict with existing types. Private records and records inside private enclosing types are unsupported. Protected nested records produce package-private builders.

Known JDK collection and map types receive `add`/`put` methods; other types receive scalar setters. Recognized Nullable annotations include JSpecify, JetBrains, `javax.annotation`, `jakarta.annotation`, AndroidX, Checker Framework, and FindBugs annotations.

Generated source omits timestamps so identical inputs produce reproducible output.

## Development

```sh
./gradlew build
```

CI runs tests on JDK 17, 21, and 25. Tests cover generated-source compilation, bulk input validation, nested-record diagnostics, and presence-mask boundaries. Sequenced collection tests run on JDK 21 and later.

## License

The MIT License.
