package recordbuilder;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
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
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
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
    private TypeAnalyzer typeAnalyzer;
    private TypeNameBuilder typeNameBuilder;

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
        this.typeAnalyzer = new TypeAnalyzer(elementUtils, typeUtils);
        this.typeNameBuilder = new TypeNameBuilder(elementUtils, typeAnalyzer);
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
            TypeName fieldType = typeNameBuilder.getFieldType(component);

            // Add @Nullable annotation for all non-primitive fields in builder
            // (Builder fields can be null during construction, even if record field is non-null)
            if (!typeAnalyzer.isPrimitive(component) && !typeAnalyzer.isNullable(component)) {
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
            if (typeAnalyzer.isPrimitive(component)) {
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
        // Use typeNameBuilder to preserve type annotations
        TypeName fieldType = typeNameBuilder.getTypeNameWithAnnotations(component.asType());

        ParameterSpec.Builder paramBuilder = ParameterSpec.builder(fieldType, fieldName);

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(builderClassName)
                .addParameter(paramBuilder.build());

        // Add null check for non-nullable, non-primitive fields
        boolean isFieldNullable = typeAnalyzer.isNullable(component);
        if (!isFieldNullable && !typeAnalyzer.isPrimitive(component)) {
            methodBuilder.addStatement(
                    "$T.requireNonNull($L, \"$L cannot be null\")", Objects.class, fieldName, fieldName);
        }

        methodBuilder.addStatement("this.$L = $L", fieldName, fieldName);

        // Mark field as set using bitmap
        methodBuilder.addStatement(PresenceBitmapHelper.generateSetBitStatement(fieldIndex, totalFields));

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
        if (typeAnalyzer.isPrimitive(component)) {
            String zeroValue = typeAnalyzer.getPrimitiveZeroValue(component);
            methodBuilder.addStatement("this.$L = $L", fieldName, zeroValue);
        } else {
            methodBuilder.addStatement("this.$L = null", fieldName);
        }

        // Clear the bit in bitmap
        methodBuilder.addStatement(PresenceBitmapHelper.generateClearBitStatement(fieldIndex, totalFields));

        methodBuilder.addStatement("return this");
        return methodBuilder.build();
    }

    private MethodSpec generateGetterMethod(RecordComponentElement component) {
        String fieldName = component.getSimpleName().toString();
        String methodName = "get" + capitalize(fieldName);
        TypeName fieldType = typeNameBuilder.getFieldType(component);

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(fieldType);

        if (typeAnalyzer.isCollection(component)) {
            TypeMirror type = component.asType();
            boolean isConcrete = typeAnalyzer.isConcreteCollectionType(type);

            if (isConcrete) {
                // Concrete collection types: return directly without wrapping
                if (typeAnalyzer.isNullable(component)) {
                    methodBuilder.addStatement("return this.$L", fieldName);
                } else {
                    methodBuilder.addStatement(
                            "return $T.requireNonNull(this.$L, \"$L has not been set a value yet\")",
                            Objects.class,
                            fieldName,
                            fieldName);
                }
            } else {
                // Interface collection types: return directly
                if (typeAnalyzer.isNullable(component)) {
                    methodBuilder.addStatement("return this.$L", fieldName);
                } else {
                    methodBuilder.addStatement(
                            "return $T.requireNonNull(this.$L, \"$L has not been set a value yet\")",
                            Objects.class,
                            fieldName,
                            fieldName);
                }
            }
        } else if (typeAnalyzer.isMap(component)) {
            TypeMirror type = component.asType();
            boolean isConcrete = typeAnalyzer.isConcreteMapType(type);

            if (isConcrete) {
                // Concrete map types: return directly without wrapping
                if (typeAnalyzer.isNullable(component)) {
                    methodBuilder.addStatement("return this.$L", fieldName);
                } else {
                    methodBuilder.addStatement(
                            "return $T.requireNonNull(this.$L, \"$L has not been set a value yet\")",
                            Objects.class,
                            fieldName,
                            fieldName);
                }
            } else {
                // Interface map types: return directly
                if (typeAnalyzer.isNullable(component)) {
                    methodBuilder.addStatement("return this.$L", fieldName);
                } else {
                    methodBuilder.addStatement(
                            "return $T.requireNonNull(this.$L, \"$L has not been set a value yet\")",
                            Objects.class,
                            fieldName,
                            fieldName);
                }
            }
        } else if (typeAnalyzer.isPrimitive(component) || typeAnalyzer.isNullable(component)) {
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

        CodeBlock checkBitExpression = PresenceBitmapHelper.generateCheckBitExpression(fieldIndex, totalFields);

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
            if (typeAnalyzer.isCollection(component)) {
                TypeMirror type = component.asType();
                boolean isConcrete = typeAnalyzer.isConcreteCollectionType(type);

                if (isConcrete) {
                    // Concrete collection types: pass directly without wrapping
                    returnStatement.add("this.$L", fieldName);
                } else {
                    // Interface collection types: pass directly without wrapping
                    returnStatement.add("this.$L", fieldName);
                }
            } else if (typeAnalyzer.isMap(component)) {
                TypeMirror type = component.asType();
                boolean isConcrete = typeAnalyzer.isConcreteMapType(type);

                if (isConcrete) {
                    // Concrete map types: pass directly without wrapping
                    returnStatement.add("this.$L", fieldName);
                } else {
                    // Interface map types: pass directly without wrapping
                    returnStatement.add("this.$L", fieldName);
                }
            } else {
                returnStatement.add("this.$L", fieldName);
            }
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
}
