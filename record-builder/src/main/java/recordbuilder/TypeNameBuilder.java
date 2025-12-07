package recordbuilder;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/**
 * Builds TypeName instances with proper annotation handling.
 *
 * @author Freeman
 */
final class TypeNameBuilder {

    private final Elements elementUtils;
    private final TypeAnalyzer typeAnalyzer;

    TypeNameBuilder(Elements elementUtils, TypeAnalyzer typeAnalyzer) {
        this.elementUtils = elementUtils;
        this.typeAnalyzer = typeAnalyzer;
    }

    /**
     * Gets the TypeName for a record component, preserving all annotations.
     * For non-primitive, non-nullable fields in the record, adds @Nullable to the builder field
     * since builder fields can be null during construction.
     */
    TypeName getFieldType(RecordComponentElement component) {
        TypeMirror type = component.asType();

        // For non-collection and non-map types, use getTypeNameWithAnnotations
        return getTypeNameWithAnnotations(type);
    }

    /**
     * Gets a TypeName with all type annotations preserved.
     * Recursively handles parameterized types to preserve nested annotations.
     */
    TypeName getTypeNameWithAnnotations(TypeMirror type) {
        TypeName typeName = TypeName.get(type);

        // Recursively handle parameterized types to preserve nested annotations
        if (type.getKind() == TypeKind.DECLARED) {
            DeclaredType declaredType = (DeclaredType) type;
            List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();

            if (!typeArguments.isEmpty()) {
                // Recursively process each type argument
                TypeName[] typeArgumentNames = new TypeName[typeArguments.size()];
                for (int i = 0; i < typeArguments.size(); i++) {
                    typeArgumentNames[i] = getTypeNameWithAnnotations(typeArguments.get(i));
                }

                // Reconstruct the parameterized type
                TypeElement typeElement = (TypeElement) declaredType.asElement();
                ClassName rawType = ClassName.get(typeElement);
                typeName = ParameterizedTypeName.get(rawType, typeArgumentNames);
            }
        }

        // Add @Nullable annotation to the top-level type if present
        if (typeAnalyzer.isTypeNullable(type)) {
            ClassName nullableAnnotation = getNullableAnnotationFromType(type);
            typeName = typeName.annotated(
                    AnnotationSpec.builder(nullableAnnotation).build());
        }

        return typeName;
    }

    /**
     * Gets the nullable annotation from a type.
     */
    ClassName getNullableAnnotationFromType(TypeMirror type) {
        for (AnnotationMirror annotation : type.getAnnotationMirrors()) {
            if (typeAnalyzer.isNullableAnnotation(annotation)) {
                return createClassName(annotation);
            }
        }

        // Default fallback
        return ClassName.get("org.jspecify.annotations", "Nullable");
    }

    /**
     * Creates a ClassName from an annotation mirror.
     */
    private ClassName createClassName(AnnotationMirror annotation) {
        TypeElement annotationType =
                (TypeElement) annotation.getAnnotationType().asElement();
        String packageName =
                elementUtils.getPackageOf(annotationType).getQualifiedName().toString();
        String simpleName = annotationType.getSimpleName().toString();
        return ClassName.get(packageName, simpleName);
    }
}
