package recordbuilder;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.NameAllocator;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import com.palantir.javapoet.TypeVariableName;
import com.palantir.javapoet.WildcardTypeName;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
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
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

/**
 * Annotation processor for generating builder classes for records annotated with {@link RecordBuilder}.
 *
 * @author Freeman
 */
public final class RecordBuilderProcessor extends AbstractProcessor {

    private static final Map<String, String> collectionTypeMappings = getCollectionTypeMappings();
    private static final Map<String, String> mapTypeMappings = getMapTypeMappings();

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
            if (!isAccessibleFromPackage(recordElement)) {
                messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "@RecordBuilder requires a record and enclosing types accessible from its package",
                        recordElement);
                continue;
            }
            try {
                generateBuilder(recordElement);
            } catch (FilerException e) {
                messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "Cannot generate " + builderName(recordElement)
                                + ": the builder name conflicts with another type or annotated record",
                        recordElement);
            } catch (IOException e) {
                messager.printMessage(
                        Diagnostic.Kind.ERROR, "Failed to generate builder: " + e.getMessage(), recordElement);
            }
        }
        return true;
    }

    private static boolean isAccessibleFromPackage(TypeElement recordElement) {
        for (Element element = recordElement; element instanceof TypeElement; element = element.getEnclosingElement()) {
            if (element.getModifiers().contains(Modifier.PRIVATE)) {
                return false;
            }
        }
        return true;
    }

    private static String builderName(TypeElement recordElement) {
        return String.join("_", ClassName.get(recordElement).simpleNames()) + "Builder";
    }

    private void generateBuilder(TypeElement recordElement) throws IOException {
        String packageName =
                elementUtils.getPackageOf(recordElement).getQualifiedName().toString();
        String builderName = builderName(recordElement);

        TypeName recordTypeName = TypeName.get(recordElement.asType());
        TypeName builderTypeName;
        if (!recordElement.getTypeParameters().isEmpty()) {
            builderTypeName = ParameterizedTypeName.get(
                    ClassName.get(packageName, builderName),
                    recordElement.getTypeParameters().stream()
                            .map(TypeVariableName::get)
                            .toArray(TypeName[]::new));
        } else {
            builderTypeName = ClassName.get(packageName, builderName);
        }

        List<? extends RecordComponentElement> components = recordElement.getRecordComponents();

        TypeSpec.Builder builderClassBuilder = TypeSpec.classBuilder(builderName);

        // Add type parameters from record
        for (var typeParameter : recordElement.getTypeParameters()) {
            builderClassBuilder.addTypeVariable(TypeVariableName.get(typeParameter));
        }

        // Top-level builders can only be public or package-private.
        recordElement.getModifiers().stream()
                .filter(m -> m == Modifier.PUBLIC)
                .findFirst()
                .ifPresent(builderClassBuilder::addModifiers);

        builderClassBuilder
                .addModifiers(Modifier.FINAL)
                .addAnnotation(AnnotationSpec.builder(Generated.class)
                        .addMember("value", "$S", RecordBuilderProcessor.class.getCanonicalName())
                        .build());

        // Add fields
        for (RecordComponentElement component : components) {
            String fieldName = component.getSimpleName().toString();
            TypeName fieldType = getTypeNameWithAnnotations(component.asType());

            FieldSpec.Builder fieldBuilder = FieldSpec.builder(fieldType, "_" + fieldName, Modifier.PRIVATE);
            builderClassBuilder.addField(fieldBuilder.build());
        }

        var presenceMask = new PresenceMask(components.size());
        builderClassBuilder.addField(presenceMask.field());

        // Add private constructor
        builderClassBuilder.addMethod(
                MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

        // Add static builder() method
        builderClassBuilder.addMethod(generateBuilderMethod(recordElement, builderTypeName));

        // Add static builder(source) method
        builderClassBuilder.addMethod(generateBuilderFromSourceMethod(recordElement, builderTypeName));

        // Add merge() method
        builderClassBuilder.addMethod(generateMergeMethod(recordTypeName, builderTypeName, components, presenceMask));

        // Add setter methods for all fields
        // For Collection types, generate addXxx and addAllXxx methods
        // For Map types, generate putXxx and putAllXxx methods
        for (int i = 0; i < components.size(); i++) {
            RecordComponentElement component = components.get(i);
            if (isCollection(component)) {
                builderClassBuilder.addMethod(generateAddMethod(builderTypeName, component, i, presenceMask));
                builderClassBuilder.addMethod(generateAddAllMethod(builderTypeName, component, i, presenceMask));
            } else if (isMap(component)) {
                builderClassBuilder.addMethod(generatePutMethod(builderTypeName, component, i, presenceMask));
                builderClassBuilder.addMethod(generatePutAllMethod(builderTypeName, component, i, presenceMask));
            } else {
                builderClassBuilder.addMethod(generateSetterMethod(builderTypeName, component, i, presenceMask));
            }
        }

        // Add hasXxx() methods for all fields
        for (int i = 0; i < components.size(); i++) {
            RecordComponentElement component = components.get(i);
            builderClassBuilder.addMethod(generateHasMethod(component, i, presenceMask));
        }

        // Add getter methods for all fields
        for (RecordComponentElement component : components) {
            builderClassBuilder.addMethod(generateGetterMethod(component));
        }

        // Add clear methods for all fields
        for (int i = 0; i < components.size(); i++) {
            RecordComponentElement component = components.get(i);
            builderClassBuilder.addMethod(generateClearMethod(builderTypeName, component, i, presenceMask));
        }

        // Add clear() method to clear all fields
        builderClassBuilder.addMethod(generateClearAllMethod(builderTypeName, components, presenceMask));

        // Add build() method
        builderClassBuilder.addMethod(generateBuildMethod(recordTypeName, components));

        // Add toString() method
        builderClassBuilder.addMethod(generateToStringMethod(builderName, components));

        // Incremental compilation (Isolating) must have exactly one originating element.
        // See https://docs.gradle.org/current/userguide/java_plugin.html#isolating_annotation_processors
        builderClassBuilder.addOriginatingElement(recordElement);

        JavaFile javaFile =
                JavaFile.builder(packageName, builderClassBuilder.build()).build();
        javaFile.writeTo(filer);
    }

    private static MethodSpec generateBuilderMethod(TypeElement recordElement, TypeName builderTypeName) {
        MethodSpec.Builder builderMethodBuilder = MethodSpec.methodBuilder("builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(builderTypeName)
                .addStatement("return new $T()", builderTypeName);

        for (var typeParameter : recordElement.getTypeParameters()) {
            builderMethodBuilder.addTypeVariable(TypeVariableName.get(typeParameter));
        }

        return builderMethodBuilder.build();
    }

    private MethodSpec generateBuilderFromSourceMethod(TypeElement recordElement, TypeName builderTypeName) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(builderTypeName)
                .addParameter(TypeName.get(recordElement.asType()), "prototype");

        for (var typeParameter : recordElement.getTypeParameters()) {
            methodBuilder.addTypeVariable(TypeVariableName.get(typeParameter));
        }

        return methodBuilder
                .addStatement("return new $T().merge(prototype)", builderTypeName)
                .build();
    }

    private MethodSpec generateSetterMethod(
            TypeName builderTypeName, RecordComponentElement component, int fieldIndex, PresenceMask presenceMask) {
        String fieldName = component.getSimpleName().toString();
        String methodName = "set" + capitalize(fieldName);
        TypeName fieldType = getTypeNameWithAnnotations(component.asType());
        boolean checkNull = !isNullable(component) && !isPrimitive(component);
        String parameter = checkNull && (fieldName.equals("Objects") || fieldName.equals("java")) ? "value" : fieldName;

        MethodSpec.Builder builder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(builderTypeName)
                .addParameter(fieldType, parameter);

        if (checkNull) {
            addNullCheck(builder, CodeBlock.of("$L", parameter), fieldName);
        }

        builder.addStatement("this._$L = $L", fieldName, parameter);

        return builder.addStatement(presenceMask.setBit(fieldIndex))
                .addStatement("return this")
                .build();
    }

    private static boolean isWildcard(TypeName typeName) {
        return typeName instanceof WildcardTypeName;
    }

    private MethodSpec generateAddMethod(
            TypeName builderTypeName, RecordComponentElement component, int fieldIndex, PresenceMask presenceMask) {
        String fieldName = component.getSimpleName().toString();
        String methodName = "add" + capitalize(fieldName);
        DeclaredType declaredType = (DeclaredType) component.asType();
        TypeName elementType = getTypeArgument(declaredType, 0);

        var builder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(builderTypeName)
                .addParameter(eraseType(elementType), "value");

        if (!isNullable(elementType)) {
            builder.addStatement("$T.requireNonNull(value, $S)", Objects.class, "value cannot be null");
        }

        var implClass = getCollectionImplClass(component).orElseThrow();
        builder.addStatement("if (this._$L == null) this._$L = new $T<>()", fieldName, fieldName, implClass);

        if (isWildcard(elementType)) {
            builder.addStatement("(($T) this._$L).add(value)", implClass, fieldName);
        } else {
            builder.addStatement("this._$L.add(value)", fieldName);
        }

        return builder.addStatement(presenceMask.setBit(fieldIndex))
                .addStatement("return this")
                .build();
    }

    private MethodSpec generateAddAllMethod(
            TypeName builderTypeName, RecordComponentElement component, int fieldIndex, PresenceMask presenceMask) {
        String fieldName = component.getSimpleName().toString();
        String methodName = "addAll" + capitalize(fieldName);
        DeclaredType declaredType = (DeclaredType) component.asType();
        TypeName elementType = getTypeArgument(declaredType, 0);
        TypeName fieldType = getParameterTypeForCollection(declaredType);

        var builder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(builderTypeName)
                .addParameter(fieldType, "values");
        if (isNullable(fieldType)) {
            builder.beginControlFlow("if (values == null)")
                    .addStatement("this._$L = null", fieldName)
                    .addStatement(presenceMask.setBit(fieldIndex))
                    .addStatement("return this")
                    .endControlFlow();
        } else {
            builder.addStatement("$T.requireNonNull(values, $S)", Objects.class, "values cannot be null");
        }

        // Read and validate the entire input before changing the builder.
        TypeName bufferType = ParameterizedTypeName.get(
                ClassName.get(ArrayList.class), isWildcard(elementType) ? ClassName.get(Object.class) : elementType);
        builder.addStatement("var pending = new $T()", bufferType);
        builder.beginControlFlow("for (var value : values)");

        if (!isNullable(elementType)) {
            builder.addStatement("$T.requireNonNull(value, $S)", Objects.class, "value cannot be null");
        }

        builder.addStatement("pending.add(value)").endControlFlow();

        var implClass = getCollectionImplClass(component).orElseThrow();
        builder.addStatement("if (this._$L == null) this._$L = new $T<>()", fieldName, fieldName, implClass);
        builder.beginControlFlow("for (var value : pending)");
        if (isWildcard(elementType)) {
            builder.addStatement("(($T) this._$L).add(value)", implClass, fieldName);
        } else {
            builder.addStatement("this._$L.add(value)", fieldName);
        }
        builder.endControlFlow();

        return builder.addStatement(presenceMask.setBit(fieldIndex))
                .addStatement("return this")
                .build();
    }

    private MethodSpec generatePutMethod(
            TypeName builderTypeName, RecordComponentElement component, int fieldIndex, PresenceMask presenceMask) {
        String fieldName = component.getSimpleName().toString();
        String methodName = "put" + capitalize(fieldName);
        DeclaredType declaredType = (DeclaredType) component.asType();
        TypeName keyType = getTypeArgument(declaredType, 0);
        TypeName valueType = getTypeArgument(declaredType, 1);

        var builder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(builderTypeName)
                .addParameter(eraseType(keyType), "key")
                .addParameter(eraseType(valueType), "value");

        if (!isNullable(keyType)) {
            builder.addStatement("$T.requireNonNull(key, $S)", Objects.class, "key cannot be null");
        }
        if (!isNullable(valueType)) {
            builder.addStatement("$T.requireNonNull(value, $S)", Objects.class, "value cannot be null");
        }

        var implClass = getMapImplClass(component).orElseThrow();
        builder.addStatement("if (this._$L == null) this._$L = new $T<>()", fieldName, fieldName, implClass);

        if (isWildcard(keyType) || isWildcard(valueType)) {
            builder.addStatement("(($T) this._$L).put(key, value)", implClass, fieldName);
        } else {
            builder.addStatement("this._$L.put(key, value)", fieldName);
        }

        return builder.addStatement(presenceMask.setBit(fieldIndex))
                .addStatement("return this")
                .build();
    }

    private static TypeName eraseType(TypeName typeName) {
        if (typeName instanceof WildcardTypeName wildcardType) {
            var lowerBounds = wildcardType.lowerBounds();
            if (!lowerBounds.isEmpty()) {
                return lowerBounds.get(0);
            }
            var upperBounds = wildcardType.upperBounds();
            if (!upperBounds.isEmpty()) {
                return upperBounds.get(0);
            }
            return ClassName.get(Object.class);
        }
        return typeName;
    }

    private MethodSpec generatePutAllMethod(
            TypeName builderTypeName, RecordComponentElement component, int fieldIndex, PresenceMask presenceMask) {
        String fieldName = component.getSimpleName().toString();
        String methodName = "putAll" + capitalize(fieldName);
        DeclaredType declaredType = (DeclaredType) component.asType();
        TypeName keyType = getTypeArgument(declaredType, 0);
        TypeName valueType = getTypeArgument(declaredType, 1);
        TypeName fieldType = getParameterTypeForMap(declaredType);

        var builder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(builderTypeName)
                .addParameter(fieldType, "values");
        if (isNullable(fieldType)) {
            builder.beginControlFlow("if (values == null)")
                    .addStatement("this._$L = null", fieldName)
                    .addStatement(presenceMask.setBit(fieldIndex))
                    .addStatement("return this")
                    .endControlFlow();
        } else {
            builder.addStatement("$T.requireNonNull(values, $S)", Objects.class, "values cannot be null");
        }

        // Snapshot entries so validation failures cannot leave partially written fields.
        TypeName entryType = ParameterizedTypeName.get(
                ClassName.get(Map.Entry.class),
                isWildcard(keyType) ? ClassName.get(Object.class) : keyType,
                isWildcard(valueType) ? ClassName.get(Object.class) : valueType);
        TypeName bufferType = ParameterizedTypeName.get(ClassName.get(ArrayList.class), entryType);
        builder.addStatement("var pending = new $T()", bufferType);
        builder.beginControlFlow("for (var entry : values.entrySet())");
        builder.addStatement("var key = entry.getKey()");
        if (!isNullable(keyType)) {
            builder.addStatement("$T.requireNonNull(key, $S)", Objects.class, "key cannot be null");
        }
        builder.addStatement("var value = entry.getValue()");
        if (!isNullable(valueType)) {
            builder.addStatement("$T.requireNonNull(value, $S)", Objects.class, "value cannot be null");
        }

        builder.addStatement("pending.add(new $T<>(key, value))", AbstractMap.SimpleImmutableEntry.class)
                .endControlFlow();

        var implClass = getMapImplClass(component).orElseThrow();
        builder.addStatement("if (this._$L == null) this._$L = new $T<>()", fieldName, fieldName, implClass);
        builder.beginControlFlow("for (var entry : pending)");
        if (isWildcard(keyType) || isWildcard(valueType)) {
            builder.addStatement("(($T) this._$L).put(entry.getKey(), entry.getValue())", implClass, fieldName);
        } else {
            builder.addStatement("this._$L.put(entry.getKey(), entry.getValue())", fieldName);
        }
        builder.endControlFlow();

        return builder.addStatement(presenceMask.setBit(fieldIndex))
                .addStatement("return this")
                .build();
    }

    private TypeName getTypeArgument(DeclaredType declaredType, int index) {
        var typeArguments = declaredType.getTypeArguments();
        if (typeArguments.size() > index) {
            TypeMirror arg = typeArguments.get(index);
            if (arg.getKind() == TypeKind.WILDCARD) {
                return WildcardTypeName.get((WildcardType) arg);
            } else {
                return getTypeNameWithAnnotations(arg);
            }
        }
        return ClassName.get(Object.class);
    }

    private static String getTypeFQN(RecordComponentElement component) {
        return ((TypeElement) ((DeclaredType) component.asType()).asElement())
                .getQualifiedName()
                .toString();
    }

    private static Class<?> getClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Class not found: " + className, e);
        }
    }

    private TypeName getParameterTypeForMap(DeclaredType declaredType) {
        ClassName rawType = ClassName.get(Map.class);
        if (isTypeNullable(declaredType)) {
            rawType = rawType.annotated(AnnotationSpec.builder(getNullableAnnotationFromType(declaredType))
                    .build());
        }
        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
        if (typeArguments.isEmpty()) {
            // using raw Map,
            // putAll should accept any Map<?, ?>
            return ParameterizedTypeName.get(
                    rawType, WildcardTypeName.subtypeOf(Object.class), WildcardTypeName.subtypeOf(Object.class));
        }
        var keyType = getTypeArgument(declaredType, 0);
        var valueType = getTypeArgument(declaredType, 1);
        // There must be a reason why Protobuf doesn't use wildcards here like for Collection :)
        return ParameterizedTypeName.get(rawType, keyType, valueType);
    }

    private TypeName getParameterTypeForCollection(DeclaredType declaredType) {
        ClassName rawType = ClassName.get(Iterable.class);
        if (isTypeNullable(declaredType)) {
            rawType = rawType.annotated(AnnotationSpec.builder(getNullableAnnotationFromType(declaredType))
                    .build());
        }
        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
        if (typeArguments.isEmpty()) {
            // using raw Iterable,
            // addAll should accept any Iterable<?>
            return ParameterizedTypeName.get(rawType, WildcardTypeName.subtypeOf(Object.class));
        }
        TypeName elementType = getTypeArgument(declaredType, 0);
        if (isWildcard(elementType)) {
            return ParameterizedTypeName.get(rawType, elementType);
        } else {
            return ParameterizedTypeName.get(rawType, WildcardTypeName.subtypeOf(elementType));
        }
    }

    private MethodSpec generateClearMethod(
            TypeName builderTypeName, RecordComponentElement component, int fieldIndex, PresenceMask presenceMask) {
        String fieldName = component.getSimpleName().toString();
        String methodName = "clear" + capitalize(fieldName);

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(builderTypeName);

        // For primitive types, set to zero value; for reference types, set to null
        if (isPrimitive(component)) {
            String zeroValue = getPrimitiveZeroValue(component);
            methodBuilder.addStatement("this._$L = $L", fieldName, zeroValue);
        } else {
            methodBuilder.addStatement("this._$L = null", fieldName);
        }

        // Clear the bit in bitmap
        methodBuilder.addStatement(presenceMask.clearBit(fieldIndex));

        methodBuilder.addStatement("return this");
        return methodBuilder.build();
    }

    private MethodSpec generateClearAllMethod(
            TypeName builderTypeName, List<? extends RecordComponentElement> components, PresenceMask presenceMask) {
        MethodSpec.Builder methodBuilder =
                MethodSpec.methodBuilder("clear").addModifiers(Modifier.PUBLIC).returns(builderTypeName);

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

        presenceMask.addResetTo(methodBuilder);

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

    private MethodSpec generateHasMethod(RecordComponentElement component, int fieldIndex, PresenceMask presenceMask) {
        String fieldName = component.getSimpleName().toString();
        String methodName = "has" + capitalize(fieldName);

        CodeBlock checkBitExpression = presenceMask.isSet(fieldIndex);

        return MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addStatement("return $L", checkBitExpression)
                .build();
    }

    private MethodSpec generateBuildMethod(TypeName recordTypeName, List<? extends RecordComponentElement> components) {
        MethodSpec.Builder methodBuilder =
                MethodSpec.methodBuilder("build").addModifiers(Modifier.PUBLIC).returns(recordTypeName);

        // Build the return statement
        CodeBlock.Builder returnStatement = CodeBlock.builder().add("return new $T(", recordTypeName);

        for (int i = 0; i < components.size(); i++) {
            RecordComponentElement component = components.get(i);
            if (i > 0) {
                returnStatement.add(", ");
            }
            returnStatement.add("\n$L", prepareBuildArgument(methodBuilder, component));
        }

        returnStatement.add(")");
        methodBuilder.addStatement(returnStatement.build());

        return methodBuilder.build();
    }

    private CodeBlock prepareBuildArgument(MethodSpec.Builder method, RecordComponentElement component) {
        var name = component.getSimpleName();
        var field = CodeBlock.of("this._$L", name);
        if (component.asType().getKind() == TypeKind.ARRAY) {
            return CodeBlock.of("$L == null ? null : $L.clone()", field, field);
        }
        if (component.asType().getKind() != TypeKind.DECLARED) {
            return field;
        }
        var implementation = getCollectionImplClass(component).or(() -> getMapImplClass(component));
        if (implementation.isEmpty()) {
            return field;
        }
        // Keep the component's static type so the copy does not select a different record constructor.
        method.addStatement(
                "$T $L = $L == null ? null : new $T<>($L)",
                getTypeNameWithAnnotations(component.asType()),
                name,
                field,
                implementation.orElseThrow(),
                field);
        return CodeBlock.of("$L", name);
    }

    private MethodSpec generateMergeMethod(
            TypeName recordTypeName,
            TypeName builderTypeName,
            List<? extends RecordComponentElement> components,
            PresenceMask presenceMask) {
        var names = new NameAllocator();
        // Keep both imported and fully qualified references to java.util.Objects visible.
        names.newName("Objects");
        names.newName("java");
        for (RecordComponentElement component : components) {
            names.newName(component.getSimpleName().toString(), component);
        }
        String sourceName = names.newName("other");
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("merge")
                .addModifiers(Modifier.PUBLIC)
                .returns(builderTypeName)
                .addParameter(recordTypeName, sourceName)
                .addStatement("$T.requireNonNull($L, $S)", Objects.class, sourceName, "other cannot be null");

        for (int i = 0; i < components.size(); i++) {
            var component = components.get(i);
            String fieldName = component.getSimpleName().toString();
            String suffix = capitalize(fieldName);
            if (isPrimitive(component)) {
                methodBuilder.addStatement("this.set$L($L.$L())", suffix, sourceName, fieldName);
                continue;
            }

            String methodPrefix = "set";
            CodeBlock localType = CodeBlock.of("var");
            Class<?> implementation = null;
            if (isCollection(component)) {
                methodPrefix = "addAll";
                implementation = getCollectionImplClass(component).orElseThrow();
                // Match the bulk parameter type so a more specific single-element adder cannot win.
                localType = CodeBlock.of("$T", getParameterTypeForCollection((DeclaredType) component.asType()));
            } else if (isMap(component)) {
                methodPrefix = "putAll";
                implementation = getMapImplClass(component).orElseThrow();
            }
            boolean sorted = implementation == TreeSet.class || implementation == TreeMap.class;
            String localName = names.get(component);
            methodBuilder
                    .addStatement(
                            "$L $L = $L.$L()",
                            sorted ? CodeBlock.of("var") : localType,
                            localName,
                            sourceName,
                            fieldName)
                    .beginControlFlow("if ($L != null)", localName);
            // Keep the sorted source type for the copy and the bulk parameter type for overload resolution.
            CodeBlock argument = implementation == TreeSet.class
                    ? CodeBlock.of("($L) $L", localType, localName)
                    : CodeBlock.of("$L", localName);
            var mergeCall = CodeBlock.of("this.$L$L($L)", methodPrefix, suffix, argument);
            if (sorted) {
                methodBuilder.beginControlFlow("if (this._$L == null)", fieldName);
                addSortedCopy(methodBuilder, component, implementation, localName, names);
                methodBuilder
                        .addStatement(presenceMask.setBit(i))
                        .nextControlFlow("else")
                        .addStatement("$L", mergeCall)
                        .endControlFlow();
            } else {
                methodBuilder.addStatement("$L", mergeCall);
            }
            methodBuilder.endControlFlow();
        }

        methodBuilder.addStatement("return this");

        return methodBuilder.build();
    }

    private void addSortedCopy(
            MethodSpec.Builder method,
            RecordComponentElement component,
            Class<?> implementation,
            String sourceName,
            NameAllocator names) {
        var declaredType = (DeclaredType) component.asType();
        var fieldName = component.getSimpleName();
        String copyName = names.newName(fieldName + "Copy");
        var arguments = declaredType.getTypeArguments();
        var copyType = ClassName.get(implementation);
        // A raw temporary lets the copy accept elements captured by a wildcard source.
        TypeName constructorType = arguments.isEmpty()
                        || arguments.stream().anyMatch(type -> type.getKind() == TypeKind.WILDCARD)
                ? copyType
                : ParameterizedTypeName.get(
                        copyType,
                        arguments.stream().map(this::getTypeNameWithAnnotations).toArray(TypeName[]::new));
        // Copy by iteration so weakly consistent sources can change size during the copy.
        method.addStatement("var $L = new $T($L.comparator())", copyName, constructorType, sourceName);
        var keyOrElement = getTypeArgument(declaredType, 0);
        if (implementation == TreeSet.class) {
            String item = names.newName("item");
            method.beginControlFlow("for (var $L : $L)", item, sourceName);
            if (!isNullable(keyOrElement)) {
                addNullCheck(method, CodeBlock.of("$L", item), "value");
            }
            method.addStatement("$L.add($L)", copyName, item).endControlFlow();
        } else {
            addSortedMapEntries(method, declaredType, copyName, sourceName, names);
        }
        method.addStatement("this._$L = $L", fieldName, copyName);
    }

    private void addSortedMapEntries(
            MethodSpec.Builder method, DeclaredType type, String copyName, String sourceName, NameAllocator names) {
        var keyType = getTypeArgument(type, 0);
        var valueType = getTypeArgument(type, 1);
        // Give raw maps a wildcard view so entrySet() still yields Map.Entry.
        var source = type.getTypeArguments().isEmpty()
                ? CodeBlock.of("(($T<?, ?>) $L)", Map.class, sourceName)
                : CodeBlock.of("$L", sourceName);
        String entry = names.newName("entry");
        String key = names.newName("key");
        String value = names.newName("value");
        method.beginControlFlow("for (var $L : $L.entrySet())", entry, source)
                .addStatement("var $L = $L.getKey()", key, entry);
        if (!isNullable(keyType)) {
            addNullCheck(method, CodeBlock.of("$L", key), "key");
        }
        method.addStatement("var $L = $L.getValue()", value, entry);
        if (!isNullable(valueType)) {
            addNullCheck(method, CodeBlock.of("$L", value), "value");
        }
        method.addStatement("$L.put($L, $L)", copyName, key, value).endControlFlow();
    }

    private static void addNullCheck(MethodSpec.Builder method, CodeBlock value, String label) {
        method.addStatement("$T.requireNonNull($L, $S)", Objects.class, value, label + " cannot be null");
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase(Locale.ROOT) + str.substring(1);
    }

    private static Optional<Class<?>> getCollectionImplClass(RecordComponentElement component) {
        String fqn = getTypeFQN(component);
        String implName = collectionTypeMappings.get(fqn);
        return implName != null ? Optional.of(getClass(implName)) : Optional.empty();
    }

    private static Optional<Class<?>> getMapImplClass(RecordComponentElement component) {
        String fqn = getTypeFQN(component);
        String implName = mapTypeMappings.get(fqn);
        return implName != null ? Optional.of(getClass(implName)) : Optional.empty();
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

    private static boolean isNullable(TypeName typeName) {
        for (var annotation : typeName.annotations()) {
            if (isNullableFQN(annotation.type().toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean isNullableAnnotation(AnnotationMirror annotation) {
        String qualifiedName = ((TypeElement) annotation.getAnnotationType().asElement())
                .getQualifiedName()
                .toString();
        return isNullableFQN(qualifiedName);
    }

    private static boolean isNullableFQN(String qualifiedName) {
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
        TypeName typeName;

        // Recursively handle parameterized types to preserve nested annotations
        if (type.getKind() == TypeKind.DECLARED) {
            typeName = getDeclaredTypeName((DeclaredType) type);
        } else if (type.getKind() == TypeKind.ARRAY) {
            ArrayType arrayType = (ArrayType) type;
            TypeName componentTypeName = getTypeNameWithAnnotations(arrayType.getComponentType());
            typeName = ArrayTypeName.of(componentTypeName);
        } else if (type.getKind() == TypeKind.WILDCARD) {
            WildcardType wildcardType = (WildcardType) type;
            TypeName extendsBound = wildcardType.getExtendsBound() != null
                    ? getTypeNameWithAnnotations(wildcardType.getExtendsBound())
                    : null;
            TypeName superBound = wildcardType.getSuperBound() != null
                    ? getTypeNameWithAnnotations(wildcardType.getSuperBound())
                    : null;
            if (extendsBound != null) {
                typeName = WildcardTypeName.subtypeOf(extendsBound);
            } else if (superBound != null) {
                typeName = WildcardTypeName.supertypeOf(superBound);
            } else {
                typeName = WildcardTypeName.subtypeOf(Object.class);
            }
        } else {
            typeName = TypeName.get(type);
        }

        if (isTypeNullable(type)) {
            return typeName.annotated(
                    AnnotationSpec.builder(getNullableAnnotationFromType(type)).build());
        } else {
            return typeName;
        }
    }

    private TypeName getDeclaredTypeName(DeclaredType type) {
        if (type.getTypeArguments().isEmpty()) {
            return TypeName.get(type);
        }

        List<TypeName> arguments = type.getTypeArguments().stream()
                .map(this::getTypeNameWithAnnotations)
                .toList();
        ClassName rawType = ClassName.get((TypeElement) type.asElement());
        TypeMirror enclosingType = type.getEnclosingType();
        if (enclosingType.getKind() == TypeKind.DECLARED
                && getTypeNameWithAnnotations(enclosingType) instanceof ParameterizedTypeName owner) {
            return owner.nestedClass(rawType.simpleName(), arguments);
        }
        return ParameterizedTypeName.get(rawType, arguments.toArray(TypeName[]::new));
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

    private MethodSpec generateToStringMethod(String builderName, List<? extends RecordComponentElement> components) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("toString")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(String.class);

        // Use StringJoiner for cleaner code
        methodBuilder.addStatement("var joiner = new $T($S, $S, $S)", StringJoiner.class, ", ", builderName + "{", "}");

        // For each field, check if it's set before adding to toString
        for (RecordComponentElement component : components) {
            String fieldName = component.getSimpleName().toString();
            String internalFieldName = "_" + fieldName;
            String hasMethodName = "has" + capitalize(fieldName);

            methodBuilder.addStatement(
                    "if ($L()) joiner.add($S + $L)", hasMethodName, fieldName + "=", internalFieldName);
        }

        methodBuilder.addStatement("return joiner.toString()");

        return methodBuilder.build();
    }

    private static Map<String, String> getCollectionTypeMappings() {
        return Map.ofEntries(
                Map.entry("java.util.Collection", "java.util.ArrayList"),
                Map.entry("java.util.List", "java.util.ArrayList"),
                Map.entry("java.util.Set", "java.util.HashSet"),
                Map.entry("java.util.Queue", "java.util.LinkedList"),
                Map.entry("java.util.Deque", "java.util.LinkedList"),
                Map.entry("java.util.SequencedCollection", "java.util.ArrayList"),
                Map.entry("java.util.SequencedSet", "java.util.LinkedHashSet"),
                Map.entry("java.util.ArrayList", "java.util.ArrayList"),
                Map.entry("java.util.LinkedList", "java.util.LinkedList"),
                Map.entry("java.util.HashSet", "java.util.HashSet"),
                Map.entry("java.util.SortedSet", "java.util.TreeSet"),
                Map.entry("java.util.TreeSet", "java.util.TreeSet"),
                Map.entry("java.util.concurrent.CopyOnWriteArrayList", "java.util.concurrent.CopyOnWriteArrayList"));
    }

    private static Map<String, String> getMapTypeMappings() {
        return Map.ofEntries(
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
    }
}
