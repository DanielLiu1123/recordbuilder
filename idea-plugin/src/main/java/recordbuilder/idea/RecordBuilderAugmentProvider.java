package recordbuilder.idea;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiType;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.LightMethodBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

public class RecordBuilderAugmentProvider extends PsiAugmentProvider {

    @SuppressWarnings("unchecked")
    @NotNull
    @Override
    public <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element, @NotNull Class<Psi> type) {
        if (type != PsiMethod.class || !(element instanceof PsiClass builderClass)) {
            return List.of();
        }

        String builderFQN = builderClass.getQualifiedName();
        if (builderFQN == null || !builderFQN.endsWith("Builder")) {
            return List.of();
        }

        // Try to find the record class
        String recordFQN = builderFQN.substring(0, builderFQN.length() - "Builder".length());
        PsiClass recordClass =
                JavaPsiFacade.getInstance(element.getProject()).findClass(recordFQN, element.getResolveScope());

        if (!RecordBuilderUtils.hasRecordBuilderAnnotation(recordClass)) {
            return List.of();
        }

        // If it's our own light class, it already has all methods
        if (builderClass instanceof RecordBuilderElementFinder.RecordBuilderLightClass) {
            return List.of();
        }

        // For real classes, we add missing methods
        List<PsiMethod> augments = new ArrayList<>();
        Set<String> existingMethods = new HashSet<>();
        for (PsiMethod method : builderClass.getMethods()) {
            existingMethods.add(method.getName());
        }

        // Add missing setters/getters etc.
        PsiType builderType = com.intellij.psi.JavaPsiFacade.getElementFactory(builderClass.getProject())
                .createType(builderClass);
        PsiType recordType = com.intellij.psi.JavaPsiFacade.getElementFactory(builderClass.getProject())
                .createType(recordClass);

        for (PsiRecordComponent component : recordClass.getRecordComponents()) {
            String setterName = "set" + RecordBuilderUtils.capitalize(component.getName());
            if (!existingMethods.contains(setterName)) {
                augments.add(createSetter(builderClass, component, builderType));
            }

            String getterName = "get" + RecordBuilderUtils.capitalize(component.getName());
            if (!existingMethods.contains(getterName)) {
                augments.add(createGetter(builderClass, component));
            }

            String hasName = "has" + RecordBuilderUtils.capitalize(component.getName());
            if (!existingMethods.contains(hasName)) {
                augments.add(createHas(builderClass, component));
            }

            String clearName = "clear" + RecordBuilderUtils.capitalize(component.getName());
            if (!existingMethods.contains(clearName)) {
                augments.add(createClear(builderClass, component, builderType));
            }
        }

        // Add static builder methods
        if (!existingMethods.contains("builder")) {
            augments.add(createStaticBuilder(builderClass, builderType));
            augments.add(createStaticBuilderWithSource(builderClass, builderType, recordType));
        }

        // Add build method
        if (!existingMethods.contains("build")) {
            augments.add(createBuild(builderClass, recordType));
        }

        // Add merge method
        if (!existingMethods.contains("merge")) {
            augments.add(createMerge(builderClass, builderType, recordType));
        }

        // Add clear (all) method
        if (!existingMethods.contains("clear")) {
            augments.add(createClearAll(builderClass, builderType));
        }

        return (List<Psi>) augments;
    }

    private PsiMethod createSetter(PsiClass builderClass, PsiRecordComponent component, PsiType builderType) {
        String methodName = "set" + RecordBuilderUtils.capitalize(component.getName());
        LightMethodBuilder method = new LightMethodBuilder(builderClass.getManager(), methodName);
        method.setModifiers(com.intellij.psi.PsiModifier.PUBLIC);
        method.setContainingClass(builderClass);
        method.addParameter(component.getName(), component.getType());
        method.setMethodReturnType(builderType);
        method.setNavigationElement(component);
        return method;
    }

    private PsiMethod createGetter(PsiClass builderClass, PsiRecordComponent component) {
        String methodName = "get" + RecordBuilderUtils.capitalize(component.getName());
        LightMethodBuilder method = new LightMethodBuilder(builderClass.getManager(), methodName);
        method.setModifiers(com.intellij.psi.PsiModifier.PUBLIC);
        method.setContainingClass(builderClass);
        method.setMethodReturnType(component.getType());
        method.setNavigationElement(component);
        return method;
    }

    private PsiMethod createHas(PsiClass builderClass, PsiRecordComponent component) {
        String methodName = "has" + RecordBuilderUtils.capitalize(component.getName());
        LightMethodBuilder method = new LightMethodBuilder(builderClass.getManager(), methodName);
        method.setModifiers(com.intellij.psi.PsiModifier.PUBLIC);
        method.setContainingClass(builderClass);
        method.setMethodReturnType(com.intellij.psi.PsiTypes.booleanType());
        method.setNavigationElement(component);
        return method;
    }

    private PsiMethod createClear(PsiClass builderClass, PsiRecordComponent component, PsiType builderType) {
        String methodName = "clear" + RecordBuilderUtils.capitalize(component.getName());
        LightMethodBuilder method = new LightMethodBuilder(builderClass.getManager(), methodName);
        method.setModifiers(com.intellij.psi.PsiModifier.PUBLIC);
        method.setContainingClass(builderClass);
        method.setMethodReturnType(builderType);
        method.setNavigationElement(component);
        return method;
    }

    private PsiMethod createStaticBuilder(PsiClass builderClass, PsiType builderType) {
        LightMethodBuilder method = new LightMethodBuilder(builderClass.getManager(), "builder");
        method.setModifiers(com.intellij.psi.PsiModifier.PUBLIC, com.intellij.psi.PsiModifier.STATIC);
        method.setContainingClass(builderClass);
        method.setMethodReturnType(builderType);
        return method;
    }

    private PsiMethod createStaticBuilderWithSource(PsiClass builderClass, PsiType builderType, PsiType recordType) {
        LightMethodBuilder method = new LightMethodBuilder(builderClass.getManager(), "builder");
        method.setModifiers(com.intellij.psi.PsiModifier.PUBLIC, com.intellij.psi.PsiModifier.STATIC);
        method.setContainingClass(builderClass);
        method.addParameter("prototype", recordType);
        method.setMethodReturnType(builderType);
        return method;
    }

    private PsiMethod createBuild(PsiClass builderClass, PsiType recordType) {
        LightMethodBuilder method = new LightMethodBuilder(builderClass.getManager(), "build");
        method.setModifiers(com.intellij.psi.PsiModifier.PUBLIC);
        method.setContainingClass(builderClass);
        method.setMethodReturnType(recordType);
        return method;
    }

    private PsiMethod createMerge(PsiClass builderClass, PsiType builderType, PsiType recordType) {
        LightMethodBuilder method = new LightMethodBuilder(builderClass.getManager(), "merge");
        method.setModifiers(com.intellij.psi.PsiModifier.PUBLIC);
        method.setContainingClass(builderClass);
        method.addParameter("other", recordType);
        method.setMethodReturnType(builderType);
        return method;
    }

    private PsiMethod createClearAll(PsiClass builderClass, PsiType builderType) {
        LightMethodBuilder method = new LightMethodBuilder(builderClass.getManager(), "clear");
        method.setModifiers(com.intellij.psi.PsiModifier.PUBLIC);
        method.setContainingClass(builderClass);
        method.setMethodReturnType(builderType);
        return method;
    }
}
