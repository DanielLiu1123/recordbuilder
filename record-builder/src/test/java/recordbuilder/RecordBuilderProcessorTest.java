package recordbuilder;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

class RecordBuilderProcessorTest {

    @Test
    void testRecordBuilderProcessor() throws Exception {
        JavaFileObject source = loadTestSource(Everything.class);

        Compilation compilation =
                Compiler.javac().withProcessors(new RecordBuilderProcessor()).compile(source);

        CompilationSubject.assertThat(compilation).succeeded();
        CompilationSubject.assertThat(compilation)
                .generatedSourceFile("recordbuilder.EverythingBuilder")
                .contentsAsUtf8String()
                .contains("getString()");
    }

    @Test
    void testEverything() {
        EverythingBuilder builder = EverythingBuilder.of();
        builder.setInt_(42);
        builder.setDouble_(3.14);
        builder.setBoolean_(true);
        builder.setString("Test String");
        builder.setLocalDate(LocalDate.now());
        builder.setNullableString("Hello");
        builder.setListString(List.of("A", "B", "C"));
        Everything record = builder.build();

        assertThat(record).isNotSameAs(EverythingBuilder.from(record).build());
        assertThat(record).isEqualTo(EverythingBuilder.from(record).build());
        assertThat(record)
                .isNotEqualTo(EverythingBuilder.from(record).clearListString().build());
    }

    private static JavaFileObject loadTestSource(Class<?> clazz) throws IOException {
        Path projectDir = Path.of("").toAbsolutePath();
        Path testSourceRoot = projectDir.resolve("src/test/java");
        String relativePath = clazz.getName().replace('.', '/') + ".java";
        return JavaFileObjects.forSourceString(
                clazz.getCanonicalName(), Files.readString(testSourceRoot.resolve(relativePath)));
    }
}
