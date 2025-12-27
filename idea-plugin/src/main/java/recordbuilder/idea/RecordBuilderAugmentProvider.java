package recordbuilder.idea;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiType;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RecordBuilderAugmentProvider extends PsiAugmentProvider {

    @Override
    @SuppressWarnings("unchecked")
    protected @NotNull <Psi extends PsiElement> List<Psi> getAugments(
            @NotNull PsiElement element, @NotNull Class<Psi> type, @Nullable String nameHint) {
        if (type != PsiMethod.class || !(element instanceof PsiClass builderClass)) {
            return List.of();
        }

        // If it's our own light class, it already has all methods
        if (builderClass instanceof RecordBuilderElementFinder.RecordBuilderLightClass) {
            return List.of();
        }

        String builderFQN = builderClass.getQualifiedName();
        if (builderFQN == null || !builderFQN.endsWith("Builder")) {
            return List.of();
        }

        PsiClass recordClass = findRecordClass(builderClass);
        if (recordClass == null) {
            return List.of();
        }

        // For real classes, we add missing methods
        var addedProperties = getAddedProperties(builderClass, recordClass);
        if (addedProperties.isEmpty()) {
            return List.of();
        }

        var recordComponentMap = new HashMap<String, PsiRecordComponent>();
        for (var prop : recordClass.getRecordComponents()) {
            recordComponentMap.put(prop.getName(), prop);
        }

        var augments = new ArrayList<PsiMethod>();
        var builderType =
                JavaPsiFacade.getElementFactory(builderClass.getProject()).createType(builderClass);
        for (var newProperty : addedProperties) {
            var component = recordComponentMap.get(newProperty);
            if (component != null) {
                augments.add(createSetter(builderClass, component, builderType));
                augments.add(createGetter(builderClass, component));
                augments.add(createHas(builderClass, component));
                augments.add(createClear(builderClass, component, builderType));
            }
        }

        return (List<Psi>) augments;
    }

    private static @NotNull HashSet<String> getAddedProperties(PsiClass builderClass, PsiClass recordClass) {
        var existingProperties = new HashSet<String>();
        for (var field : builderClass.getAllFields()) {
            if (!field.getName().equals("_presenceMask0_")) {
                existingProperties.add(field.getName().substring(1)); // remove leading underscore
            }
        }

        var latestProperties = new HashSet<String>();
        for (var prop : recordClass.getRecordComponents()) {
            latestProperties.add(prop.getName());
        }

        var addedProperties = new HashSet<>(latestProperties);
        addedProperties.removeAll(existingProperties);
        return addedProperties;
    }

    @Nullable
    private PsiClass findRecordClass(PsiClass builderClass) {
        String builderFQN = builderClass.getQualifiedName();
        if (builderFQN == null || !builderFQN.endsWith("Builder")) {
            return null;
        }

        // For non-nested classes, try to find the record class
        String recordFQN = builderFQN.substring(0, builderFQN.length() - "Builder".length());
        var recordClass = findClass(builderClass.getResolveScope(), recordFQN);
        if (RecordBuilderUtils.hasRecordBuilderAnnotation(recordClass)) {
            return recordClass;
        }

        // For nested classes, try to find the record class in the package
        String packageName = StringUtil.getPackageName(builderFQN);
        PsiPackage psiPackage =
                JavaPsiFacade.getInstance(builderClass.getProject()).findPackage(packageName);
        if (psiPackage != null) {
            for (PsiClass target : psiPackage.getClasses(builderClass.getResolveScope())) {
                if (RecordBuilderUtils.hasRecordBuilderAnnotation(target)) {
                    String targetBuilderFQN = RecordBuilderUtils.getBuilderFQN(target);
                    if (builderFQN.equals(targetBuilderFQN)) {
                        return target;
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    private static PsiClass findClass(GlobalSearchScope scope, String fqn) {
        var project = scope.getProject();
        if (project == null) {
            return null;
        }
        return JavaPsiFacade.getInstance(project).findClass(fqn, scope);
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
}
