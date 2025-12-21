package recordbuilder.idea;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for RecordBuilder plugin.
 *
 * @author Freeman
 */
public final class RecordBuilderUtils {

    private static final String RECORD_BUILDER_ANNOTATION = "recordbuilder.RecordBuilder";

    private RecordBuilderUtils() {}

    /**
     * Check if a class is annotated with @RecordBuilder.
     */
    public static boolean hasRecordBuilderAnnotation(@NotNull PsiClass psiClass) {
        return psiClass.getAnnotation(RECORD_BUILDER_ANNOTATION) != null;
    }

    /**
     * Check if a class is a record and is annotated with @RecordBuilder.
     */
    public static boolean isRecordBuilder(@NotNull PsiClass psiClass) {
        return psiClass.isRecord() && hasRecordBuilderAnnotation(psiClass);
    }

    /**
     * Get the builder class name for a record.
     */
    @NotNull
    public static String getBuilderClassName(@NotNull PsiClass recordClass) {
        return recordClass.getName() + "Builder";
    }

    /**
     * Get the builder class qualified name for a record.
     */
    @NotNull
    public static String getBuilderQualifiedName(@NotNull PsiClass recordClass) {
        String packageName = getPackageName(recordClass);
        String builderName = getBuilderClassName(recordClass);
        return packageName.isEmpty() ? builderName : packageName + "." + builderName;
    }

    /**
     * Get the package name of a class.
     */
    @NotNull
    public static String getPackageName(@NotNull PsiClass psiClass) {
        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null) {
            return "";
        }
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot > 0 ? qualifiedName.substring(0, lastDot) : "";
    }

    /**
     * Capitalize the first letter of a string.
     */
    @NotNull
    public static String capitalize(@NotNull String str) {
        if (str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    /**
     * Check if a record component is nullable based on annotations.
     */
    public static boolean isNullable(@NotNull PsiRecordComponent component) {
        PsiAnnotation[] annotations = component.getAnnotations();
        for (PsiAnnotation annotation : annotations) {
            String qualifiedName = annotation.getQualifiedName();
            if (isNullableAnnotation(qualifiedName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if an annotation is a nullable annotation.
     */
    private static boolean isNullableAnnotation(@Nullable String qualifiedName) {
        if (qualifiedName == null) {
            return false;
        }
        return qualifiedName.equals("org.jspecify.annotations.Nullable")
                || qualifiedName.equals("javax.annotation.Nullable")
                || qualifiedName.equals("jakarta.annotation.Nullable")
                || qualifiedName.equals("org.jetbrains.annotations.Nullable")
                || qualifiedName.equals("androidx.annotation.Nullable")
                || qualifiedName.equals("org.checkerframework.checker.nullness.qual.Nullable")
                || qualifiedName.equals("edu.umd.cs.findbugs.annotations.Nullable");
    }

    /**
     * Check if a PsiClass exists in the current scope.
     */
    public static boolean classExists(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
        JavaPsiFacade facade = JavaPsiFacade.getInstance(scope.getProject());
        PsiClass psiClass = facade.findClass(qualifiedName, scope);
        return psiClass != null;
    }
}
