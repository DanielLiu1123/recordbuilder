package recordbuilder;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Analyzes types and their characteristics for the record builder processor.
 *
 * @author Freeman
 */
final class TypeAnalyzer {

    private final Elements elementUtils;
    private final Types typeUtils;

    TypeAnalyzer(Elements elementUtils, Types typeUtils) {
        this.elementUtils = elementUtils;
        this.typeUtils = typeUtils;
    }

    /**
     * Checks if a record component is a collection type.
     */
    boolean isCollection(RecordComponentElement component) {
        TypeMirror type = component.asType();
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }

        TypeElement collectionType = elementUtils.getTypeElement("java.util.Collection");
        if (collectionType == null) {
            return false;
        }

        TypeMirror collectionTypeMirror = typeUtils.erasure(collectionType.asType());
        TypeMirror erasedType = typeUtils.erasure(type);
        return typeUtils.isAssignable(erasedType, collectionTypeMirror);
    }

    /**
     * Checks if a record component is a map type.
     */
    boolean isMap(RecordComponentElement component) {
        TypeMirror type = component.asType();
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }

        TypeElement mapType = elementUtils.getTypeElement("java.util.Map");
        if (mapType == null) {
            return false;
        }

        TypeMirror mapTypeMirror = typeUtils.erasure(mapType.asType());
        TypeMirror erasedType = typeUtils.erasure(type);
        return typeUtils.isAssignable(erasedType, mapTypeMirror);
    }

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
     * Checks if a type is a concrete collection implementation class
     * (ArrayList, HashSet, LinkedHashSet, TreeSet, etc.)
     */
    boolean isConcreteCollectionType(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }

        TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
        String qualifiedName = typeElement.getQualifiedName().toString();

        return qualifiedName.equals("java.util.ArrayList")
                || qualifiedName.equals("java.util.HashSet")
                || qualifiedName.equals("java.util.LinkedHashSet")
                || qualifiedName.equals("java.util.TreeSet")
                || qualifiedName.equals("java.util.LinkedList")
                || qualifiedName.equals("java.util.ArrayDeque")
                || qualifiedName.equals("java.util.Vector")
                || qualifiedName.equals("java.util.Stack")
                || qualifiedName.equals("java.util.concurrent.CopyOnWriteArrayList")
                || qualifiedName.equals("java.util.concurrent.CopyOnWriteArraySet")
                || qualifiedName.equals("java.util.concurrent.ConcurrentSkipListSet");
    }

    /**
     * Checks if a type is a concrete map implementation class
     * (HashMap, TreeMap, LinkedHashMap, etc.)
     */
    boolean isConcreteMapType(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }

        TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
        String qualifiedName = typeElement.getQualifiedName().toString();

        return qualifiedName.equals("java.util.HashMap")
                || qualifiedName.equals("java.util.TreeMap")
                || qualifiedName.equals("java.util.LinkedHashMap")
                || qualifiedName.equals("java.util.WeakHashMap")
                || qualifiedName.equals("java.util.IdentityHashMap")
                || qualifiedName.equals("java.util.Hashtable")
                || qualifiedName.equals("java.util.Properties")
                || qualifiedName.equals("java.util.concurrent.ConcurrentHashMap")
                || qualifiedName.equals("java.util.concurrent.ConcurrentSkipListMap");
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
