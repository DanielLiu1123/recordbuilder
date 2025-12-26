package recordbuilder.idea;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightPsiClassBuilder;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RecordBuilderElementFinder extends PsiElementFinder {

    @Nullable
    @Override
    public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
        if (!qualifiedName.endsWith("Builder")) {
            return null;
        }

        Project project = scope.getProject();
        if (project == null) {
            return null;
        }

        var facade = JavaPsiFacade.getInstance(project);
        var recordQualifiedName = qualifiedName.substring(0, qualifiedName.length() - "Builder".length());

        PsiClass recordClass = facade.findClass(recordQualifiedName, scope);
        if (recordClass != null
                && recordClass.isRecord()
                && RecordBuilderUtils.hasRecordBuilderAnnotation(recordClass)) {
            return createLightBuilderClass(recordClass, qualifiedName);
        }

        return null;
    }

    @Override
    public PsiClass @NotNull [] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
        PsiClass psiClass = findClass(qualifiedName, scope);
        return psiClass != null ? new PsiClass[] {psiClass} : PsiClass.EMPTY_ARRAY;
    }

    @Override
    public PsiClass @NotNull [] getClasses(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
        // 关键：不要调用 psiPackage.getClasses()，否则会触发所有 ElementFinder 的递归调用。
        // 我们通过遍历包下的文件（PsiFile）来获取类。
        var result = new ArrayList<PsiClass>();
        for (PsiFile file : psiPackage.getFiles(scope)) {
            if (file instanceof PsiJavaFile javaFile) {
                for (PsiClass psiClass : javaFile.getClasses()) {
                    collectBuilderClasses(psiClass, result);
                }
            }
        }
        return result.toArray(PsiClass.EMPTY_ARRAY);
    }

    @NotNull
    @Override
    public Set<String> getClassNames(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
        var result = new HashSet<String>();
        for (PsiFile file : psiPackage.getFiles(scope)) {
            if (file instanceof PsiJavaFile javaFile) {
                for (PsiClass psiClass : javaFile.getClasses()) {
                    collectBuilderNames(psiClass, result);
                }
            }
        }
        return result;
    }

    private static void collectBuilderNames(PsiClass psiClass, Set<String> result) {
        if (psiClass.isRecord() && RecordBuilderUtils.hasRecordBuilderAnnotation(psiClass)) {
            result.add(RecordBuilderUtils.getBuilderClassName(psiClass));
        }
        for (PsiClass innerClass : psiClass.getInnerClasses()) {
            collectBuilderNames(innerClass, result);
        }
    }

    private static void collectBuilderClasses(PsiClass psiClass, ArrayList<PsiClass> result) {
        if (psiClass.isRecord() && RecordBuilderUtils.hasRecordBuilderAnnotation(psiClass)) {
            String builderQName = RecordBuilderUtils.getBuilderQualifiedName(psiClass);
            result.add(createLightBuilderClass(psiClass, builderQName));
        }
        for (PsiClass innerClass : psiClass.getInnerClasses()) {
            collectBuilderClasses(innerClass, result);
        }
    }

    static PsiClass createLightBuilderClass(PsiClass recordClass, String qualifiedName) {
        String builderClassName = RecordBuilderUtils.getBuilderClassName(recordClass);
        var builder = new RecordBuilderLightClass(recordClass, builderClassName, qualifiedName);
        builder.getModifierList().addModifier(PsiModifier.PUBLIC);
        builder.getModifierList().addModifier(PsiModifier.FINAL);
        var methods = createBuilderMethods(recordClass, builder);
        for (var method : methods) {
            builder.addMethod(method);
        }
        return builder;
    }

    private static List<PsiMethod> createBuilderMethods(PsiClass recordClass, PsiClass builderClass) {
        List<PsiMethod> methods = new ArrayList<>();
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(recordClass.getProject());
        PsiClassType builderType = factory.createType(builderClass);
        PsiClassType recordType = factory.createType(recordClass);

        // setter: setXxx methods
        for (PsiRecordComponent component : recordClass.getRecordComponents()) {
            String methodName = "set" + RecordBuilderUtils.capitalize(component.getName());
            LightMethodBuilder setter = new LightMethodBuilder(recordClass.getManager(), methodName);
            setter.setModifiers(PsiModifier.PUBLIC);
            setter.setContainingClass(builderClass);
            setter.addParameter(component.getName(), component.getType());
            setter.setMethodReturnType(builderType);
            setter.setNavigationElement(component);
            methods.add(setter);
        }

        // hasXxx methods
        for (PsiRecordComponent component : recordClass.getRecordComponents()) {
            String methodName = "has" + RecordBuilderUtils.capitalize(component.getName());
            LightMethodBuilder hasMethod = new LightMethodBuilder(recordClass.getManager(), methodName);
            hasMethod.setModifiers(PsiModifier.PUBLIC);
            hasMethod.setContainingClass(builderClass);
            hasMethod.setMethodReturnType(PsiTypes.booleanType());
            hasMethod.setNavigationElement(component);
            methods.add(hasMethod);
        }

        // getXxx methods
        for (PsiRecordComponent component : recordClass.getRecordComponents()) {
            String methodName = "get" + RecordBuilderUtils.capitalize(component.getName());
            LightMethodBuilder getMethod = new LightMethodBuilder(recordClass.getManager(), methodName);
            getMethod.setModifiers(PsiModifier.PUBLIC);
            getMethod.setContainingClass(builderClass);
            getMethod.setMethodReturnType(component.getType());
            getMethod.setNavigationElement(component);
            methods.add(getMethod);
        }

        // clearXxx methods
        for (PsiRecordComponent component : recordClass.getRecordComponents()) {
            String methodName = "clear" + RecordBuilderUtils.capitalize(component.getName());
            LightMethodBuilder clearMethod = new LightMethodBuilder(recordClass.getManager(), methodName);
            clearMethod.setModifiers(PsiModifier.PUBLIC);
            clearMethod.setContainingClass(builderClass);
            clearMethod.setMethodReturnType(builderType);
            clearMethod.setNavigationElement(component);
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

    private static class RecordBuilderLightClass extends LightPsiClassBuilder {
        private final String qualifiedName;
        private final PsiClass recordClass;

        public RecordBuilderLightClass(PsiClass recordClass, String simpleName, String qualifiedName) {
            super(recordClass, simpleName);
            this.recordClass = recordClass;
            this.qualifiedName = qualifiedName;
            setNavigationElement(recordClass);
        }

        @Override
        public String getQualifiedName() {
            return qualifiedName;
        }

        @Override
        public PsiFile getContainingFile() {
            return recordClass.getContainingFile();
        }

        @Override
        public PsiElement getParent() {
            return recordClass.getParent();
        }
    }
}
