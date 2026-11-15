package recordbuilder;

import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.MethodSpec;
import java.util.Arrays;
import javax.lang.model.element.Modifier;

/** Generates the field and bit operations that track which Builder properties have been set. */
final class PresenceMask {
    private static final String FIELD_NAME = "presenceMask";

    private final int fieldCount;

    PresenceMask(int fieldCount) {
        this.fieldCount = fieldCount;
    }

    FieldSpec field() {
        if (usesArray()) {
            int wordCount = (fieldCount + Long.SIZE - 1) / Long.SIZE;
            return FieldSpec.builder(long[].class, FIELD_NAME, Modifier.PRIVATE)
                    .initializer("new long[$L]", wordCount)
                    .build();
        }
        return FieldSpec.builder(usesInt() ? int.class : long.class, FIELD_NAME, Modifier.PRIVATE)
                .build();
    }

    CodeBlock setBit(int index) {
        return CodeBlock.of("$L |= $L", word(index), bit(index));
    }

    CodeBlock clearBit(int index) {
        return CodeBlock.of("$L &= ~$L", word(index), bit(index));
    }

    CodeBlock isSet(int index) {
        return CodeBlock.of("($L & $L) != 0", word(index), bit(index));
    }

    void addResetTo(MethodSpec.Builder method) {
        if (fieldCount == 0) {
            return;
        }
        if (usesArray()) {
            method.addStatement("$T.fill(this.$L, 0L)", Arrays.class, FIELD_NAME);
        } else {
            method.addStatement("this.$L = $L", FIELD_NAME, usesInt() ? "0" : "0L");
        }
    }

    private CodeBlock word(int index) {
        if (usesArray()) {
            return CodeBlock.of("this.$L[$L]", FIELD_NAME, index / Long.SIZE);
        }
        return CodeBlock.of("this.$L", FIELD_NAME);
    }

    private CodeBlock bit(int index) {
        int offset = usesArray() ? index % Long.SIZE : index;
        return CodeBlock.of("($L << $L)", usesInt() ? "1" : "1L", offset);
    }

    private boolean usesInt() {
        return fieldCount <= Integer.SIZE;
    }

    private boolean usesArray() {
        return fieldCount > Long.SIZE;
    }
}
