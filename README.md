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

### Installation

```gradle
dependencies {
    compileOnly "io.github.danielliu1123:record-builder:+"
    annotationProcessor "io.github.danielliu1123:record-builder:+"
}
```

### Usage

Add `@RecordBuilder` annotation to your record:

```java
import recordbuilder.RecordBuilder;
import org.jspecify.annotations.Nullable;

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

@Generated(
    value = "recordbuilder.RecordBuilderProcessor",
    date = "..."
)
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
    public UserBuilder addAllRoles(Iterable<String> values) { ... }

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

## License

The MIT License.
