package recordbuilder;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class GenerationRegressionTest {
    @ParameterizedTest(name = "things: {0}, allThings: {1}")
    @CsvSource(textBlock = """
            java.util.ArrayList<String>, java.util.List<java.util.List<String>>
            java.util.ArrayList<String>, java.util.List<java.io.Serializable>
            java.util.ArrayList<String>, java.util.List<? extends java.util.List<String>>
            java.util.ArrayList<String>, java.util.List<? super java.util.List<String>>
            java.util.ArrayList<String>, java.util.List<?>
            java.util.ArrayList<String>, java.util.List
            java.util.List<? extends String>, java.util.List<java.util.List<String>>
            java.util.List<? super String>, java.util.List<java.util.List<String>>
            java.util.List<?>, java.util.List<java.util.List<String>>
            java.util.List, java.util.List<java.util.List<String>>
            """)
    void mergeSelectsBulkAdderDespiteOverlappingNames(String thingsType, String allThingsType) throws Exception {
        Compilation compilation = compile("Sample", """
                package test;
                @recordbuilder.RecordBuilder public record Sample(%s things, %s allThings) {
                    public static Sample source() {
                        return new Sample(
                            new java.util.ArrayList<>(java.util.List.of("value")),
                            java.util.List.of(new java.util.ArrayList<>(java.util.List.of("nested"))));
                    }
                    public static Sample copy() {
                        return SampleBuilder.builder(source()).build();
                    }
                    public static Sample merge() {
                        return SampleBuilder.builder().addThings("existing").merge(source()).build();
                    }
                }
                """.formatted(thingsType, allThingsType));
        CompilationSubject.assertThat(compilation).succeeded();
        Class<?> sample = loadClass(compilation, "test.Sample");
        Object source = sample.getMethod("source").invoke(null);
        Object copy = sample.getMethod("copy").invoke(null);
        Object merged = sample.getMethod("merge").invoke(null);
        assertThat(copy).as("copy preserves both components").isEqualTo(source);
        assertThat(sample.getMethod("things").invoke(merged))
                .as("merge appends to the existing things")
                .isEqualTo(List.of("existing", "value"));
        assertThat(sample.getMethod("allThings").invoke(merged))
                .as("merge preserves allThings without adding things to it")
                .isEqualTo(sample.getMethod("allThings").invoke(source));
    }

    @Test
    void mergeSelectsBulkAdderForGenericComponents() throws Exception {
        Compilation compilation = compile("Sample", """
                package test;
                @recordbuilder.RecordBuilder public record Sample<T>(
                    java.util.List<T> things, java.util.List<java.util.List<T>> allThings) {
                    public static boolean verify() {
                        var source = new Sample<>(java.util.List.of("value"), java.util.List.of(java.util.List.of("nested")));
                        return SampleBuilder.builder(source).build().equals(source);
                    }
                }
                """);
        CompilationSubject.assertThat(compilation).succeeded();
        assertThat(loadClass(compilation, "test.Sample").getMethod("verify").invoke(null))
                .isEqualTo(true);
    }

    @Test
    void unambiguousBulkAdderOverloadsRemainUsable() throws Exception {
        Compilation compilation = compile("Sample", """
                package test;
                @recordbuilder.RecordBuilder public record Sample(
                    java.util.List<String> things, java.util.List<Integer> allThings) {
                    public static boolean verify() {
                        var source = new Sample(java.util.List.of("value"), java.util.List.of(42));
                        return SampleBuilder.builder(source).build().equals(source);
                    }
                }
                """);
        CompilationSubject.assertThat(compilation).succeeded();
        assertThat(loadClass(compilation, "test.Sample").getMethod("verify").invoke(null))
                .isEqualTo(true);
    }

    @Test
    void mergeUsesComponentNamesWithoutParameterCollisions() throws Exception {
        Compilation compilation = compile("Sample", """
                package test;
                @recordbuilder.RecordBuilder
                public record Sample(String name, String other, String other_, int count) {
                    public static boolean verify() {
                        var source = new Sample("name", "other", "other_", 42);
                        return SampleBuilder.builder(source).build().equals(source);
                    }
                }
                """);
        CompilationSubject.assertThat(compilation).succeeded();
        String generated = compilation
                .generatedSourceFile("test.SampleBuilder")
                .orElseThrow()
                .getCharContent(true)
                .toString();
        assertThat(generated)
                .contains("var name = other__.name()", "var other = other__.other()", "var other_ = other__.other_()")
                .doesNotContain("value_name");
        assertThat(loadClass(compilation, "test.Sample").getMethod("verify").invoke(null))
                .isEqualTo(true);
    }

    @Test
    void componentNameDoesNotCollideWithPresenceMask() {
        CompilationSubject.assertThat(compile("Sample", """
                package test;
                @recordbuilder.RecordBuilder public record Sample(int presenceMask0_, int presenceMask) {}
                """)).succeeded();
    }

    @Test
    void generatedMethodNamesAreIndependentOfDefaultLocale() throws IOException {
        var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            Compilation compilation = compile("Sample", """
                    package test;
                    @recordbuilder.RecordBuilder public record Sample(int id) {}
                    """);
            CompilationSubject.assertThat(compilation).succeeded();
            assertThat(compilation
                            .generatedSourceFile("test.SampleBuilder")
                            .orElseThrow()
                            .getCharContent(true)
                            .toString())
                    .contains("setId(", "getId(", "hasId(", "clearId(");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void mergeReadsReferenceAccessorsOnce() throws Exception {
        Compilation compilation = compile("Sample", """
                package test;
                @recordbuilder.RecordBuilder public record Sample(String name, java.util.List<String> items,
                        java.util.Map<String, String> entries) {
                    static int names, lists, maps;
                    public String name() { return ++names == 1 ? name : null; }
                    public java.util.List<String> items() { return ++lists == 1 ? items : null; }
                    public java.util.Map<String, String> entries() { return ++maps == 1 ? entries : null; }
                    public static boolean verify() {
                        var source = new Sample("name", java.util.List.of("item"), java.util.Map.of("key", "value"));
                        var builder = SampleBuilder.builder(source);
                        return names == 1 && lists == 1 && maps == 1
                                && builder.getName().equals("name")
                                && builder.getItems().equals(java.util.List.of("item"))
                                && builder.getEntries().equals(java.util.Map.of("key", "value"));
                    }
                }
                """);
        CompilationSubject.assertThat(compilation).succeeded();
        assertThat(loadClass(compilation, "test.Sample").getMethod("verify").invoke(null))
                .isEqualTo(true);
    }

    @Test
    void parameterizedEnclosingTypesCompile() {
        CompilationSubject.assertThat(compile("Sample", """
                package test;
                class Outer<T> { class Inner<U> {} }
                @recordbuilder.RecordBuilder
                public record Sample(Outer<String>.Inner<Integer> value) {}
                """)).succeeded();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 31, 32, 33, 63, 64, 65, 129})
    void presenceBitsRemainIndependentAcrossWordBoundaries(int count) throws Exception {
        String components =
                IntStream.range(0, count).mapToObj(i -> "int field" + i).collect(Collectors.joining(", "));
        Compilation compilation = compile("Sample", """
                package test;
                @recordbuilder.RecordBuilder
                public record Sample(%s) {}
                """.formatted(components));
        CompilationSubject.assertThat(compilation).succeeded();
        Class<?> type = loadClass(compilation, "test.SampleBuilder");
        Object builder = type.getMethod("builder").invoke(null);
        for (int i = 0; i < count; i++) {
            assertThat(type.getMethod("hasField" + i).invoke(builder)).isEqualTo(false);
            type.getMethod("setField" + i, int.class).invoke(builder, 0);
            for (int j = 0; j < count; j++) {
                assertThat(type.getMethod("hasField" + j).invoke(builder)).isEqualTo(j <= i);
            }
        }
        for (int i = 0; i < count; i++) {
            type.getMethod("clearField" + i).invoke(builder);
            for (int j = 0; j < count; j++) {
                assertThat(type.getMethod("hasField" + j).invoke(builder)).isEqualTo(j > i);
            }
        }
        for (int i = 0; i < count; i++) {
            type.getMethod("setField" + i, int.class).invoke(builder, 42);
        }
        type.getMethod("clear").invoke(builder);
        for (int i = 0; i < count; i++) {
            assertThat(type.getMethod("hasField" + i).invoke(builder)).isEqualTo(false);
            assertThat(type.getMethod("getField" + i).invoke(builder)).isEqualTo(0);
        }
        assertThat(type.getMethod("build").invoke(builder)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "java.util.Collection", "java.util.List", "java.util.Set", "java.util.Queue", "java.util.Deque",
                "java.util.ArrayList", "java.util.LinkedList", "java.util.HashSet", "java.util.SortedSet",
                        "java.util.TreeSet",
                "java.util.concurrent.CopyOnWriteArrayList"
            })
    void mappedCollectionsCompile(String type) {
        assertCollectionCompiles(type);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "java.util.Map", "java.util.HashMap", "java.util.LinkedHashMap", "java.util.SortedMap",
                "java.util.NavigableMap", "java.util.Hashtable", "java.util.IdentityHashMap", "java.util.TreeMap",
                "java.util.concurrent.ConcurrentMap", "java.util.concurrent.ConcurrentHashMap"
            })
    void mappedMapsCompile(String type) {
        assertMapCompiles(type);
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    void sequencedCollectionsCompileAndPreserveSetOrder() throws Exception {
        assertCollectionCompiles("java.util.SequencedCollection");
        assertMapCompiles("java.util.SequencedMap");
        Compilation compilation = compile("Sample", """
                package test;
                @recordbuilder.RecordBuilder
                public record Sample(java.util.SequencedSet<String> values) {}
                """);
        CompilationSubject.assertThat(compilation).succeeded();
        Class<?> type = loadClass(compilation, "test.SampleBuilder");
        Object builder = type.getMethod("builder").invoke(null);
        type.getMethod("addValues", String.class).invoke(builder, "first");
        type.getMethod("addAllValues", Iterable.class).invoke(builder, java.util.List.of("second", "first"));
        assertThat((Iterable<?>) type.getMethod("getValues").invoke(builder))
                .extracting(Object::toString)
                .containsExactly("first", "second");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "java.util.List<? extends Number>",
                "java.util.List<? super Integer>",
                "java.util.Map<? extends Number, ? super Integer>",
                "java.util.List",
                "java.util.Map",
                "java.util.SortedSet<? extends Number>",
                "java.util.NavigableSet<? super Integer>",
                "java.util.SortedMap<? extends Number, ? super Integer>",
                "java.util.SortedSet",
                "java.util.SortedMap"
            })
    void wildcardAndRawTypesCompile(String type) {
        CompilationSubject.assertThat(compile("Sample", """
                package test;
                @recordbuilder.RecordBuilder
                public record Sample(%s values) {}
                """.formatted(type))).succeeded();
    }

    @Test
    void generatedSourceIsDeterministic() throws IOException {
        String source = "package test; @recordbuilder.RecordBuilder public record Sample(String name) {}";
        Compilation first = compile("Sample", source);
        Compilation second = compile("Sample", source);
        CompilationSubject.assertThat(first).succeeded();
        CompilationSubject.assertThat(second).succeeded();
        String generated = first.generatedSourceFile("test.SampleBuilder")
                .orElseThrow()
                .getCharContent(true)
                .toString();
        assertThat(generated)
                .doesNotContain("date =")
                .isEqualTo(second.generatedSourceFile("test.SampleBuilder")
                        .orElseThrow()
                        .getCharContent(true)
                        .toString());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "private record Hidden() {}",
                "private static class Container { @recordbuilder.RecordBuilder public record Hidden() {} }"
            })
    void privateRecordsOrEnclosingTypesProduceActionableDiagnostics(String declaration) {
        String annotation = declaration.startsWith("private record") ? "@recordbuilder.RecordBuilder " : "";
        Compilation compilation =
                compile("Outer", "package test; public class Outer { " + annotation + declaration + " }");
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining("accessible from its package");
    }

    @Test
    void protectedNestedRecordsGeneratePackagePrivateBuilders() {
        CompilationSubject.assertThat(compile("Outer", """
                package test;
                public class Outer {
                    @recordbuilder.RecordBuilder protected record Item(String value) {}
                }
                """)).succeeded();
    }

    @Test
    void sameSimpleNamesGenerateDistinctUsableBuilders() throws Exception {
        Compilation compilation = compile("A", """
                package test;
                public class A {
                    @recordbuilder.RecordBuilder public record Item<T>(T value) {}
                    public static boolean verify() {
                        Item<String> a = A_ItemBuilder.<String>builder().setValue("a").build();
                        B.Item b = B_ItemBuilder.builder().setValue(42).build();
                        return A_ItemBuilder.builder(a).build().equals(a)
                                && B_ItemBuilder.builder().merge(b).build().equals(b);
                    }
                }
                class B {
                    @recordbuilder.RecordBuilder record Item(int value) {}
                }
                """);
        CompilationSubject.assertThat(compilation).succeeded();
        assertThat(loadClass(compilation, "test.A").getMethod("verify").invoke(null))
                .isEqualTo(true);
    }

    @Test
    void fullEnclosingPathDistinguishesNestedNames() throws Exception {
        Compilation compilation = compile("Outer", """
                package test;
                public class Outer {
                    static class AB { @recordbuilder.RecordBuilder record Item() {} }
                    static class A {
                        static class B { @recordbuilder.RecordBuilder record Item() {} }
                    }
                    public static boolean verify() {
                        AB.Item first = Outer_AB_ItemBuilder.builder().build();
                        A.B.Item second = Outer_A_B_ItemBuilder.builder().build();
                        return first.equals(new AB.Item()) && second.equals(new A.B.Item());
                    }
                }
                """);
        CompilationSubject.assertThat(compilation).succeeded();
        assertThat(loadClass(compilation, "test.Outer").getMethod("verify").invoke(null))
                .isEqualTo(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"class A_ItemBuilder {}", "@recordbuilder.RecordBuilder record A_Item() {}"})
    void nestedBuilderNameCollisionsProduceActionableDiagnostics(String conflictingType) {
        Compilation compilation = compile("A", """
                package test;
                public class A {
                    @recordbuilder.RecordBuilder public record Item() {}
                }
                %s
                """.formatted(conflictingType));
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation)
                .hadErrorContaining("Cannot generate A_ItemBuilder: the builder name conflicts");
    }

    @Test
    void existingBuilderTypesProduceActionableDiagnostics() {
        Compilation compilation = compile("Sample", """
                package test;
                @recordbuilder.RecordBuilder public record Sample() {}
                class SampleBuilder {}
                """);
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining("builder name conflicts");
    }

    private static void assertCollectionCompiles(String type) {
        CompilationSubject.assertThat(compile("Sample", """
                package test;
                @recordbuilder.RecordBuilder
                public record Sample(%s<String> values) {}
                """.formatted(type))).succeeded();
    }

    private static void assertMapCompiles(String type) {
        CompilationSubject.assertThat(compile("Sample", """
                package test;
                @recordbuilder.RecordBuilder
                public record Sample(%s<String, Integer> values) {}
                """.formatted(type))).succeeded();
    }

    private static Compilation compile(String name, String source) {
        return Compiler.javac()
                .withProcessors(new RecordBuilderProcessor())
                .compile(JavaFileObjects.forSourceString("test." + name, source));
    }

    private static Class<?> loadClass(Compilation compilation, String name) throws ClassNotFoundException {
        return new ClassLoader(GenerationRegressionTest.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String className) throws ClassNotFoundException {
                var file = compilation.generatedFile(
                        StandardLocation.CLASS_OUTPUT, className.replace('.', '/') + ".class");
                if (file.isEmpty()) {
                    throw new ClassNotFoundException(className);
                }
                try (var stream = file.orElseThrow().openInputStream()) {
                    byte[] bytes = stream.readAllBytes();
                    return defineClass(className, bytes, 0, bytes.length);
                } catch (IOException e) {
                    throw new ClassNotFoundException(className, e);
                }
            }
        }.loadClass(name);
    }
}
