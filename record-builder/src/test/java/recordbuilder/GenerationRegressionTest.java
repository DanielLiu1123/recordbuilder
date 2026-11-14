package recordbuilder;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GenerationRegressionTest {
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
                "java.util.Map"
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
    void duplicateNestedNamesProduceActionableDiagnostics() {
        Compilation compilation = compile("Outer", """
                package test;
                public class Outer {
                    static class A { @recordbuilder.RecordBuilder record Item() {} }
                    static class B { @recordbuilder.RecordBuilder record Item() {} }
                }
                """);
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining("builder name conflicts");
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
