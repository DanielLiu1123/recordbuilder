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
import java.util.List;
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
import javax.tools.Diagnostic;

/**
 * Annotation processor for generating builder classes for records annotated with {@link RecordBuilder}.
 *
 * @author Freeman
 */
public final class RecordBuilderProcessor extends AbstractProcessor {

    private static final String PRESENCE_MASK_FIELD = "_presenceMask0_";

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

        ClassName recordClassName = ClassName.get(packageName, recordName);
        ClassName builderClassName = ClassName.get(packageName, builderName);

        List<? extends RecordComponentElement> components = recordElement.getRecordComponents();

        TypeSpec.Builder builderClassBuilder =
                TypeSpec.classBuilder(builderName).addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        // Add fields
        for (RecordComponentElement component : components) {
            String fieldName = component.getSimpleName().toString();
            TypeName fieldType = getTypeNameWithAnnotations(component.asType());

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

        // Add setter methods for all fields
        for (int i = 0; i < components.size(); i++) {
            RecordComponentElement component = components.get(i);
            builderClassBuilder.addMethod(generateSetterMethod(builderClassName, component, i, components.size()));
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
            String setterName = "set" + capitalize(fieldName);

            // Use setter method for all fields
            if (isPrimitive(component)) {
                // If primitive, no null check needed
                methodBuilder.addStatement("builder.$L(source.$L())", setterName, getterName);
            } else {
                // For reference types, check null before setting
                methodBuilder
                        .beginControlFlow("if (source.$L() != null)", getterName)
                        .addStatement("builder.$L(source.$L())", setterName, getterName)
                        .endControlFlow();
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

    private MethodSpec generateGetterMethod(RecordComponentElement component) {
        String fieldName = component.getSimpleName().toString();
        String methodName = "get" + capitalize(fieldName);
        TypeName fieldType = getTypeNameWithAnnotations(component.asType());

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(fieldType);

        if (isPrimitive(component) || isNullable(component)) {
            methodBuilder.addStatement("return this.$L", fieldName);
        } else {
            methodBuilder.addStatement(
                    "return $T.requireNonNull(this.$L, \"$L has not been set a value yet\")",
                    Objects.class,
                    fieldName,
                    fieldName);
        }

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

            returnStatement.add("this.$L", fieldName);
        }

        returnStatement.add(")");
        methodBuilder.addStatement(returnStatement.build());

        return methodBuilder.build();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
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
}
