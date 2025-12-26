package recordbuilder.idea;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RecordBuilderUtils {

    public static final String ANNOTATION_FQN = "recordbuilder.RecordBuilder";

    private RecordBuilderUtils() {}

    public static boolean hasRecordBuilderAnnotation(@Nullable PsiClass psiClass) {
        return psiClass != null && psiClass.isRecord() && psiClass.getAnnotation(ANNOTATION_FQN) != null;
    }

    @NotNull
    public static String getBuilderSimpleClassName(@NotNull PsiClass recordClass) {
        return recordClass.getName() + "Builder";
    }

    @NotNull
    public static String getBuilderFQN(@NotNull PsiClass recordClass) {
        String builderSimpleClassName = getBuilderSimpleClassName(recordClass);
        PsiFile containingFile = recordClass.getContainingFile();
        if (containingFile instanceof PsiJavaFile psiJavaFile) {
            String packageName = psiJavaFile.getPackageName();
            return packageName.isEmpty() ? builderSimpleClassName : packageName + "." + builderSimpleClassName;
        }
        return builderSimpleClassName;
    }

    @NotNull
    public static String capitalize(@NotNull String str) {
        if (str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
