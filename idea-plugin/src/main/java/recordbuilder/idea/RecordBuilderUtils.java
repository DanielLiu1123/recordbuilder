package recordbuilder.idea;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

public final class RecordBuilderUtils {

    public static final String ANNOTATION_FQN = "recordbuilder.RecordBuilder";

    private RecordBuilderUtils() {}

    public static boolean hasRecordBuilderAnnotation(@NotNull PsiClass psiClass) {
        return psiClass.getAnnotation(ANNOTATION_FQN) != null;
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

    public static @NotNull String capitalize(@NotNull String str) {
        if (str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
