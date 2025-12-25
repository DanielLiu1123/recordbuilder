package recordbuilder.idea;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightPsiClassBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RecordBuilderAugmentProvider extends PsiAugmentProvider {

    @Override
    @SuppressWarnings("unchecked")
    protected @NotNull <Psi extends PsiElement> List<Psi> getAugments(
            @NotNull PsiElement element, @NotNull Class<Psi> type, @Nullable String nameHint) {
        if (nameHint == null) {
            return super.getAugments(element, type, null);
        }
        if (type != PsiMethod.class && type != PsiClass.class) {
            return super.getAugments(element, type, nameHint);
        }

        // 为生成的 Builder 类添加方法和字段
        if (element instanceof LightPsiClassBuilder builderClass && type == PsiMethod.class) {
            PsiElement originRecord = builderClass.getNavigationElement();
            if (originRecord instanceof PsiClass recordClass
                    && recordClass.isRecord()
                    && RecordBuilderUtils.hasRecordBuilderAnnotation(recordClass)) {
                if (Objects.equals(builderClass.getName(), RecordBuilderUtils.getBuilderClassName(recordClass))) {
                    return (List<Psi>) createBuilderMethods(recordClass, builderClass);
                }
            }
        }

        return super.getAugments(element, type, nameHint);
    }

    private static List<PsiMethod> createBuilderMethods(PsiClass recordClass, PsiClass builderClass) {
        List<PsiMethod> methods = new ArrayList<>();
        PsiRecordComponent[] components = recordClass.getRecordComponents();
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(recordClass.getProject());
        PsiClassType builderType = factory.createType(builderClass);
        PsiClassType recordType = factory.createType(recordClass);

        // setter: setXxx methods
        for (PsiRecordComponent component : components) {
            String methodName = "set" + RecordBuilderUtils.capitalize(component.getName());
            LightMethodBuilder setter = new LightMethodBuilder(recordClass.getManager(), methodName);
            setter.setModifiers(PsiModifier.PUBLIC);
            setter.setContainingClass(builderClass);
            setter.addParameter(component.getName(), component.getType());
            setter.setMethodReturnType(builderType);
            methods.add(setter);
        }

        // hasXxx methods
        for (PsiRecordComponent component : components) {
            String methodName = "has" + RecordBuilderUtils.capitalize(component.getName());
            LightMethodBuilder hasMethod = new LightMethodBuilder(recordClass.getManager(), methodName);
            hasMethod.setModifiers(PsiModifier.PUBLIC);
            hasMethod.setContainingClass(builderClass);
            hasMethod.setMethodReturnType(PsiTypes.booleanType());
            methods.add(hasMethod);
        }

        // getXxx methods
        for (PsiRecordComponent component : components) {
            String methodName = "get" + RecordBuilderUtils.capitalize(component.getName());
            LightMethodBuilder getMethod = new LightMethodBuilder(recordClass.getManager(), methodName);
            getMethod.setModifiers(PsiModifier.PUBLIC);
            getMethod.setContainingClass(builderClass);
            getMethod.setMethodReturnType(component.getType());
            methods.add(getMethod);
        }

        // clearXxx methods
        for (PsiRecordComponent component : components) {
            String methodName = "clear" + RecordBuilderUtils.capitalize(component.getName());
            LightMethodBuilder clearMethod = new LightMethodBuilder(recordClass.getManager(), methodName);
            clearMethod.setModifiers(PsiModifier.PUBLIC);
            clearMethod.setContainingClass(builderClass);
            clearMethod.setMethodReturnType(builderType);
            methods.add(clearMethod);
        }
        // clear() method
        LightMethodBuilder clearMethod = new LightMethodBuilder(recordClass.getManager(), "clear");
        clearMethod.setModifiers(PsiModifier.PUBLIC);
        clearMethod.setContainingClass(builderClass);
        clearMethod.setMethodReturnType(builderType);
        methods.add(clearMethod);

        // merge(Record) method
        LightMethodBuilder mergeMethod = new LightMethodBuilder(recordClass.getManager(), "merge");
        mergeMethod.setModifiers(PsiModifier.PUBLIC);
        mergeMethod.setContainingClass(builderClass);
        mergeMethod.addParameter("other", recordType);
        mergeMethod.setMethodReturnType(builderType);
        methods.add(mergeMethod);

        // builder() static method (on Builder class)
        LightMethodBuilder builderMethod = new LightMethodBuilder(recordClass.getManager(), "builder");
        builderMethod.setModifiers(PsiModifier.PUBLIC, PsiModifier.STATIC);
        builderMethod.setContainingClass(builderClass);
        builderMethod.setMethodReturnType(builderType);
        methods.add(builderMethod);

        // builder(Record) static method (on Builder class)
        LightMethodBuilder builderFromSource = new LightMethodBuilder(recordClass.getManager(), "builder");
        builderFromSource.setModifiers(PsiModifier.PUBLIC, PsiModifier.STATIC);
        builderFromSource.setContainingClass(builderClass);
        builderFromSource.addParameter("prototype", recordType);
        builderFromSource.setMethodReturnType(builderType);
        methods.add(builderFromSource);

        // build() method
        LightMethodBuilder buildMethod = new LightMethodBuilder(recordClass.getManager(), "build");
        buildMethod.setModifiers(PsiModifier.PUBLIC);
        buildMethod.setContainingClass(builderClass);
        buildMethod.setMethodReturnType(recordType);
        methods.add(buildMethod);

        return methods;
    }
}
