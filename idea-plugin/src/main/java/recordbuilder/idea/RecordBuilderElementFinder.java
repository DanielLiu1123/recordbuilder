package recordbuilder.idea;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.light.LightPsiClassBuilder;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.ArrayList;
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
        // TODO(Freeman): Do we really need this?
        //        var result = new ArrayList<PsiClass>();
        //        for (PsiFile file : psiPackage.getFiles(scope)) {
        //            if (file instanceof PsiJavaFile javaFile) {
        //                for (PsiClass psiClass : javaFile.getClasses()) {
        //                    collectBuilderClasses(psiClass, result);
        //                }
        //            }
        //        }
        //
        //        return result.toArray(PsiClass.EMPTY_ARRAY);
        return super.getClasses(psiPackage, scope);
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

    private static PsiClass createLightBuilderClass(PsiClass recordClass, String qualifiedName) {
        String builderClassName = RecordBuilderUtils.getBuilderClassName(recordClass);
        var builder = new RecordBuilderLightClass(recordClass, builderClassName, qualifiedName);
        builder.getModifierList().addModifier(PsiModifier.PUBLIC);
        builder.getModifierList().addModifier(PsiModifier.FINAL);
        return builder;
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
            return getContainingFile();
        }
    }
}
