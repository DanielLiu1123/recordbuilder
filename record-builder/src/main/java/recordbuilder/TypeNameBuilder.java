package recordbuilder;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import java.util.List;
import java.util.Map;
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

        if (typeAnalyzer.isCollection(component)) {
            return buildCollectionFieldType(component);
        } else if (typeAnalyzer.isMap(component)) {
            return buildMapFieldType(component);
        }

        // For non-collection and non-map types, use getTypeNameWithAnnotations
        return getTypeNameWithAnnotations(type);
    }

    /**
     * Builds TypeName for collection fields.
     */
    private TypeName buildCollectionFieldType(RecordComponentElement component) {
        TypeMirror type = component.asType();

        TypeMirror elementType = getTypeArgument(type, 0);
        TypeName elementTypeName = getTypeNameWithAnnotations(elementType);

        TypeName result;
        if (typeAnalyzer.isConcreteCollectionType(type)) {
            // Preserve concrete type (ArrayList, HashSet, etc.)
            TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
            ClassName concreteClass = ClassName.get(typeElement);
            result = ParameterizedTypeName.get(concreteClass, elementTypeName);
        } else {
            // Use interface type (List, Set, Collection)
            ClassName interfaceClass = CollectionHelper.getCollectionInterfaceClass(type);
            result = ParameterizedTypeName.get(interfaceClass, elementTypeName);
        }

        // Preserve @Nullable annotation on the collection itself if present
        if (typeAnalyzer.isNullable(component)) {
            ClassName nullableAnnotation = getNullableAnnotation(component);
            result = result.annotated(AnnotationSpec.builder(nullableAnnotation).build());
        }

        return result;
    }

    /**
     * Builds TypeName for map fields.
     */
    private TypeName buildMapFieldType(RecordComponentElement component) {
        TypeMirror type = component.asType();

        TypeMirror keyType = getTypeArgument(type, 0);
        TypeMirror valueType = getTypeArgument(type, 1);
        TypeName keyTypeName = getTypeNameWithAnnotations(keyType);
        TypeName valueTypeName = getTypeNameWithAnnotations(valueType);

        TypeName result;
        if (typeAnalyzer.isConcreteMapType(type)) {
            // Preserve concrete type (HashMap, TreeMap, etc.)
            TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
            ClassName concreteClass = ClassName.get(typeElement);
            result = ParameterizedTypeName.get(concreteClass, keyTypeName, valueTypeName);
        } else {
            // Use interface type (Map)
            result = ParameterizedTypeName.get(ClassName.get(Map.class), keyTypeName, valueTypeName);
        }

        // Preserve @Nullable annotation on the map itself if present
        if (typeAnalyzer.isNullable(component)) {
            ClassName nullableAnnotation = getNullableAnnotation(component);
            result = result.annotated(AnnotationSpec.builder(nullableAnnotation).build());
        }

        return result;
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
     * Gets the nullable annotation from a record component.
     * Checks both element and type annotations.
     */
    ClassName getNullableAnnotation(RecordComponentElement component) {
        // Check element annotations
        for (AnnotationMirror annotation : component.getAnnotationMirrors()) {
            if (typeAnalyzer.isNullableAnnotation(annotation)) {
                return createClassName(annotation);
            }
        }

        // Check type annotations
        for (AnnotationMirror annotation : component.asType().getAnnotationMirrors()) {
            if (typeAnalyzer.isNullableAnnotation(annotation)) {
                return createClassName(annotation);
            }
        }

        // Default fallback
        return ClassName.get("org.jspecify.annotations", "Nullable");
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

    /**
     * Gets the type argument at the specified index from a parameterized type.
     */
    TypeMirror getTypeArgument(TypeMirror type, int index) {
        if (type.getKind() == TypeKind.DECLARED) {
            DeclaredType declaredType = (DeclaredType) type;
            List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
            if (typeArguments.size() > index) {
                return typeArguments.get(index);
            }
        }
        return elementUtils.getTypeElement("java.lang.Object").asType();
    }
}
