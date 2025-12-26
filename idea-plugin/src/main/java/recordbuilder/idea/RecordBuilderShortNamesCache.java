package recordbuilder.idea;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.Processor;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class RecordBuilderShortNamesCache extends PsiShortNamesCache {
    private final Project project;

    public RecordBuilderShortNamesCache(Project project) {
        this.project = project;
    }

    @Override
    public PsiClass @NotNull [] getClassesByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
        List<PsiClass> result = new ArrayList<>();
        for (PsiShortNamesCache cache : PsiShortNamesCache.EP_NAME.getExtensions(project)) {
            if (cache == this) {
                continue;
            }
            for (PsiClass recordClass : cache.getClassesByName(name, scope)) {
                if (recordClass.isRecord() && RecordBuilderUtils.hasRecordBuilderAnnotation(recordClass)) {
                    for (var psiElementFinder : PsiElementFinder.EP.getExtensions(project)) {
                        if (psiElementFinder instanceof RecordBuilderElementFinder finder) {
                            var builderFQN = RecordBuilderUtils.getBuilderQualifiedName(recordClass);
                            PsiClass builderClass = finder.findClass(builderFQN, scope);
                            if (builderClass != null) {
                                result.add(builderClass);
                            }
                        }
                    }
                }
            }
        }
        return result.toArray(PsiClass.EMPTY_ARRAY);
    }

    @Override
    public String @NotNull [] getAllClassNames() {
        return new String[0];
    }

    @Override
    public PsiMethod @NotNull [] getMethodsByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
        return PsiMethod.EMPTY_ARRAY;
    }

    @Override
    public PsiMethod @NotNull [] getMethodsByNameIfNotMoreThan(
            @NonNls @NotNull String name, @NotNull GlobalSearchScope scope, int maxCount) {
        return PsiMethod.EMPTY_ARRAY;
    }

    @Override
    public String @NotNull [] getAllMethodNames() {
        return new String[0];
    }

    @Override
    public boolean processMethodsWithName(
            @NonNls @NotNull String name,
            @NotNull GlobalSearchScope scope,
            @NotNull Processor<? super PsiMethod> processor) {
        return true;
    }

    @Override
    public PsiField @NotNull [] getFieldsByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
        return PsiField.EMPTY_ARRAY;
    }

    @Override
    public PsiField @NotNull [] getFieldsByNameIfNotMoreThan(
            @NonNls @NotNull String name, @NotNull GlobalSearchScope scope, int maxCount) {
        return PsiField.EMPTY_ARRAY;
    }

    @Override
    public String @NotNull [] getAllFieldNames() {
        return new String[0];
    }
}
