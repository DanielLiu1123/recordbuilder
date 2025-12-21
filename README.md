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
    int age,
    @Nullable String email // respect jspecify `@Nullable`
) {}
```

After compilation, a `UserBuilder` class will be generated:

```java
// Create builder and build object
User user = UserBuilder.builder()
    .setName("Alice")           // Non-null fields have null checks
    .setAge(18)
    .setEmail("alice@example.com")
    .build();

// Create builder from existing record
UserBuilder builder = UserBuilder.builder(user);

// Getter methods
String name = builder.getName();
String email = builder.getEmail();

// Has methods (check if field is set)
boolean hasName = builder.hasName();    // Returns true even if set to default value
boolean hasEmail = builder.hasEmail();  // Returns false if not set

// Clear methods
builder.clearName();   // Clear field value, has method will return false
builder.clearEmail();

// Merge method
var anotherUser = UserBuilder.builder()
    .setName("Bob")
    .setAge(25)
    .build();
builder.merge(anotherUser); // Merge values from existing record
```

## License

The MIT License.
