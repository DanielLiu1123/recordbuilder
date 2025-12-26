package recordbuilder.idea;

import com.intellij.icons.AllIcons;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
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
import java.util.List;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RecordBuilderElementFinder extends PsiElementFinder {

    @Nullable
    @Override
    public PsiClass findClass(@NotNull String builderFQN, @NotNull GlobalSearchScope scope) {
        if (!builderFQN.endsWith("Builder")) {
            return null;
        }

        Project project = scope.getProject();
        if (project == null) {
            return null;
        }

        var facade = JavaPsiFacade.getInstance(project);
        var recordFQN = builderFQN.substring(0, builderFQN.length() - "Builder".length());

        // For non-nested records, we can find directly
        PsiClass recordClass = facade.findClass(recordFQN, scope);
        if (RecordBuilderUtils.hasRecordBuilderAnnotation(recordClass)) {
            if (isAbsent(scope, builderFQN)) {
                return createLightBuilderClass(recordClass, builderFQN);
            }
        }

        // For nested records, we need to search in the package
        String packageName = StringUtil.getPackageName(builderFQN);
        PsiPackage psiPackage = facade.findPackage(packageName);
        if (psiPackage != null) {
            for (PsiClass clazz : getClasses(psiPackage, scope)) {
                if (builderFQN.equals(clazz.getQualifiedName())) {
                    return clazz;
                }
            }
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
        var project = scope.getProject();
        if (project == null) {
            return PsiClass.EMPTY_ARRAY;
        }

        var result = new ArrayList<PsiClass>();
        for (PsiFile file : psiPackage.getFiles(scope)) {
            if (file instanceof PsiJavaFile javaFile) {
                for (PsiClass psiClass : javaFile.getClasses()) {
                    collectBuilderClasses(scope, psiClass, result);
                }
            }
        }

        return result.toArray(PsiClass.EMPTY_ARRAY);
    }

    private boolean isAbsent(GlobalSearchScope scope, String fqn) {
        var project = scope.getProject();
        if (project == null) {
            return true;
        }
        var finders = PsiElementFinder.EP.getExtensions(project);
        for (var finder : finders) {
            if (finder == this) {
                continue;
            }
            var psiClass = finder.findClass(fqn, scope);
            if (psiClass != null) {
                return false;
            }
        }
        return true;
    }

    private void collectBuilderClasses(GlobalSearchScope scope, PsiClass psiClass, ArrayList<PsiClass> result) {
        if (RecordBuilderUtils.hasRecordBuilderAnnotation(psiClass)) {
            String builderFQN = RecordBuilderUtils.getBuilderFQN(psiClass);
            if (isAbsent(scope, builderFQN)) {
                result.add(createLightBuilderClass(psiClass, builderFQN));
            }
        }
        for (PsiClass innerClass : psiClass.getInnerClasses()) {
            collectBuilderClasses(scope, innerClass, result);
        }
    }

    private static PsiClass createLightBuilderClass(PsiClass recordClass, String builderFQN) {
        String builderClassName = RecordBuilderUtils.getBuilderSimpleClassName(recordClass);
        var builder = new RecordBuilderLightClass(recordClass, builderClassName, builderFQN);
        var modifierList = builder.getModifierList();
        if (recordClass.hasModifier(JvmModifier.PUBLIC)) {
            modifierList.addModifier(PsiModifier.PUBLIC);
        } else if (recordClass.hasModifier(JvmModifier.PRIVATE)) {
            modifierList.addModifier(PsiModifier.PRIVATE);
        } else if (recordClass.hasModifier(JvmModifier.PROTECTED)) {
            modifierList.addModifier(PsiModifier.PROTECTED);
        }
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

    static final class RecordBuilderLightClass extends LightPsiClassBuilder {
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
            return recordClass.getContainingFile();
        }

        @Override
        public Icon getIcon(int flags) {
            return AllIcons.Nodes.Class;
        }
    }
}
