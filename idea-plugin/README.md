# RecordBuilder IntelliJ IDEA Plugin

This plugin provides IDE support for the RecordBuilder annotation processor, allowing you to use generated builder classes without compilation.

## Features

- **Zero-compilation IDE support**: Use builder classes immediately after adding `@RecordBuilder` annotation
- **Full code completion**: All builder methods are available in IDE autocomplete
- **Type-safe navigation**: Navigate to builder classes and methods
- **Real-time updates**: Builder structure updates as you modify record fields

## Supported Builder Methods

The plugin provides IDE support for all generated builder methods:

- `builder()` - Static factory method
- `builder(prototype)` - Static factory method with prototype
- `merge(other)` - Merge values from another record
- `setXxx(value)` - Setter methods for each field
- `getXxx()` - Getter methods for each field
- `hasXxx()` - Presence checker methods
- `clearXxx()` - Field clearer methods
- `build()` - Build the final record instance

## Usage

### 1. Build the Plugin

```bash
cd idea-plugin
./gradlew buildPlugin
```

The plugin ZIP file will be generated in `build/distributions/`.

### 2. Install the Plugin

#### Option A: Install from Disk
1. Open IntelliJ IDEA
2. Go to `Settings/Preferences` → `Plugins`
3. Click the gear icon ⚙️ → `Install Plugin from Disk...`
4. Select the generated ZIP file
5. Restart IDE

#### Option B: Run in Development Mode
```bash
./gradlew runIde
```

This will launch a new IntelliJ IDEA instance with the plugin installed.

### 3. Use in Your Project

Once the plugin is installed, simply annotate your records with `@RecordBuilder`:

```java
import recordbuilder.RecordBuilder;

@RecordBuilder
public record User(
    String name,
    Integer age,
    String email
) {}
```

The IDE will immediately recognize `UserBuilder` without compilation:

```java
// Works without compilation!
UserBuilder builder = UserBuilder.builder()
    .setName("John")
    .setAge(25)
    .setEmail("john@example.com");

User user = builder.build();

// Also works
User updated = UserBuilder.builder(user)
    .setAge(26)
    .build();
```

## Architecture

The plugin uses IntelliJ Platform's light class generation mechanism:

### Key Components

1. **RecordBuilderLightClassGenerator**
   - Extends `PsiAugmentProvider`
   - Generates virtual builder classes on-the-fly
   - Creates light methods for all builder operations

2. **RecordBuilderAugmentProvider**
   - Provides additional augmentations to record classes
   - Reserved for future enhancements

3. **RecordBuilderUtils**
   - Utility methods for annotation detection
   - Name generation helpers
   - Type checking utilities

### Design Principles

- **Non-intrusive**: No modification to source code
- **Performance**: Light classes are created lazily
- **Consistency**: Generated structure matches annotation processor output
- **Compatibility**: Works with standard IntelliJ Platform APIs

## Development

### Prerequisites

- JDK 17 or higher
- Gradle 7.0 or higher
- IntelliJ IDEA 2023.2 or higher (for development)

### Building

```bash
./gradlew :idea-plugin:buildPlugin
```

### Running Tests

```bash
./gradlew :idea-plugin:test
```

### Debugging

1. Run `./gradlew :idea-plugin:runIde`
2. Set breakpoints in plugin code
3. Use the launched IDE instance for testing

## Compatibility

- **IntelliJ IDEA**: 2023.2 - 2024.3
- **Java**: 17+
- **RecordBuilder**: 0.5.0+

## Limitations

- Only supports top-level record classes
- Nested records are not currently supported
- Generated builder classes appear only in IDE, not in compiled bytecode (use annotation processor for actual compilation)

## Troubleshooting

### Builder class not recognized

1. Ensure `@RecordBuilder` annotation is present
2. Check that the record is a top-level class
3. Try `File` → `Invalidate Caches / Restart`

### Plugin not loading

1. Verify IntelliJ IDEA version compatibility
2. Check `Help` → `Show Log in Finder/Explorer` for errors
3. Ensure plugin is enabled in `Settings` → `Plugins`

## Contributing

This plugin follows IntelliJ Platform plugin development best practices:

- Uses light classes for virtual code generation
- Follows PSI (Program Structure Interface) patterns
- Implements proper extension points
- Maintains compatibility with IntelliJ Platform updates

## License

Same as RecordBuilder project.
