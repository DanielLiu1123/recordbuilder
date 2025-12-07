package recordbuilder;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Analyzes types and their characteristics for the record builder processor.
 *
 * @author Freeman
 */
final class TypeAnalyzer {

    TypeAnalyzer() {}

    /**
     * Checks if a record component is nullable.
     * Checks both element annotations and type annotations.
     */
    boolean isNullable(RecordComponentElement component) {
        // Check element annotations (for @Nullable on the parameter itself)
        boolean hasElementAnnotation = component.getAnnotationMirrors().stream().anyMatch(this::isNullableAnnotation);

        // Check type annotations (for @Nullable on the type, like jspecify)
        boolean hasTypeAnnotation =
                component.asType().getAnnotationMirrors().stream().anyMatch(this::isNullableAnnotation);

        return hasElementAnnotation || hasTypeAnnotation;
    }

    /**
     * Checks if a type is annotated with @Nullable.
     */
    boolean isTypeNullable(TypeMirror type) {
        return type.getAnnotationMirrors().stream().anyMatch(this::isNullableAnnotation);
    }

    /**
     * Checks if an annotation is a nullable annotation.
     * Supports multiple nullable annotation types.
     */
    boolean isNullableAnnotation(AnnotationMirror annotation) {
        String qualifiedName = ((TypeElement) annotation.getAnnotationType().asElement())
                .getQualifiedName()
                .toString();
        return qualifiedName.equals(Constants.JSPECIFY_NULLABLE)
                || qualifiedName.equals(Constants.JAVAX_NULLABLE)
                || qualifiedName.equals(Constants.JAKARTA_NULLABLE)
                || qualifiedName.equals(Constants.JETBRAINS_NULLABLE)
                || qualifiedName.equals(Constants.ANDROIDX_NULLABLE)
                || qualifiedName.equals(Constants.CHECKERFRAMEWORK_NULLABLE)
                || qualifiedName.equals(Constants.FINDBUGS_NULLABLE);
    }

    /**
     * Checks if a record component is a primitive type.
     */
    boolean isPrimitive(RecordComponentElement component) {
        return component.asType().getKind().isPrimitive();
    }

    /**
     * Gets the primitive zero value for a component.
     *
     * @throws IllegalArgumentException if the component is not a primitive type
     */
    String getPrimitiveZeroValue(RecordComponentElement component) {
        TypeKind kind = component.asType().getKind();
        return switch (kind) {
            case BOOLEAN -> "false";
            case BYTE, SHORT, INT, LONG -> "0";
            case CHAR -> "'\\0'";
            case FLOAT -> "0.0f";
            case DOUBLE -> "0.0";
            default -> throw new IllegalArgumentException("Not a primitive type: " + kind);
        };
    }
}
