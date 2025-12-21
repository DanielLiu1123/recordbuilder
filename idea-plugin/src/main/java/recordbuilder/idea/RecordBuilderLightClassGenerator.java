package recordbuilder.idea;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiType;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightPsiClassBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Generates light Builder classes for records annotated with @RecordBuilder.
 *
 * <p>This allows the IDE to recognize generated Builder classes without compilation.
 *
 * @author Freeman
 */
public class RecordBuilderLightClassGenerator extends PsiAugmentProvider {

    @NotNull
    @Override
    protected <Psi extends PsiElement> List<Psi> getAugments(
            @NotNull PsiElement element, @NotNull Class<Psi> type) {
        if (type != PsiClass.class) {
            return Collections.emptyList();
        }

        // Handle both PsiFile and PsiClass as element
        List<Psi> result = new ArrayList<>();
        
        if (element instanceof PsiFile) {
            // When element is PsiFile, find all record classes with @RecordBuilder
            PsiFile psiFile = (PsiFile) element;
            PsiElement[] children = psiFile.getChildren();
            for (PsiElement child : children) {
                if (child instanceof PsiClass) {
                    PsiClass psiClass = (PsiClass) child;
                    if (RecordBuilderUtils.isRecordBuilder(psiClass)) {
                        PsiClass builderClass = generateBuilderClass(psiClass);
                        if (builderClass != null) {
                            result.add((Psi) builderClass);
                        }
                    }
                }
            }
        } else if (element instanceof PsiClass) {
            // When element is PsiClass, check if it needs a builder
            PsiClass psiClass = (PsiClass) element;
            if (RecordBuilderUtils.isRecordBuilder(psiClass)) {
                PsiClass builderClass = generateBuilderClass(psiClass);
                if (builderClass != null) {
                    result.add((Psi) builderClass);
                }
            }
        }

        return result;
    }

    /**
     * Generate a light Builder class for the given record.
     */
    private PsiClass generateBuilderClass(@NotNull PsiClass recordClass) {
        String builderName = RecordBuilderUtils.getBuilderClassName(recordClass);
        String packageName = RecordBuilderUtils.getPackageName(recordClass);

        LightPsiClassBuilder builder =
                new LightPsiClassBuilder(recordClass, builderName, packageName);

        // Set the visibility to match the record class
        if (recordClass.hasModifierProperty(PsiModifier.PUBLIC)) {
            builder.setModifiers(PsiModifier.PUBLIC, PsiModifier.FINAL);
        } else {
            builder.setModifiers(PsiModifier.FINAL);
        }

        // Get record components
        PsiRecordComponent[] components = recordClass.getRecordComponents();

        // Add fields
        for (PsiRecordComponent component : components) {
            PsiField field = createBuilderField(recordClass, component);
            builder.addField(field);
        }

        // Add presence mask field
        builder.addField(createPresenceMaskField(recordClass, components.length));

        // Add private constructor
        builder.addMethod(createPrivateConstructor(recordClass, builderName));

        // Add static builder() method
        builder.addMethod(createStaticBuilderMethod(recordClass, builderName));

        // Add static builder(prototype) method
        builder.addMethod(createStaticBuilderFromPrototypeMethod(recordClass, builderName));

        // Add merge() method
        builder.addMethod(createMergeMethod(recordClass, builderName));

        // Add setter methods
        for (PsiRecordComponent component : components) {
            builder.addMethod(createSetterMethod(recordClass, component, builderName));
        }

        // Add getter methods
        for (PsiRecordComponent component : components) {
            builder.addMethod(createGetterMethod(recordClass, component));
        }

        // Add has methods
        for (PsiRecordComponent component : components) {
            builder.addMethod(createHasMethod(recordClass, component));
        }

        // Add clear methods
        for (PsiRecordComponent component : components) {
            builder.addMethod(createClearMethod(recordClass, component, builderName));
        }

        // Add build() method
        builder.addMethod(createBuildMethod(recordClass));

        return builder;
    }

    private PsiField createBuilderField(@NotNull PsiClass recordClass, @NotNull PsiRecordComponent component) {
        String fieldName = "_" + component.getName();
        PsiType fieldType = component.getType();
        LightFieldBuilder field = new LightFieldBuilder(recordClass.getManager(), fieldName, fieldType);
        field.setModifiers(PsiModifier.PRIVATE);
        field.setContainingClass(recordClass);
        return field;
    }

    private PsiField createPresenceMaskField(@NotNull PsiClass recordClass, int componentCount) {
        PsiType type;
        if (componentCount <= 32) {
            type = PsiType.INT;
        } else if (componentCount <= 64) {
            type = PsiType.LONG;
        } else {
            type = PsiType.LONG.createArrayType();
        }
        LightFieldBuilder field = new LightFieldBuilder(recordClass.getManager(), "_presenceMask0_", type);
        field.setModifiers(PsiModifier.PRIVATE);
        field.setContainingClass(recordClass);
        return field;
    }

    private PsiMethod createPrivateConstructor(@NotNull PsiClass recordClass, @NotNull String builderName) {
        LightMethodBuilder method = new LightMethodBuilder(recordClass.getManager(), builderName);
        method.setConstructor(true);
        method.setModifiers(PsiModifier.PRIVATE);
        method.setContainingClass(recordClass);
        return method;
    }

    private PsiMethod createStaticBuilderMethod(@NotNull PsiClass recordClass, @NotNull String builderName) {
        LightMethodBuilder method = new LightMethodBuilder(recordClass.getManager(), "builder");
        method.setModifiers(PsiModifier.PUBLIC, PsiModifier.STATIC);
        method.setContainingClass(recordClass);

        PsiClassType builderType = createBuilderType(recordClass);
        method.setMethodReturnType(builderType);

        return method;
    }

    private PsiMethod createStaticBuilderFromPrototypeMethod(
            @NotNull PsiClass recordClass, @NotNull String builderName) {
        LightMethodBuilder method = new LightMethodBuilder(recordClass.getManager(), "builder");
        method.setModifiers(PsiModifier.PUBLIC, PsiModifier.STATIC);
        method.setContainingClass(recordClass);

        PsiClassType recordType = createRecordType(recordClass);
        method.addParameter("prototype", recordType);

        PsiClassType builderType = createBuilderType(recordClass);
        method.setMethodReturnType(builderType);

        return method;
    }

    private PsiMethod createMergeMethod(@NotNull PsiClass recordClass, @NotNull String builderName) {
        LightMethodBuilder method = new LightMethodBuilder(recordClass.getManager(), "merge");
        method.setModifiers(PsiModifier.PUBLIC);
        method.setContainingClass(recordClass);

        PsiClassType recordType = createRecordType(recordClass);
        method.addParameter("other", recordType);

        PsiClassType builderType = createBuilderType(recordClass);
        method.setMethodReturnType(builderType);

        return method;
    }

    private PsiMethod createSetterMethod(
            @NotNull PsiClass recordClass, @NotNull PsiRecordComponent component, @NotNull String builderName) {
        String methodName = "set" + RecordBuilderUtils.capitalize(component.getName());
        LightMethodBuilder method = new LightMethodBuilder(recordClass.getManager(), methodName);
        method.setModifiers(PsiModifier.PUBLIC);
        method.setContainingClass(recordClass);

        method.addParameter(component.getName(), component.getType());

        PsiClassType builderType = createBuilderType(recordClass);
        method.setMethodReturnType(builderType);

        return method;
    }

    private PsiMethod createGetterMethod(@NotNull PsiClass recordClass, @NotNull PsiRecordComponent component) {
        String methodName = "get" + RecordBuilderUtils.capitalize(component.getName());
        LightMethodBuilder method = new LightMethodBuilder(recordClass.getManager(), methodName);
        method.setModifiers(PsiModifier.PUBLIC);
        method.setContainingClass(recordClass);
        method.setMethodReturnType(component.getType());
        return method;
    }

    private PsiMethod createHasMethod(@NotNull PsiClass recordClass, @NotNull PsiRecordComponent component) {
        String methodName = "has" + RecordBuilderUtils.capitalize(component.getName());
        LightMethodBuilder method = new LightMethodBuilder(recordClass.getManager(), methodName);
        method.setModifiers(PsiModifier.PUBLIC);
        method.setContainingClass(recordClass);
        method.setMethodReturnType(PsiType.BOOLEAN);
        return method;
    }

    private PsiMethod createClearMethod(
            @NotNull PsiClass recordClass, @NotNull PsiRecordComponent component, @NotNull String builderName) {
        String methodName = "clear" + RecordBuilderUtils.capitalize(component.getName());
        LightMethodBuilder method = new LightMethodBuilder(recordClass.getManager(), methodName);
        method.setModifiers(PsiModifier.PUBLIC);
        method.setContainingClass(recordClass);

        PsiClassType builderType = createBuilderType(recordClass);
        method.setMethodReturnType(builderType);

        return method;
    }

    private PsiMethod createBuildMethod(@NotNull PsiClass recordClass) {
        LightMethodBuilder method = new LightMethodBuilder(recordClass.getManager(), "build");
        method.setModifiers(PsiModifier.PUBLIC);
        method.setContainingClass(recordClass);

        PsiClassType recordType = createRecordType(recordClass);
        method.setMethodReturnType(recordType);

        return method;
    }

    private PsiClassType createBuilderType(@NotNull PsiClass recordClass) {
        String qualifiedName = RecordBuilderUtils.getBuilderQualifiedName(recordClass);
        return PsiType.getTypeByName(qualifiedName, recordClass.getProject(), recordClass.getResolveScope());
    }

    private PsiClassType createRecordType(@NotNull PsiClass recordClass) {
        String qualifiedName = recordClass.getQualifiedName();
        if (qualifiedName == null) {
            qualifiedName = recordClass.getName();
        }
        return PsiType.getTypeByName(qualifiedName, recordClass.getProject(), recordClass.getResolveScope());
    }
}
