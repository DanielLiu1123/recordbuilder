package recordbuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"rawtypes", "unchecked"})
class RecordBuilderProcessorTest {

    @Test
    void shouldFailWhenAnnotationIsNotUsedOnClass() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.NotARecord", """
                package test;

                import recordbuilder.RecordBuilder;

                @RecordBuilder
                public class NotARecord {
                    private String field;
                }
                """);

        Compilation compilation =
                Compiler.javac().withProcessors(new RecordBuilderProcessor()).compile(source);

        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining("@RecordBuilder can only be applied to records");
    }

    @Test
    void shouldFailWhenAnnotationIsUsedOnInterface() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.NotARecord", """
                package test;

                import recordbuilder.RecordBuilder;

                @RecordBuilder
                public interface NotARecord {
                    String field();
                }
                """);

        Compilation compilation =
                Compiler.javac().withProcessors(new RecordBuilderProcessor()).compile(source);

        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining("@RecordBuilder can only be applied to records");
    }

    @Test
    void shouldFailWhenAnnotationIsUsedOnEnum() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.NotARecord", """
                package test;

                import recordbuilder.RecordBuilder;

                @RecordBuilder
                public enum NotARecord {
                    VALUE1, VALUE2
                }
                """);

        Compilation compilation =
                Compiler.javac().withProcessors(new RecordBuilderProcessor()).compile(source);

        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining("@RecordBuilder can only be applied to records");
    }

    @Test
    void testGenericRecord() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceString("test.GenericRecord", """
                package test;
                import recordbuilder.RecordBuilder;
                import java.util.List;
                @RecordBuilder
                public record GenericRecord<T, E extends Number>(T data, E value, List<T> list) {}
                """);

        Compilation compilation =
                Compiler.javac().withProcessors(new RecordBuilderProcessor()).compile(source);

        CompilationSubject.assertThat(compilation).succeeded();

        var builderSource =
                compilation.generatedSourceFile("test.GenericRecordBuilder").orElseThrow();
        String content = builderSource.getCharContent(true).toString();

        assertThat(content)
                .contains("public final class GenericRecordBuilder<T, E extends Number>")
                .contains("public static <T, E extends Number> GenericRecordBuilder<T, E> builder()")
                .contains("public static <T, E extends Number> GenericRecordBuilder<T, E> builder(")
                .contains("GenericRecord<T, E> prototype")
                .contains("public GenericRecordBuilder<T, E> merge(GenericRecord<T, E> other)")
                .contains("public GenericRecordBuilder<T, E> setData(T data)")
                .contains("public GenericRecord<T, E> build()");
    }

    /**
     * For debugging the annotation processor.
     */
    @Test
    void testRecordBuilderProcessor() throws Exception {
        JavaFileObject source = loadTestSource(Everything.class);

        Compilation compilation =
                Compiler.javac().withProcessors(new RecordBuilderProcessor()).compile(source);

        CompilationSubject.assertThat(compilation).succeeded();

        var everythingBuilderSource =
                CompilationSubject.assertThat(compilation).generatedSourceFile("recordbuilder.EverythingBuilder");

        // Verify array component annotation
        // JavaPoet might add a space after the dot for nested classes: Everything. @Nullable JavaClass
        // And it might add newlines
        everythingBuilderSource.contentsAsUtf8String().contains("setNullableJavaClassArray");
        everythingBuilderSource
                .contentsAsUtf8String()
                .contains("Everything. @Nullable JavaClass[] nullableJavaClassArray");
    }

    @Nested
    class BuilderMethodTests {
        @Test
        void shouldCreateNewBuilderInstance() {
            // Test that builder() creates a new builder instance
            EverythingBuilder builder1 = EverythingBuilder.builder();
            EverythingBuilder builder2 = EverythingBuilder.builder();

            assertThat(builder1).isNotNull();
            assertThat(builder2).isNotNull();
            assertThat(builder1).isNotSameAs(builder2);
        }

        @Test
        void shouldCopyAllFieldsFromRecord() {
            // Create a record with some values
            LocalDate date = LocalDate.of(2024, 1, 15);
            Everything original = EverythingBuilder.builder()
                    .setByte_((byte) 10)
                    .setShort_((short) 20)
                    .setInt_(100)
                    .setLong_(1000L)
                    .setFloat_(1.5f)
                    .setDouble_(2.5)
                    .setChar_('X')
                    .setBoolean_(true)
                    .setString("Original")
                    .setNullableString("Nullable")
                    .setLocalDate(date)
                    .setJavaRecord(new Everything.JavaRecord("record"))
                    .setJavaClass(createJavaClass("class"))
                    .addAllListString(List.of("A", "B"))
                    .addAllSetString(Set.of("X", "Y"))
                    .putAllMapStringInteger(Map.of("key", 42))
                    .addAllArrayListString(new ArrayList<>(List.of("C", "D")))
                    .putAllHashMapStringInteger(new HashMap<>(Map.of("hash", 99)))
                    .addAllHashSetString(new HashSet<>(Set.of("Z")))
                    .build();

            // Test builder(prototype) creates a builder with all values copied
            Everything copy = EverythingBuilder.builder(original).build();

            assertThat(copy).isEqualTo(original);
        }

        @Test
        void shouldThrowExceptionWhenPrototypeIsNull() {
            assertThatThrownBy(() -> EverythingBuilder.builder(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class MergeMethodTests {
        @Test
        void shouldMergePrimitiveFields() {
            // Create a source record with primitive values
            Everything source = EverythingBuilder.builder()
                    .setByte_((byte) 5)
                    .setShort_((short) 10)
                    .setInt_(50)
                    .setLong_(500L)
                    .setFloat_(2.5f)
                    .setDouble_(3.5)
                    .setChar_('Y')
                    .setBoolean_(false)
                    .setString("source")
                    .setLocalDate(LocalDate.of(2024, 6, 1))
                    .setJavaRecord(new Everything.JavaRecord("record"))
                    .setJavaClass(createJavaClass("class"))
                    .addAllListString(List.of("X"))
                    .addAllSetString(Set.of("S"))
                    .putAllMapStringInteger(Map.of("m", 1))
                    .addAllArrayListString(new ArrayList<>())
                    .putAllHashMapStringInteger(new HashMap<>())
                    .addAllHashSetString(new HashSet<>())
                    .build();

            // Merge into an empty builder
            Everything result = EverythingBuilder.builder().merge(source).build();

            assertThat(result).isEqualTo(source);
        }

        @Test
        void shouldSkipNullReferenceFields() {
            // Create source with nullable fields set to null
            Everything source = EverythingBuilder.builder()
                    .setInt_(42)
                    .setString("test")
                    .setLocalDate(LocalDate.now())
                    .setJavaRecord(new Everything.JavaRecord("record"))
                    .setJavaClass(createJavaClass("class"))
                    .addAllListString(List.of("A"))
                    .addAllSetString(Set.of("S"))
                    .putAllMapStringInteger(Map.of("k", 1))
                    .addAllArrayListString(new ArrayList<>())
                    .putAllHashMapStringInteger(new HashMap<>())
                    .addAllHashSetString(new HashSet<>())
                    .build();

            // Merge should skip null reference fields
            EverythingBuilder builder =
                    EverythingBuilder.builder().setNullableString("existing").merge(source);

            // Nullable field should remain as "existing" since source has null
            assertThat(builder.getNullableString()).isEqualTo("existing");
            assertThat(builder.getInt_()).isEqualTo(42);
        }

        @Test
        void shouldThrowExceptionWhenOtherIsNull() {
            EverythingBuilder builder = EverythingBuilder.builder();

            assertThatThrownBy(() -> builder.merge(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("other cannot be null");
        }
    }

    @Nested
    class GetterMethodTests {
        @Test
        void shouldReturnCorrectValuesForAllFieldTypes() {
            LocalDate date = LocalDate.of(2024, 3, 20);
            Everything.JavaRecord javaRecord = new Everything.JavaRecord("test");
            Everything.JavaClass javaClass = createJavaClass("class");

            EverythingBuilder builder = EverythingBuilder.builder()
                    .setByte_((byte) 7)
                    .setInt_(123)
                    .setDouble_(9.99)
                    .setChar_('A')
                    .setBoolean_(true)
                    .setString("getter-test")
                    .setNullableString("nullable")
                    .setLocalDate(date)
                    .setJavaRecord(javaRecord)
                    .setJavaClass(javaClass)
                    .addAllListString(List.of("L1", "L2"))
                    .addAllSetString(Set.of("S1"))
                    .putAllMapStringInteger(Map.of("key", 999));

            // Test all getter methods
            assertThat(builder.getByte_()).isEqualTo((byte) 7);
            assertThat(builder.getInt_()).isEqualTo(123);
            assertThat(builder.getDouble_()).isEqualTo(9.99);
            assertThat(builder.getChar_()).isEqualTo('A');
            assertThat(builder.getBoolean_()).isEqualTo(true);
            assertThat(builder.getString()).isEqualTo("getter-test");
            assertThat(builder.getNullableString()).isEqualTo("nullable");
            assertThat(builder.getLocalDate()).isEqualTo(date);
            assertThat(builder.getJavaRecord()).isEqualTo(javaRecord);
            assertThat(builder.getJavaClass()).isEqualTo(javaClass);
            assertThat(builder.getListString()).isEqualTo(List.of("L1", "L2"));
            assertThat(builder.getSetString()).isEqualTo(Set.of("S1"));
            assertThat(builder.getMapStringInteger()).isEqualTo(Map.of("key", 999));
        }
    }

    @Nested
    class SetterMethodTests {
        @Test
        void shouldSetValidValuesAndReturnBuilder() {
            EverythingBuilder builder = EverythingBuilder.builder();

            // Test setter return type (fluent interface)
            EverythingBuilder result = builder.setInt_(42);
            assertThat(result).isSameAs(builder);

            // Test setters for various types
            builder.setByte_((byte) 1)
                    .setShort_((short) 2)
                    .setInt_(3)
                    .setLong_(4L)
                    .setFloat_(5.0f)
                    .setDouble_(6.0)
                    .setChar_('Z')
                    .setBoolean_(true)
                    .setString("test")
                    .setNullableString("nullable");

            assertThat(builder.getByte_()).isEqualTo((byte) 1);
            assertThat(builder.getShort_()).isEqualTo((short) 2);
            assertThat(builder.getInt_()).isEqualTo(3);
            assertThat(builder.getLong_()).isEqualTo(4L);
            assertThat(builder.getFloat_()).isEqualTo(5.0f);
            assertThat(builder.getDouble_()).isEqualTo(6.0);
            assertThat(builder.getChar_()).isEqualTo('Z');
            assertThat(builder.getBoolean_()).isEqualTo(true);
            assertThat(builder.getString()).isEqualTo("test");
            assertThat(builder.getNullableString()).isEqualTo("nullable");
        }

        @Test
        void shouldValidateNullForNonNullableFields() {
            EverythingBuilder builder = EverythingBuilder.builder();

            // Test that non-nullable fields reject null
            assertThatThrownBy(() -> builder.setString(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("string cannot be null");

            assertThatThrownBy(() -> builder.setLocalDate(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("localDate cannot be null");

            assertThatThrownBy(() -> builder.addAllListString(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("values cannot be null");
        }

        @Test
        void shouldAcceptNullForNullableFields() {
            EverythingBuilder builder = EverythingBuilder.builder();

            // Test that nullable fields accept null
            builder.setNullableString(null);
            assertThat(builder.getNullableString()).isNull();
            assertThat(builder.hasNullableString()).isTrue();

            builder.setNullableLocalDate(null);
            assertThat(builder.getNullableLocalDate()).isNull();
            assertThat(builder.hasNullableLocalDate()).isTrue();
        }
    }

    @Nested
    class AdderMethodTests {
        @Test
        void shouldAddElementsToCollections() {
            EverythingBuilder builder = EverythingBuilder.builder();

            // Test adding elements to list
            builder.addListString("one").addListString("two");
            assertThat(builder.getListString()).containsExactly("one", "two");

            // Test adding multiple elements to list
            builder.addAllListString(List.of("three", "four"));
            assertThat(builder.getListString()).containsExactly("one", "two", "three", "four");

            // Test adding elements to set
            builder.addSetString("A").addSetString("B");
            assertThat(builder.getSetString()).containsExactlyInAnyOrder("A", "B");

            // Test adding multiple elements to set
            builder.addAllSetString(Set.of("C", "D"));
            assertThat(builder.getSetString()).containsExactlyInAnyOrder("A", "B", "C", "D");
        }

        @Test
        void shouldThrowExceptionWhenAddingNullCollection() {
            EverythingBuilder builder = EverythingBuilder.builder();

            assertThatThrownBy(() -> builder.addListString(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("value cannot be null");

            assertThatThrownBy(() -> builder.addSetString(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("value cannot be null");

            assertThatThrownBy(() -> builder.addAllListString(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("values cannot be null");

            assertThatThrownBy(() -> builder.addAllSetString(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("values cannot be null");
        }

        @Test
        void shouldAcceptNullForNullableCollectionFields() {
            EverythingBuilder builder = EverythingBuilder.builder();

            builder.addAllNullableListString(null);
            assertThat(builder.getNullableListString()).isNull();
            assertThat(builder.hasNullableListString()).isTrue();

            builder.addAllNullableSetString(null);
            assertThat(builder.getNullableSetString()).isNull();
            assertThat(builder.hasNullableSetString()).isTrue();
        }
    }

    @Nested
    class PutterMethodTests {
        @Test
        void shouldPutEntriesIntoMaps() {
            EverythingBuilder builder = EverythingBuilder.builder();

            // Test putting single entries
            builder.putMapStringInteger("key1", 1).putMapStringInteger("key2", 2);
            assertThat(builder.getMapStringInteger()).containsOnly(Map.entry("key1", 1), Map.entry("key2", 2));

            // Test putting multiple entries
            builder.putAllMapStringInteger(Map.of("key3", 3, "key4", 4));
            assertThat(builder.getMapStringInteger())
                    .containsOnly(
                            Map.entry("key1", 1), Map.entry("key2", 2), Map.entry("key3", 3), Map.entry("key4", 4));
        }

        @Test
        void shouldThrowExceptionWhenPuttingNullKeyOrValue() {
            EverythingBuilder builder = EverythingBuilder.builder();

            assertThatThrownBy(() -> builder.putMapStringInteger(null, 1))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("key cannot be null");

            assertThatThrownBy(() -> builder.putMapStringInteger("key", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("value cannot be null");

            assertThatThrownBy(() -> builder.putAllMapStringInteger(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("values cannot be null");
        }

        @Test
        void shouldAcceptNullForNullableMapFields() {
            EverythingBuilder builder = EverythingBuilder.builder();

            builder.putAllNullableMapStringInteger(null);
            assertThat(builder.getNullableMapStringInteger()).isNull();
            assertThat(builder.hasNullableMapStringInteger()).isTrue();
        }
    }

    @Nested
    class HasMethodTests {
        @Test
        void shouldReturnFalseForUnsetFields() {
            EverythingBuilder builder = EverythingBuilder.builder();

            // Initially, no fields should be set
            assertThat(builder.hasInt_()).isFalse();
            assertThat(builder.hasString()).isFalse();
            assertThat(builder.hasNullableString()).isFalse();
            assertThat(builder.hasLocalDate()).isFalse();
            assertThat(builder.hasListString()).isFalse();
            assertThat(builder.hasBoolean_()).isFalse();
            assertThat(builder.hasDouble_()).isFalse();
        }

        @Test
        void shouldReturnTrueAfterSettingField() {
            EverythingBuilder builder = EverythingBuilder.builder();

            // Set a field and check has method
            builder.setInt_(100);
            assertThat(builder.hasInt_()).isTrue();
            assertThat(builder.hasString()).isFalse();

            builder.setString("test");
            assertThat(builder.hasString()).isTrue();

            builder.setNullableString(null);
            assertThat(builder.hasNullableString()).isTrue(); // Even null sets the field

            builder.addAllListString(List.of());
            assertThat(builder.hasListString()).isTrue();
        }

        @Test
        void shouldReturnFalseAfterClearingField() {
            EverythingBuilder builder =
                    EverythingBuilder.builder().setInt_(42).setString("test").setDouble_(1.5);

            assertThat(builder.hasInt_()).isTrue();
            assertThat(builder.hasString()).isTrue();
            assertThat(builder.hasDouble_()).isTrue();

            // Clear and check has methods
            builder.clearInt_();
            assertThat(builder.hasInt_()).isFalse();
            assertThat(builder.hasString()).isTrue(); // Other fields unchanged

            builder.clearString();
            assertThat(builder.hasString()).isFalse();

            builder.clearDouble_();
            assertThat(builder.hasDouble_()).isFalse();
        }
    }

    @Nested
    class ClearMethodTests {
        @Test
        void shouldResetPrimitiveFieldsToZeroValues() {
            EverythingBuilder builder = EverythingBuilder.builder()
                    .setByte_((byte) 10)
                    .setInt_(100)
                    .setLong_(1000L)
                    .setFloat_(1.5f)
                    .setDouble_(2.5)
                    .setChar_('X')
                    .setBoolean_(true);

            // Clear primitive fields - should reset to zero/false/null char
            builder.clearByte_();
            assertThat(builder.getByte_()).isEqualTo((byte) 0);
            assertThat(builder.hasByte_()).isFalse();

            builder.clearInt_();
            assertThat(builder.getInt_()).isEqualTo(0);
            assertThat(builder.hasInt_()).isFalse();

            builder.clearLong_();
            assertThat(builder.getLong_()).isEqualTo(0L);
            assertThat(builder.hasLong_()).isFalse();

            builder.clearFloat_();
            assertThat(builder.getFloat_()).isEqualTo(0.0f);
            assertThat(builder.hasFloat_()).isFalse();

            builder.clearChar_();
            assertThat(builder.getChar_()).isEqualTo('\u0000');
            assertThat(builder.hasChar_()).isFalse();

            // Double and Boolean are boxed types (not primitives), so their getters
            // will throw NPE after clearing - only test has() method
            builder.clearDouble_();
            assertThat(builder.hasDouble_()).isFalse();

            builder.clearBoolean_();
            assertThat(builder.hasBoolean_()).isFalse();
        }

        @Test
        void clearXxxShouldClearReferenceFieldToNullThenHasXxxShouldReturnFalse() {
            LocalDate date = LocalDate.now();
            EverythingBuilder builder = EverythingBuilder.builder()
                    .setString("test")
                    .setNullableString("nullable")
                    .setLocalDate(date)
                    .addAllListString(List.of("A", "B"))
                    .addAllSetString(Set.of("X"))
                    .putAllMapStringInteger(Map.of("key", 1));

            builder.clearNullableString();
            assertThat(builder.getNullableString()).isNull();
            assertThat(builder.hasNullableString()).isFalse();

            builder.clearString();
            assertThat(builder.getString()).isNull();
            assertThat(builder.hasString()).isFalse();

            builder.clearLocalDate();
            assertThat(builder.getLocalDate()).isNull();
            assertThat(builder.hasLocalDate()).isFalse();

            builder.clearListString();
            assertThat(builder.getListString()).isNull();
            assertThat(builder.hasListString()).isFalse();

            builder.clearSetString();
            assertThat(builder.getSetString()).isNull();
            assertThat(builder.hasSetString()).isFalse();

            builder.clearMapStringInteger();
            assertThat(builder.getMapStringInteger()).isNull();
            assertThat(builder.hasMapStringInteger()).isFalse();
        }

        @Test
        void clearMethodShouldClearAllFields() {
            EverythingBuilder builder = EverythingBuilder.builder()
                    .setInt_(1)
                    .setBoolean_(true)
                    .setString("string")
                    .setNullableString("nullable")
                    .addAllListString(List.of("a"));

            builder.clear();

            assertThat(builder).usingRecursiveComparison().isEqualTo(EverythingBuilder.builder());
        }
    }

    @Nested
    class ToStringMethodTests {
        @Test
        void onlyContainsSetFields() {
            EverythingBuilder builder =
                    EverythingBuilder.builder().setInt_(42).setBoolean_(true).setString("example");

            String actual = builder.toString();

            String expected = "EverythingBuilder{int_=42, boolean_=true, string=example}";
            assertThat(actual).isEqualTo(expected);
        }
    }

    private static JavaFileObject loadTestSource(Class<?> clazz) throws IOException {
        Path projectDir = Path.of("").toAbsolutePath();
        Path testSourceRoot = projectDir.resolve("src/test/java");
        String relativePath = clazz.getName().replace('.', '/') + ".java";
        return JavaFileObjects.forSourceString(
                clazz.getCanonicalName(), Files.readString(testSourceRoot.resolve(relativePath)));
    }

    private static Everything.JavaClass createJavaClass(String value) {
        Everything.JavaClass obj = new Everything.JavaClass();
        obj.field = value;
        return obj;
    }
}
