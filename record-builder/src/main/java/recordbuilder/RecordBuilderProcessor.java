package recordbuilder;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * Annotation processor for generating builder classes for records annotated with {@link RecordBuilder}.
 *
 * @author Freeman
 */
public final class RecordBuilderProcessor extends AbstractProcessor {

    private Filer filer;
    private Messager messager;
    private Elements elementUtils;
    private Types typeUtils;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(RecordBuilder.class.getCanonicalName());
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.filer = processingEnv.getFiler();
        this.messager = processingEnv.getMessager();
        this.elementUtils = processingEnv.getElementUtils();
        this.typeUtils = processingEnv.getTypeUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(RecordBuilder.class)) {
            if (element.getKind() != ElementKind.RECORD) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@RecordBuilder can only be applied to records", element);
                continue;
            }

            TypeElement recordElement = (TypeElement) element;
            try {
                generateBuilder(recordElement);
            } catch (IOException e) {
                messager.printMessage(
                        Diagnostic.Kind.ERROR, "Failed to generate builder: " + e.getMessage(), recordElement);
            }
        }
        return true;
    }

    private void generateBuilder(TypeElement recordElement) throws IOException {
        String packageName =
                elementUtils.getPackageOf(recordElement).getQualifiedName().toString();
        String recordName = recordElement.getSimpleName().toString();
        String builderName = recordName + "Builder";

        ClassName recordClassName = ClassName.get(packageName, recordName);
        ClassName builderClassName = ClassName.get(packageName, builderName);

        List<? extends RecordComponentElement> components = recordElement.getRecordComponents();

        TypeSpec.Builder builderClassBuilder =
                TypeSpec.classBuilder(builderName).addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        // Add fields
        for (RecordComponentElement component : components) {
            String fieldName = component.getSimpleName().toString();
            TypeName fieldType = getBuilderFieldType(component);

            // Add @Nullable annotation for all non-primitive fields in builder
            // (Builder fields can be null during construction, even if record field is non-null)
            if (!isPrimitive(component) && !isNullable(component)) {
                // For non-nullable fields in the record, add @Nullable as a type annotation to the builder field
                // Use annotated() to ensure correct placement for qualified types (e.g., Outer.@Nullable Inner)
                ClassName nullableAnnotation = ClassName.get("org.jspecify.annotations", "Nullable");
                fieldType = fieldType.annotated(
                        AnnotationSpec.builder(nullableAnnotation).build());
            }

            FieldSpec.Builder fieldBuilder = FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE);
            builderClassBuilder.addField(fieldBuilder.build());
        }

        // Add bitmap field to track which fields have been set.
        // Use int for <= 32 fields, long for <= 64 fields, long[] for > 64 fields
        int fieldCount = components.size();
        if (fieldCount <= 32) {
            builderClassBuilder.addField(FieldSpec.builder(int.class, "_presenceMask0_", Modifier.PRIVATE)
                    .build());
        } else if (fieldCount <= 64) {
            builderClassBuilder.addField(FieldSpec.builder(long.class, "_presenceMask0_", Modifier.PRIVATE)
                    .build());
        } else {
            // For > 64 fields, use long array
            int arraySize = (fieldCount + 63) / 64; // ceil(fieldCount / 64)
            builderClassBuilder.addField(FieldSpec.builder(long[].class, "_presenceMask0_", Modifier.PRIVATE)
                    .initializer("new long[$L]", arraySize)
                    .build());
        }

        // Add private constructor
        builderClassBuilder.addMethod(
                MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

        // Add static of() method
        builderClassBuilder.addMethod(MethodSpec.methodBuilder("of")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(builderClassName)
                .addStatement("return new $T()", builderClassName)
                .build());

        // Add static from() method
        builderClassBuilder.addMethod(generateFromMethod(recordClassName, builderClassName, components));

        // Add setter methods, add adder/putter methods for Collection/Map fields
        for (int i = 0; i < components.size(); i++) {
            RecordComponentElement component = components.get(i);
            if (isCollection(component)) {
                builderClassBuilder.addMethod(generateAddMethod(builderClassName, component, i, components.size()));
                builderClassBuilder.addMethod(generateAddAllMethod(builderClassName, component, i, components.size()));
            } else if (isMap(component)) {
                builderClassBuilder.addMethod(generatePutMethod(builderClassName, component, i, components.size()));
                builderClassBuilder.addMethod(generatePutAllMethod(builderClassName, component, i, components.size()));
            } else {
                builderClassBuilder.addMethod(generateSetterMethod(builderClassName, component, i, components.size()));
            }
        }

        // Add hasXxx() methods for all fields
        for (int i = 0; i < components.size(); i++) {
            RecordComponentElement component = components.get(i);
            builderClassBuilder.addMethod(generateHasMethod(component, i, components.size()));
        }

        // Add getter methods for all fields
        for (RecordComponentElement component : components) {
            builderClassBuilder.addMethod(generateGetterMethod(component));
        }

        // Add clear methods for all fields
        for (int i = 0; i < components.size(); i++) {
            RecordComponentElement component = components.get(i);
            builderClassBuilder.addMethod(generateClearMethod(builderClassName, component, i, components.size()));
        }

        // Add build() method
        builderClassBuilder.addMethod(generateBuildMethod(recordClassName, components));

        // Incremental compilation (Isolating) must have exactly one originating element.
        // See https://docs.gradle.org/current/userguide/java_plugin.html#isolating_annotation_processors
        builderClassBuilder.addOriginatingElement(recordElement);

        JavaFile javaFile =
                JavaFile.builder(packageName, builderClassBuilder.build()).build();
        javaFile.writeTo(filer);
    }

    private TypeName getBuilderFieldType(RecordComponentElement component) {
        TypeMirror type = component.asType();

        if (isCollection(component)) {
            TypeMirror elementType = getTypeArgument(type, 0);
            ClassName interfaceClass = getCollectionInterfaceClass(type);
            // Preserve @Nullable annotations on element type
            TypeName elementTypeName = getTypeNameWithAnnotations(elementType);
            return ParameterizedTypeName.get(interfaceClass, elementTypeName);
        } else if (isMap(component)) {
            TypeMirror keyType = getTypeArgument(type, 0);
            TypeMirror valueType = getTypeArgument(type, 1);
            // Preserve @Nullable annotations on key/value types
            TypeName keyTypeName = getTypeNameWithAnnotations(keyType);
            TypeName valueTypeName = getTypeNameWithAnnotations(valueType);
            return ParameterizedTypeName.get(ClassName.get(Map.class), keyTypeName, valueTypeName);
        }

        // For non-collection and non-map types, use getTypeNameWithAnnotations to preserve type annotations
        return getTypeNameWithAnnotations(type);
    }

    private MethodSpec generateFromMethod(
            ClassName recordClassName, ClassName builderClassName, List<? extends RecordComponentElement> components) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("from")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(builderClassName)
                .addParameter(recordClassName, "source")
                .addStatement("$T builder = new $T()", builderClassName, builderClassName);

        for (RecordComponentElement component : components) {
            String fieldName = component.getSimpleName().toString();
            String getterName = fieldName;

            if (isCollection(component)) {
                // For collections, use addAll method
                String addAllMethodName = "addAll" + capitalize(fieldName);
                methodBuilder
                        .beginControlFlow("if (source.$L() != null)", getterName)
                        .addStatement("builder.$L(source.$L())", addAllMethodName, getterName)
                        .endControlFlow();
            } else if (isMap(component)) {
                // For maps, use putAll method
                String putAllMethodName = "putAll" + capitalize(fieldName);
                methodBuilder
                        .beginControlFlow("if (source.$L() != null)", getterName)
                        .addStatement("builder.$L(source.$L())", putAllMethodName, getterName)
                        .endControlFlow();
            } else {
                // For regular fields, use setter method
                // If primitive, no null check needed
                String setterName = "set" + capitalize(fieldName);
                if (isPrimitive(component)) {
                    methodBuilder.addStatement("builder.$L(source.$L())", setterName, getterName);
                } else {
                    methodBuilder
                            .beginControlFlow("if (source.$L() != null)", getterName)
                            .addStatement("builder.$L(source.$L())", setterName, getterName)
                            .endControlFlow();
                }
            }
        }

        methodBuilder.addStatement("return builder");
        return methodBuilder.build();
    }

    private MethodSpec generateSetterMethod(
            ClassName builderClassName, RecordComponentElement component, int fieldIndex, int totalFields) {
        String fieldName = component.getSimpleName().toString();
        String methodName = "set" + capitalize(fieldName);
        // Use getTypeNameWithAnnotations to preserve type annotations
        TypeName fieldType = getTypeNameWithAnnotations(component.asType());

        ParameterSpec.Builder paramBuilder = ParameterSpec.builder(fieldType, fieldName);

        // Note: @Nullable annotation is already included in fieldType via getTypeNameWithAnnotations
        // Don't add it again to avoid duplication or incorrect placement for qualified types

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(builderClassName)
                .addParameter(paramBuilder.build());

        // Add null check for non-nullable, non-primitive fields
        boolean isFieldNullable = isNullable(component);
        if (!isFieldNullable && !isPrimitive(component)) {
            methodBuilder.addStatement(
                    "$T.requireNonNull($L, \"$L cannot be null\")", Objects.class, fieldName, fieldName);
        }

        methodBuilder.addStatement("this.$L = $L", fieldName, fieldName);

        // Mark field as set using bitmap (protobuf-style semantics: even null values mark the field as set)
        methodBuilder.addStatement(generateSetBitStatement(fieldIndex, totalFields));

        methodBuilder.addStatement("return this");

        return methodBuilder.build();
    }

    private MethodSpec generateAddMethod(
            ClassName builderClassName, RecordComponentElement component, int fieldIndex, int totalFields) {
        String fieldName = component.getSimpleName().toString();
        String methodName = "add" + capitalize(fieldName);
        TypeMirror elementType = getTypeArgument(component.asType(), 0);
        boolean isElementNullable = isTypeNullable(elementType);
        Class<?> implClass = getCollectionImplClass(component.asType());

        ParameterSpec.Builder paramBuilder = ParameterSpec.builder(TypeName.get(elementType), "item");
        if (isElementNullable) {
            paramBuilder.addAnnotation(getNullableAnnotationFromType(elementType));
        }

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(builderClassName)
                .addParameter(paramBuilder.build());
        if (!isElementNullable) {
            methodBuilder.addStatement("$T.requireNonNull(item, \"item cannot be null\")", Objects.class);
        }
        methodBuilder
                .beginControlFlow("if (this.$L == null)", fieldName)
                .addStatement("this.$L = new $T<>()", fieldName, implClass)
                .endControlFlow()
                .addStatement("this.$L.add(item)", fieldName);

        // Mark field as set using bitmap
        methodBuilder.addStatement(generateSetBitStatement(fieldIndex, totalFields));

        methodBuilder.addStatement("return this");

        return methodBuilder.build();
    }

    private MethodSpec generateAddAllMethod(
            ClassName builderClassName, RecordComponentElement component, int fieldIndex, int totalFields) {
        String fieldName = component.getSimpleName().toString();
        String methodName = "addAll" + capitalize(fieldName);
        TypeMirror elementType = getTypeArgument(component.asType(), 0);
        Class<?> implClass = getCollectionImplClass(component.asType());

        // Get TypeName with annotations preserved
        TypeName elementTypeName = getTypeNameWithAnnotations(elementType);
        ParameterizedTypeName collectionType =
                ParameterizedTypeName.get(ClassName.get(Iterable.class), elementTypeName);

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(builderClassName)
                .addParameter(collectionType, "items")
                .addStatement("$T.requireNonNull(items, \"items cannot be null\")", Objects.class)
                .beginControlFlow("if (this.$L == null)", fieldName)
                .addStatement("this.$L = new $T<>()", fieldName, implClass)
                .endControlFlow()
                .beginControlFlow("for ($T item : items)", elementTypeName)
                .addStatement("this.$L.add(item)", fieldName)
                .endControlFlow();

        // Mark field as set using bitmap
        methodBuilder.addStatement(generateSetBitStatement(fieldIndex, totalFields));

        methodBuilder.addStatement("return this");

        return methodBuilder.build();
    }

    private MethodSpec generatePutMethod(
            ClassName builderClassName, RecordComponentElement component, int fieldIndex, int totalFields) {
        String fieldName = component.getSimpleName().toString();
        String methodName = "put" + capitalize(fieldName);
        TypeMirror keyType = getTypeArgument(component.asType(), 0);
        TypeMirror valueType = getTypeArgument(component.asType(), 1);
        boolean isKeyTypeNullable = isTypeNullable(keyType);
        boolean isValueTypeNullable = isTypeNullable(valueType);

        // Get TypeName with annotations preserved
        TypeName keyTypeName = getTypeNameWithAnnotations(keyType);
        TypeName valueTypeName = getTypeNameWithAnnotations(valueType);

        ParameterSpec.Builder keyParamBuilder = ParameterSpec.builder(keyTypeName, "key");
        ParameterSpec.Builder valueParamBuilder = ParameterSpec.builder(valueTypeName, "value");

        // Note: @Nullable annotation is already included in keyTypeName/valueTypeName via getTypeNameWithAnnotations
        // Don't add it again to avoid duplication or incorrect placement for qualified types

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(builderClassName)
                .addParameter(keyParamBuilder.build())
                .addParameter(valueParamBuilder.build());
        if (!isKeyTypeNullable) {
            methodBuilder.addStatement("$T.requireNonNull(key, \"key cannot be null\")", Objects.class);
        }
        if (!isValueTypeNullable) {
            methodBuilder.addStatement("$T.requireNonNull(value, \"value cannot be null\")", Objects.class);
        }
        methodBuilder
                .beginControlFlow("if (this.$L == null)", fieldName)
                .addStatement("this.$L = new $T<>()", fieldName, HashMap.class)
                .endControlFlow()
                .addStatement("this.$L.put(key, value)", fieldName);

        // Mark field as set using bitmap
        methodBuilder.addStatement(generateSetBitStatement(fieldIndex, totalFields));

        methodBuilder.addStatement("return this");

        return methodBuilder.build();
    }

    private MethodSpec generatePutAllMethod(
            ClassName builderClassName, RecordComponentElement component, int fieldIndex, int totalFields) {
        String fieldName = component.getSimpleName().toString();
        String methodName = "putAll" + capitalize(fieldName);
        TypeMirror keyType = getTypeArgument(component.asType(), 0);
        TypeMirror valueType = getTypeArgument(component.asType(), 1);

        // Get TypeName with annotations preserved
        TypeName keyTypeName = getTypeNameWithAnnotations(keyType);
        TypeName valueTypeName = getTypeNameWithAnnotations(valueType);
        TypeName mapType = ParameterizedTypeName.get(ClassName.get(Map.class), keyTypeName, valueTypeName);

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(builderClassName)
                .addParameter(mapType, "map")
                .addStatement("$T.requireNonNull(map, \"map cannot be null\")", Objects.class)
                .beginControlFlow("if (this.$L == null)", fieldName)
                .addStatement("this.$L = new $T<>()", fieldName, HashMap.class)
                .endControlFlow()
                .addStatement("this.$L.putAll(map)", fieldName);

        // Mark field as set using bitmap
        methodBuilder.addStatement(generateSetBitStatement(fieldIndex, totalFields));

        methodBuilder.addStatement("return this");

        return methodBuilder.build();
    }

    private MethodSpec generateClearMethod(
            ClassName builderClassName, RecordComponentElement component, int fieldIndex, int totalFields) {
        String fieldName = component.getSimpleName().toString();
        String methodName = "clear" + capitalize(fieldName);

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(builderClassName);

        // For primitive types, set to zero value; for reference types, set to null
        if (isPrimitive(component)) {
            String zeroValue = getPrimitiveZeroValue(component);
            methodBuilder.addStatement("this.$L = $L", fieldName, zeroValue);
        } else {
            methodBuilder.addStatement("this.$L = null", fieldName);
        }

        // Clear the bit in bitmap
        methodBuilder.addStatement(generateClearBitStatement(fieldIndex, totalFields));

        methodBuilder.addStatement("return this");
        return methodBuilder.build();
    }

    private String getPrimitiveZeroValue(RecordComponentElement component) {
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

    private MethodSpec generateGetterMethod(RecordComponentElement component) {
        String fieldName = component.getSimpleName().toString();
        String methodName = "get" + capitalize(fieldName);
        // Use getBuilderFieldType to preserve type parameter annotations
        TypeName fieldType = getBuilderFieldType(component);

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(fieldType);

        if (isCollection(component)) {
            if (isNullable(component)) {
                ClassName interfaceClass = getCollectionInterfaceClass(component.asType());
                String unmodifiableMethod =
                        interfaceClass.equals(ClassName.get(Set.class)) ? "unmodifiableSet" : "unmodifiableList";
                methodBuilder.addStatement(
                        "return this.$L != null ? $T.$L(this.$L) : null",
                        fieldName,
                        Collections.class,
                        unmodifiableMethod,
                        fieldName);
            } else {
                methodBuilder.addStatement(
                        "$T.requireNonNull(this.$L, \"$L has not been set a value yet\")",
                        Objects.class,
                        fieldName,
                        fieldName);
                ClassName interfaceClass = getCollectionInterfaceClass(component.asType());
                String unmodifiableMethod =
                        interfaceClass.equals(ClassName.get(Set.class)) ? "unmodifiableSet" : "unmodifiableList";
                methodBuilder.addStatement("return $T.$L(this.$L)", Collections.class, unmodifiableMethod, fieldName);
            }
        } else if (isMap(component)) {
            if (isNullable(component)) {
                methodBuilder.addStatement(
                        "return this.$L != null ? $T.unmodifiableMap(this.$L) : null",
                        fieldName,
                        Collections.class,
                        fieldName);
            } else {
                methodBuilder.addStatement(
                        "$T.requireNonNull(this.$L, \"$L has not been set a value yet\")",
                        Objects.class,
                        fieldName,
                        fieldName);
                methodBuilder.addStatement("return $T.unmodifiableMap(this.$L)", Collections.class, fieldName);
            }
        } else if (isPrimitive(component) || isNullable(component)) {
            methodBuilder.addStatement("return this.$L", fieldName);
        } else {
            methodBuilder.addStatement(
                    "return $T.requireNonNull(this.$L, \"$L has not been set a value yet\")",
                    Objects.class,
                    fieldName,
                    fieldName);
        }

        // Note: @Nullable annotation is already included in fieldType via getBuilderFieldType
        // Don't add it again to avoid duplication

        return methodBuilder.build();
    }

    private MethodSpec generateHasMethod(RecordComponentElement component, int fieldIndex, int totalFields) {
        String fieldName = component.getSimpleName().toString();
        String methodName = "has" + capitalize(fieldName);

        CodeBlock checkBitExpression = generateCheckBitExpression(fieldIndex, totalFields);

        return MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addStatement("return $L", checkBitExpression)
                .build();
    }

    private MethodSpec generateBuildMethod(
            ClassName recordClassName, List<? extends RecordComponentElement> components) {
        MethodSpec.Builder methodBuilder =
                MethodSpec.methodBuilder("build").addModifiers(Modifier.PUBLIC).returns(recordClassName);

        // Build the return statement
        CodeBlock.Builder returnStatement = CodeBlock.builder().add("return new $T(", recordClassName);

        for (int i = 0; i < components.size(); i++) {
            RecordComponentElement component = components.get(i);
            String fieldName = component.getSimpleName().toString();

            if (i > 0) {
                returnStatement.add(", ");
            }

            returnStatement.add("\n");

            // For collections, create defensive copies to prevent modifications
            // Use new collection + unmodifiable wrapper instead of copyOf() to support null elements
            if (isCollection(component)) {
                ClassName interfaceClass = getCollectionInterfaceClass(component.asType());
                String unmodifiableMethod =
                        interfaceClass.equals(ClassName.get(Set.class)) ? "unmodifiableSet" : "unmodifiableList";

                // Return unmodifiable collection if not null, otherwise pass null to record constructor
                returnStatement.add(
                        "this.$L != null ? $T.$L(this.$L) : null",
                        fieldName,
                        Collections.class,
                        unmodifiableMethod,
                        fieldName);
            } else if (isMap(component)) {
                // Return unmodifiable map if not null, otherwise pass null to record constructor
                returnStatement.add(
                        "this.$L != null ? $T.unmodifiableMap(this.$L) : null",
                        fieldName,
                        Collections.class,
                        fieldName);
            } else {
                returnStatement.add("this.$L", fieldName);
            }
        }

        returnStatement.add(")");
        methodBuilder.addStatement(returnStatement.build());

        return methodBuilder.build();
    }

    private boolean isCollection(RecordComponentElement component) {
        TypeMirror type = component.asType();
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }

        TypeElement collectionType = elementUtils.getTypeElement("java.util.Collection");
        if (collectionType == null) {
            return false;
        }

        // Check if the type is assignable to Collection
        TypeMirror collectionTypeMirror = typeUtils.erasure(collectionType.asType());
        TypeMirror erasedType = typeUtils.erasure(type);
        return typeUtils.isAssignable(erasedType, collectionTypeMirror);
    }

    private Class<?> getCollectionImplClass(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return ArrayList.class;
        }

        TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
        String qualifiedName = typeElement.getQualifiedName().toString();

        // Determine the appropriate implementation class based on the interface
        if (qualifiedName.equals("java.util.Set") || qualifiedName.equals("java.util.HashSet")) {
            return HashSet.class;
        } else if (qualifiedName.equals("java.util.List") || qualifiedName.equals("java.util.ArrayList")) {
            return ArrayList.class;
        } else {
            // Default to ArrayList for other Collection types
            return ArrayList.class;
        }
    }

    private ClassName getCollectionInterfaceClass(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return ClassName.get(List.class);
        }

        TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
        String qualifiedName = typeElement.getQualifiedName().toString();

        // Return the appropriate interface class
        if (qualifiedName.equals("java.util.Set") || qualifiedName.equals("java.util.HashSet")) {
            return ClassName.get(Set.class);
        } else if (qualifiedName.equals("java.util.List") || qualifiedName.equals("java.util.ArrayList")) {
            return ClassName.get(List.class);
        } else {
            // Default to List for other Collection types
            return ClassName.get(List.class);
        }
    }

    private boolean isMap(RecordComponentElement component) {
        TypeMirror type = component.asType();
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }

        TypeElement mapType = elementUtils.getTypeElement("java.util.Map");
        if (mapType == null) {
            return false;
        }

        // Check if the type is assignable to Map
        TypeMirror mapTypeMirror = typeUtils.erasure(mapType.asType());
        TypeMirror erasedType = typeUtils.erasure(type);
        return typeUtils.isAssignable(erasedType, mapTypeMirror);
    }

    private boolean isNullable(RecordComponentElement component) {
        // Check element annotations (for @Nullable on the parameter itself)
        boolean hasElementAnnotation =
                component.getAnnotationMirrors().stream().anyMatch(annotation -> isNullableAnnotation(annotation));

        // Check type annotations (for @Nullable on the type, like jspecify)
        boolean hasTypeAnnotation = component.asType().getAnnotationMirrors().stream()
                .anyMatch(annotation -> isNullableAnnotation(annotation));

        return hasElementAnnotation || hasTypeAnnotation;
    }

    private boolean isNullableAnnotation(AnnotationMirror annotation) {
        String qualifiedName = ((TypeElement) annotation.getAnnotationType().asElement())
                .getQualifiedName()
                .toString();
        return qualifiedName.equals("org.jspecify.annotations.Nullable")
                || qualifiedName.equals("javax.annotation.Nullable")
                || qualifiedName.equals("jakarta.annotation.Nullable")
                || qualifiedName.equals("org.jetbrains.annotations.Nullable")
                || qualifiedName.equals("androidx.annotation.Nullable")
                || qualifiedName.equals("org.checkerframework.checker.nullness.qual.Nullable")
                || qualifiedName.equals("edu.umd.cs.findbugs.annotations.Nullable");
    }

    private boolean isPrimitive(RecordComponentElement component) {
        return component.asType().getKind().isPrimitive();
    }

    private boolean isTypeNullable(TypeMirror type) {
        return type.getAnnotationMirrors().stream().anyMatch(annotation -> isNullableAnnotation(annotation));
    }

    private TypeName getTypeNameWithAnnotations(TypeMirror type) {
        TypeName typeName = TypeName.get(type);

        // Recursively handle parameterized types to preserve nested annotations
        // For example: Map<String, List<@Nullable String>>
        if (type.getKind() == TypeKind.DECLARED) {
            DeclaredType declaredType = (DeclaredType) type;
            List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();

            if (!typeArguments.isEmpty()) {
                // Recursively process each type argument to preserve nested annotations
                TypeName[] typeArgumentNames = new TypeName[typeArguments.size()];
                for (int i = 0; i < typeArguments.size(); i++) {
                    typeArgumentNames[i] = getTypeNameWithAnnotations(typeArguments.get(i));
                }

                // Get the raw type
                TypeElement typeElement = (TypeElement) declaredType.asElement();
                ClassName rawType = ClassName.get(typeElement);

                // Reconstruct the parameterized type with all nested annotations preserved
                typeName = ParameterizedTypeName.get(rawType, typeArgumentNames);
            }
        }

        // Add @Nullable annotation to the top-level type if present
        if (isTypeNullable(type)) {
            ClassName nullableAnnotation = getNullableAnnotationFromType(type);
            typeName = typeName.annotated(
                    AnnotationSpec.builder(nullableAnnotation).build());
        }

        return typeName;
    }

    private ClassName getNullableAnnotation(RecordComponentElement component) {
        // Check element annotations
        for (AnnotationMirror annotation : component.getAnnotationMirrors()) {
            if (isNullableAnnotation(annotation)) {
                TypeElement annotationType =
                        (TypeElement) annotation.getAnnotationType().asElement();
                String packageName = elementUtils
                        .getPackageOf(annotationType)
                        .getQualifiedName()
                        .toString();
                String simpleName = annotationType.getSimpleName().toString();
                return ClassName.get(packageName, simpleName);
            }
        }

        // Check type annotations
        for (AnnotationMirror annotation : component.asType().getAnnotationMirrors()) {
            if (isNullableAnnotation(annotation)) {
                TypeElement annotationType =
                        (TypeElement) annotation.getAnnotationType().asElement();
                String packageName = elementUtils
                        .getPackageOf(annotationType)
                        .getQualifiedName()
                        .toString();
                String simpleName = annotationType.getSimpleName().toString();
                return ClassName.get(packageName, simpleName);
            }
        }

        // Default fallback (shouldn't reach here if isNullable() returned true)
        return ClassName.get("org.jspecify.annotations", "Nullable");
    }

    private ClassName getNullableAnnotationFromType(TypeMirror type) {
        for (AnnotationMirror annotation : type.getAnnotationMirrors()) {
            if (isNullableAnnotation(annotation)) {
                TypeElement annotationType =
                        (TypeElement) annotation.getAnnotationType().asElement();
                String packageName = elementUtils
                        .getPackageOf(annotationType)
                        .getQualifiedName()
                        .toString();
                String simpleName = annotationType.getSimpleName().toString();
                return ClassName.get(packageName, simpleName);
            }
        }

        // Default fallback
        return ClassName.get("org.jspecify.annotations", "Nullable");
    }

    private TypeMirror getTypeArgument(TypeMirror type, int index) {
        if (type.getKind() == TypeKind.DECLARED) {
            DeclaredType declaredType = (DeclaredType) type;
            List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
            if (typeArguments.size() > index) {
                return typeArguments.get(index);
            }
        }
        return elementUtils.getTypeElement("java.lang.Object").asType();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private CodeBlock generateSetBitStatement(int bitIndex, int totalFields) {
        if (totalFields <= 32) {
            // Use int bitmap
            return CodeBlock.of("this._presenceMask0_ |= $L", "(1 << " + bitIndex + ")");
        } else if (totalFields <= 64) {
            // Use long bitmap
            return CodeBlock.of("this._presenceMask0_ |= $L", "(1L << " + bitIndex + ")");
        } else {
            // Use long array bitmap
            int arrayIndex = bitIndex / 64;
            int bitOffset = bitIndex % 64;
            return CodeBlock.of("this._presenceMask0_[$L] |= $L", arrayIndex, "(1L << " + bitOffset + ")");
        }
    }

    private CodeBlock generateCheckBitExpression(int bitIndex, int totalFields) {
        if (totalFields <= 32) {
            // Use int bitmap
            return CodeBlock.of("(this._presenceMask0_ & $L) != 0", "(1 << " + bitIndex + ")");
        } else if (totalFields <= 64) {
            // Use long bitmap
            return CodeBlock.of("(this._presenceMask0_ & $L) != 0", "(1L << " + bitIndex + ")");
        } else {
            // Use long array bitmap
            int arrayIndex = bitIndex / 64;
            int bitOffset = bitIndex % 64;
            return CodeBlock.of("(this._presenceMask0_[$L] & $L) != 0", arrayIndex, "(1L << " + bitOffset + ")");
        }
    }

    private CodeBlock generateClearBitStatement(int bitIndex, int totalFields) {
        if (totalFields <= 32) {
            // Use int bitmap
            return CodeBlock.of("this._presenceMask0_ &= ~$L", "(1 << " + bitIndex + ")");
        } else if (totalFields <= 64) {
            // Use long bitmap
            return CodeBlock.of("this._presenceMask0_ &= ~$L", "(1L << " + bitIndex + ")");
        } else {
            // Use long array bitmap
            int arrayIndex = bitIndex / 64;
            int bitOffset = bitIndex % 64;
            return CodeBlock.of("this._presenceMask0_[$L] &= ~$L", arrayIndex, "(1L << " + bitOffset + ")");
        }
    }
}
