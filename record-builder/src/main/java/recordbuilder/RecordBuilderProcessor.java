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
import com.palantir.javapoet.WildcardTypeName;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Generated;
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
import javax.tools.Diagnostic;

/**
 * Annotation processor for generating builder classes for records annotated with {@link RecordBuilder}.
 *
 * @author Freeman
 */
public final class RecordBuilderProcessor extends AbstractProcessor {

    private static final String PRESENCE_MASK_FIELD = "_presenceMask0_";

    private static final Map<String, String> collectionTypeMappings = Map.ofEntries(
            Map.entry("java.util.Collection", "java.util.ArrayList"),
            Map.entry("java.util.List", "java.util.ArrayList"),
            Map.entry("java.util.Set", "java.util.HashSet"),
            Map.entry("java.util.Queue", "java.util.LinkedList"),
            Map.entry("java.util.Deque", "java.util.LinkedList"),
            Map.entry("java.util.SequencedCollection", "java.util.ArrayList"),
            Map.entry("java.util.SequencedSet", "java.util.HashSet"),
            Map.entry("java.util.ArrayList", "java.util.ArrayList"),
            Map.entry("java.util.LinkedList", "java.util.LinkedList"),
            Map.entry("java.util.HashSet", "java.util.HashSet"),
            Map.entry("java.util.SortedSet", "java.util.TreeSet"),
            Map.entry("java.util.TreeSet", "java.util.TreeSet"));

    private static final Map<String, String> mapTypeMappings = Map.ofEntries(
            Map.entry("java.util.Map", "java.util.HashMap"),
            Map.entry("java.util.HashMap", "java.util.HashMap"),
            Map.entry("java.util.LinkedHashMap", "java.util.LinkedHashMap"),
            Map.entry("java.util.SortedMap", "java.util.TreeMap"),
            Map.entry("java.util.SequencedMap", "java.util.LinkedHashMap"),
            Map.entry("java.util.NavigableMap", "java.util.TreeMap"),
            Map.entry("java.util.Hashtable", "java.util.Hashtable"),
            Map.entry("java.util.IdentityHashMap", "java.util.IdentityHashMap"),
            Map.entry("java.util.TreeMap", "java.util.TreeMap"),
            Map.entry("java.util.concurrent.ConcurrentMap", "java.util.concurrent.ConcurrentHashMap"),
            Map.entry("java.util.concurrent.ConcurrentHashMap", "java.util.concurrent.ConcurrentHashMap"));

    private Filer filer;
    private Messager messager;
    private Elements elementUtils;

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

        ClassName recordClassName = ClassName.get(recordElement);
        ClassName builderClassName = ClassName.get(packageName, builderName);

        List<? extends RecordComponentElement> components = recordElement.getRecordComponents();

        TypeSpec.Builder builderClassBuilder = TypeSpec.classBuilder(builderName);

        // Set the same visibility as the record
        recordElement.getModifiers().stream()
                .filter(m -> m == Modifier.PUBLIC || m == Modifier.PROTECTED || m == Modifier.PRIVATE)
                .findFirst()
                .ifPresent(builderClassBuilder::addModifiers);

        builderClassBuilder
                .addModifiers(Modifier.FINAL)
                .addAnnotation(AnnotationSpec.builder(Generated.class)
                        .addMember("value", "$S", RecordBuilderProcessor.class.getCanonicalName())
                        .addMember("date", "$S", OffsetDateTime.now().toString())
                        .build());

        // Add fields
        for (RecordComponentElement component : components) {
            String fieldName = component.getSimpleName().toString();
            TypeName fieldType = getTypeNameWithAnnotations(component.asType());

            FieldSpec.Builder fieldBuilder = FieldSpec.builder(fieldType, "_" + fieldName, Modifier.PRIVATE);
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

        // Add static builder() method
        builderClassBuilder.addMethod(MethodSpec.methodBuilder("builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(builderClassName)
                .addStatement("return new $T()", builderClassName)
                .build());

        // Add static builder(source) method
        builderClassBuilder.addMethod(generateBuilderFromSourceMethod(recordClassName, builderClassName));

        // Add merge() method
        builderClassBuilder.addMethod(generateMergeMethod(recordClassName, builderClassName, components));

        // Add setter methods for all fields
        // For Collection types, generate addXxx and addAllXxx methods
        // For Map types, generate putXxx and putAllXxx methods
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

        // Add clear() method to clear all fields
        builderClassBuilder.addMethod(generateClearAllMethod(builderClassName, components));

        // Add build() method
        builderClassBuilder.addMethod(generateBuildMethod(recordClassName, components));

        // Add toString() method
        builderClassBuilder.addMethod(generateToStringMethod(builderName, components));

        // Incremental compilation (Isolating) must have exactly one originating element.
        // See https://docs.gradle.org/current/userguide/java_plugin.html#isolating_annotation_processors
        builderClassBuilder.addOriginatingElement(recordElement);

        JavaFile javaFile =
                JavaFile.builder(packageName, builderClassBuilder.build()).build();
        javaFile.writeTo(filer);
    }

    private MethodSpec generateBuilderFromSourceMethod(ClassName recordClassName, ClassName builderClassName) {
        return MethodSpec.methodBuilder("builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(builderClassName)
                .addParameter(recordClassName, "prototype")
                .addStatement("return new $T().merge(prototype)", builderClassName)
                .build();
    }

    private MethodSpec generateSetterMethod(
            ClassName builderClassName, RecordComponentElement component, int fieldIndex, int totalFields) {
        String fieldName = component.getSimpleName().toString();
        String methodName = "set" + capitalize(fieldName);
        // Use getTypeNameWithAnnotations to preserve type annotations
        TypeName fieldType = getTypeNameWithAnnotations(component.asType());

        ParameterSpec.Builder paramBuilder = ParameterSpec.builder(fieldType, fieldName);

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

        methodBuilder.addStatement("this._$L = $L", fieldName, fieldName);

        // Mark field as set using bitmap
        methodBuilder.addStatement(generateSetBitStatement(fieldIndex, totalFields));

        methodBuilder.addStatement("return this");

        return methodBuilder.build();
    }

    private MethodSpec generateAddMethod(
            ClassName builderClassName, RecordComponentElement component, int fieldIndex, int totalFields) {
        String fieldName = component.getSimpleName().toString();
        String methodName = "add" + capitalize(fieldName);
        DeclaredType declaredType = (DeclaredType) component.asType();
        TypeName elementType =
                getTypeNameWithAnnotations(declaredType.getTypeArguments().get(0));
        String collectionType = collectionTypeMappings.get(getTypeFQN(component));

        var builder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(builderClassName)
                .addParameter(elementType, "value");
        if (!isTypeNullable(declaredType.getTypeArguments().get(0))) {
            builder.addStatement("$T.requireNonNull(value, \"value cannot be null\")", Objects.class);
        }
        builder.addStatement(
                        "if (this._$L == null) this._$L = new $T<>()", fieldName, fieldName, getClass(collectionType))
                .addStatement("this._$L.add(value)", fieldName)
                .addStatement(generateSetBitStatement(fieldIndex, totalFields))
                .addStatement("return this");
        return builder.build();
    }

    private static Class<?> getClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Class not found: " + className, e);
        }
    }

    private MethodSpec generateAddAllMethod(
            ClassName builderClassName, RecordComponentElement component, int fieldIndex, int totalFields) {
        String fieldName = component.getSimpleName().toString();
        String methodName = "addAll" + capitalize(fieldName);
        DeclaredType declaredType = (DeclaredType) component.asType();
        TypeName elementType =
                getTypeNameWithAnnotations(declaredType.getTypeArguments().get(0));
        TypeName fieldType = getParameterTypeForCollection(declaredType);
        String collectionType = collectionTypeMappings.get(getTypeFQN(component));

        var builder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(builderClassName)
                .addParameter(fieldType, "values");
        if (isTypeNullable(declaredType)) {
            builder.beginControlFlow("if (values == null)")
                    .addStatement("this._$L = null", fieldName)
                    .addStatement(generateSetBitStatement(fieldIndex, totalFields))
                    .addStatement("return this")
                    .endControlFlow();
        } else {
            builder.addStatement("$T.requireNonNull(values, \"values cannot be null\")", Objects.class);
        }
        builder.addStatement(
                        "if (this._$L == null) this._$L = new $T<>()", fieldName, fieldName, getClass(collectionType))
                .beginControlFlow("for ($T value : values)", elementType);
        if (!isTypeNullable(declaredType.getTypeArguments().get(0))) {
            builder.addStatement("$T.requireNonNull(value, \"value cannot be null\")", Objects.class);
        }
        builder.addStatement("this._$L.add(value)", fieldName)
                .endControlFlow()
                .addStatement(generateSetBitStatement(fieldIndex, totalFields))
                .addStatement("return this");
        return builder.build();
    }

    private MethodSpec generatePutMethod(
            ClassName builderClassName, RecordComponentElement component, int fieldIndex, int totalFields) {
        String fieldName = component.getSimpleName().toString();
        String methodName = "put" + capitalize(fieldName);
        DeclaredType declaredType = (DeclaredType) component.asType();
        TypeName keyType =
                getTypeNameWithAnnotations(declaredType.getTypeArguments().get(0));
        TypeName valueType =
                getTypeNameWithAnnotations(declaredType.getTypeArguments().get(1));
        String mapType = mapTypeMappings.get(getTypeFQN(component));

        var builder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(builderClassName)
                .addParameter(keyType, "key")
                .addParameter(valueType, "value");
        if (!isTypeNullable(declaredType.getTypeArguments().get(0))) {
            builder.addStatement("$T.requireNonNull(key, \"key cannot be null\")", Objects.class);
        }
        if (!isTypeNullable(declaredType.getTypeArguments().get(1))) {
            builder.addStatement("$T.requireNonNull(value, \"value cannot be null\")", Objects.class);
        }
        builder.addStatement("if (this._$L == null) this._$L = new $T<>()", fieldName, fieldName, getClass(mapType))
                .addStatement("this._$L.put(key, value)", fieldName)
                .addStatement(generateSetBitStatement(fieldIndex, totalFields))
                .addStatement("return this");
        return builder.build();
    }

    private static String getTypeFQN(RecordComponentElement component) {
        return ((TypeElement) ((DeclaredType) component.asType()).asElement())
                .getQualifiedName()
                .toString();
    }

    private MethodSpec generatePutAllMethod(
            ClassName builderClassName, RecordComponentElement component, int fieldIndex, int totalFields) {
        String fieldName = component.getSimpleName().toString();
        String methodName = "putAll" + capitalize(fieldName);
        DeclaredType declaredType = (DeclaredType) component.asType();
        TypeName keyType =
                getTypeNameWithAnnotations(declaredType.getTypeArguments().get(0));
        TypeName valueType =
                getTypeNameWithAnnotations(declaredType.getTypeArguments().get(1));
        TypeName fieldType = getParameterTypeForMap(declaredType);
        String mapType = mapTypeMappings.get(getTypeFQN(component));

        var builder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(builderClassName)
                .addParameter(fieldType, "values");
        if (isTypeNullable(declaredType)) {
            builder.beginControlFlow("if (values == null)")
                    .addStatement("this._$L = null", fieldName)
                    .addStatement(generateSetBitStatement(fieldIndex, totalFields))
                    .addStatement("return this")
                    .endControlFlow();
        } else {
            builder.addStatement("$T.requireNonNull(values, \"values cannot be null\")", Objects.class);
        }
        builder.addStatement("if (this._$L == null) this._$L = new $T<>()", fieldName, fieldName, getClass(mapType))
                .beginControlFlow("for ($T.Entry<$T, $T> entry : values.entrySet())", Map.class, keyType, valueType);
        if (!isTypeNullable(declaredType.getTypeArguments().get(0))) {
            builder.addStatement("$T.requireNonNull(entry.getKey(), \"key cannot be null\")", Objects.class);
        }
        if (!isTypeNullable(declaredType.getTypeArguments().get(1))) {
            builder.addStatement("$T.requireNonNull(entry.getValue(), \"value cannot be null\")", Objects.class);
        }
        builder.addStatement("this._$L.put(entry.getKey(), entry.getValue())", fieldName)
                .endControlFlow()
                .addStatement(generateSetBitStatement(fieldIndex, totalFields))
                .addStatement("return this");
        return builder.build();
    }

    private TypeName getParameterTypeForMap(DeclaredType declaredType) {
        ClassName rawType = ClassName.get(Map.class);
        if (isTypeNullable(declaredType)) {
            rawType = rawType.annotated(AnnotationSpec.builder(getNullableAnnotationFromType(declaredType))
                    .build());
        }
        TypeName keyType =
                getTypeNameWithAnnotations(declaredType.getTypeArguments().get(0));
        TypeName valueType =
                getTypeNameWithAnnotations(declaredType.getTypeArguments().get(1));
        return ParameterizedTypeName.get(rawType, keyType, valueType);
    }

    private TypeName getParameterTypeForCollection(DeclaredType declaredType) {
        ClassName rawType = ClassName.get(Iterable.class);
        if (isTypeNullable(declaredType)) {
            rawType = rawType.annotated(AnnotationSpec.builder(getNullableAnnotationFromType(declaredType))
                    .build());
        }
        TypeName elementType =
                getTypeNameWithAnnotations(declaredType.getTypeArguments().get(0));
        return ParameterizedTypeName.get(rawType, WildcardTypeName.subtypeOf(elementType));
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
            methodBuilder.addStatement("this._$L = $L", fieldName, zeroValue);
        } else {
            methodBuilder.addStatement("this._$L = null", fieldName);
        }

        // Clear the bit in bitmap
        methodBuilder.addStatement(generateClearBitStatement(fieldIndex, totalFields));

        methodBuilder.addStatement("return this");
        return methodBuilder.build();
    }

    private MethodSpec generateClearAllMethod(
            ClassName builderClassName, List<? extends RecordComponentElement> components) {
        MethodSpec.Builder methodBuilder =
                MethodSpec.methodBuilder("clear").addModifiers(Modifier.PUBLIC).returns(builderClassName);

        // Reset all fields to their default values
        for (RecordComponentElement component : components) {
            String fieldName = component.getSimpleName().toString();
            if (isPrimitive(component)) {
                String zeroValue = getPrimitiveZeroValue(component);
                methodBuilder.addStatement("this._$L = $L", fieldName, zeroValue);
            } else {
                methodBuilder.addStatement("this._$L = null", fieldName);
            }
        }

        // Reset the presence mask
        int fieldCount = components.size();
        if (fieldCount > 0) {
            if (fieldCount <= 32) {
                methodBuilder.addStatement("this." + PRESENCE_MASK_FIELD + " = 0");
            } else if (fieldCount <= 64) {
                methodBuilder.addStatement("this." + PRESENCE_MASK_FIELD + " = 0L");
            } else {
                // Requires import of java.util.Arrays, which JavaPoet will handle
                methodBuilder.addStatement("$T.fill(this.$L, 0L)", Arrays.class, PRESENCE_MASK_FIELD);
            }
        }

        methodBuilder.addStatement("return this");

        return methodBuilder.build();
    }

    private MethodSpec generateGetterMethod(RecordComponentElement component) {
        String fieldName = component.getSimpleName().toString();
        String methodName = "get" + capitalize(fieldName);
        TypeName fieldType = getTypeNameWithAnnotations(component.asType());

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(fieldType);

        methodBuilder.addStatement("return this._$L", fieldName);

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

            returnStatement.add("this._$L", fieldName);
        }

        returnStatement.add(")");
        methodBuilder.addStatement(returnStatement.build());

        return methodBuilder.build();
    }

    private MethodSpec generateMergeMethod(
            ClassName recordClassName, ClassName builderClassName, List<? extends RecordComponentElement> components) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("merge")
                .addModifiers(Modifier.PUBLIC)
                .returns(builderClassName)
                .addParameter(recordClassName, "other")
                .addStatement("$T.requireNonNull(other, \"other cannot be null\")", Objects.class);

        for (RecordComponentElement component : components) {
            String fieldName = component.getSimpleName().toString();
            String setterName = "set" + capitalize(fieldName);
            String allAllName = "addAll" + capitalize(fieldName);
            String putAllName = "putAll" + capitalize(fieldName);

            // Use setter method for all fields
            if (isPrimitive(component)) {
                // If primitive, no null check needed
                methodBuilder.addStatement("this.$L(other.$L())", setterName, fieldName);
            } else {
                // For reference types, check null before setting
                if (isCollection(component)) {
                    methodBuilder
                            .beginControlFlow("if (other.$L() != null)", fieldName)
                            .addStatement("this.$L(other.$L())", allAllName, fieldName)
                            .endControlFlow();
                } else if (isMap(component)) {
                    methodBuilder
                            .beginControlFlow("if (other.$L() != null)", fieldName)
                            .addStatement("this.$L(other.$L())", putAllName, fieldName)
                            .endControlFlow();
                } else {
                    methodBuilder
                            .beginControlFlow("if (other.$L() != null)", fieldName)
                            .addStatement("this.$L(other.$L())", setterName, fieldName)
                            .endControlFlow();
                }
            }
        }

        methodBuilder.addStatement("return this");

        return methodBuilder.build();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private static boolean isCollection(RecordComponentElement component) {
        TypeMirror type = component.asType();
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        return collectionTypeMappings.containsKey(getTypeFQN(component));
    }

    private static boolean isMap(RecordComponentElement component) {
        TypeMirror type = component.asType();
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        return mapTypeMappings.containsKey(getTypeFQN(component));
    }

    private boolean isNullable(RecordComponentElement component) {
        // Check element annotations (for @Nullable on the parameter itself)
        boolean hasElementAnnotation = component.getAnnotationMirrors().stream().anyMatch(this::isNullableAnnotation);

        // Check type annotations (for @Nullable on the type, like jspecify)
        boolean hasTypeAnnotation =
                component.asType().getAnnotationMirrors().stream().anyMatch(this::isNullableAnnotation);

        return hasElementAnnotation || hasTypeAnnotation;
    }

    private boolean isTypeNullable(TypeMirror type) {
        return type.getAnnotationMirrors().stream().anyMatch(this::isNullableAnnotation);
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

    private TypeName getTypeNameWithAnnotations(TypeMirror type) {
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
        if (isTypeNullable(type)) {
            ClassName nullableAnnotation = getNullableAnnotationFromType(type);
            typeName = typeName.annotated(
                    AnnotationSpec.builder(nullableAnnotation).build());
        }

        return typeName;
    }

    /**
     * Gets the nullable annotation from a type.
     */
    private ClassName getNullableAnnotationFromType(TypeMirror type) {
        for (AnnotationMirror annotation : type.getAnnotationMirrors()) {
            if (isNullableAnnotation(annotation)) {
                return createClassName(annotation);
            }
        }

        // Default fallback
        return ClassName.get("org.jspecify.annotations", "Nullable");
    }

    private ClassName createClassName(AnnotationMirror annotation) {
        TypeElement annotationType =
                (TypeElement) annotation.getAnnotationType().asElement();
        String packageName =
                elementUtils.getPackageOf(annotationType).getQualifiedName().toString();
        String simpleName = annotationType.getSimpleName().toString();
        return ClassName.get(packageName, simpleName);
    }

    private static CodeBlock generateSetBitStatement(int bitIndex, int totalFields) {
        if (totalFields <= 32) {
            // Use int bitmap
            return CodeBlock.of("this." + PRESENCE_MASK_FIELD + " |= $L", "(1 << " + bitIndex + ")");
        } else if (totalFields <= 64) {
            // Use long bitmap
            return CodeBlock.of("this." + PRESENCE_MASK_FIELD + " |= $L", "(1L << " + bitIndex + ")");
        } else {
            // Use long array bitmap
            int arrayIndex = bitIndex / 64;
            int bitOffset = bitIndex % 64;
            return CodeBlock.of("this." + PRESENCE_MASK_FIELD + "[$L] |= $L", arrayIndex, "(1L << " + bitOffset + ")");
        }
    }

    private static CodeBlock generateCheckBitExpression(int bitIndex, int totalFields) {
        if (totalFields <= 32) {
            // Use int bitmap
            return CodeBlock.of("(this." + PRESENCE_MASK_FIELD + " & $L) != 0", "(1 << " + bitIndex + ")");
        } else if (totalFields <= 64) {
            // Use long bitmap
            return CodeBlock.of("(this." + PRESENCE_MASK_FIELD + " & $L) != 0", "(1L << " + bitIndex + ")");
        } else {
            // Use long array bitmap
            int arrayIndex = bitIndex / 64;
            int bitOffset = bitIndex % 64;
            return CodeBlock.of(
                    "(this." + PRESENCE_MASK_FIELD + "[$L] & $L) != 0", arrayIndex, "(1L << " + bitOffset + ")");
        }
    }

    private static CodeBlock generateClearBitStatement(int bitIndex, int totalFields) {
        if (totalFields <= 32) {
            // Use int bitmap
            return CodeBlock.of("this." + PRESENCE_MASK_FIELD + " &= ~$L", "(1 << " + bitIndex + ")");
        } else if (totalFields <= 64) {
            // Use long bitmap
            return CodeBlock.of("this." + PRESENCE_MASK_FIELD + " &= ~$L", "(1L << " + bitIndex + ")");
        } else {
            // Use long array bitmap
            int arrayIndex = bitIndex / 64;
            int bitOffset = bitIndex % 64;
            return CodeBlock.of("this." + PRESENCE_MASK_FIELD + "[$L] &= ~$L", arrayIndex, "(1L << " + bitOffset + ")");
        }
    }

    private MethodSpec generateToStringMethod(String builderName, List<? extends RecordComponentElement> components) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("toString")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(String.class);

        // Use StringJoiner for cleaner code
        methodBuilder.addStatement(
                "$T joiner = new $T($S, $S, $S)", StringJoiner.class, StringJoiner.class, ", ", builderName + "{", "}");

        // For each field, check if it's set before adding to toString
        for (RecordComponentElement component : components) {
            String fieldName = component.getSimpleName().toString();
            String internalFieldName = "_" + fieldName;
            String hasMethodName = "has" + capitalize(fieldName);

            methodBuilder.addStatement(
                    "if ($L()) joiner.add($S + $L)", hasMethodName, internalFieldName + "=", internalFieldName);
        }

        methodBuilder.addStatement("return joiner.toString()");

        return methodBuilder.build();
    }
}
