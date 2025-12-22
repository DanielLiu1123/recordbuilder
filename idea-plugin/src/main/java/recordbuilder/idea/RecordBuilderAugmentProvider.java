package recordbuilder.idea;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.augment.PsiAugmentProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Augments record classes annotated with @RecordBuilder to add static builder() methods.
 *
 * <p>This allows code like {@code UserBuilder.builder()} to work without compilation.
 *
 * @author Freeman
 */
public class RecordBuilderAugmentProvider extends PsiAugmentProvider {

    @NotNull
    @Override
    protected <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element, @NotNull Class<Psi> type) {
        if (!(element instanceof PsiClass)) {
            return Collections.emptyList();
        }

        PsiClass psiClass = (PsiClass) element;

        // Only process record classes with @RecordBuilder annotation
        if (!RecordBuilderUtils.isRecordBuilder(psiClass)) {
            return Collections.emptyList();
        }

        List<Psi> augments = new ArrayList<>();

        // For records, we don't add builder() methods to the record itself
        // because they should be on the generated Builder class
        // This provider is kept for potential future extensions

        return augments;
    }
}
