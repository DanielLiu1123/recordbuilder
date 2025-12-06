package recordbuilder;

/**
 * Constants used throughout the record builder processor.
 *
 * @author Freeman
 */
final class Constants {

    // Annotation names
    static final String JSPECIFY_NULLABLE = "org.jspecify.annotations.Nullable";
    static final String JAVAX_NULLABLE = "javax.annotation.Nullable";
    static final String JAKARTA_NULLABLE = "jakarta.annotation.Nullable";
    static final String JETBRAINS_NULLABLE = "org.jetbrains.annotations.Nullable";
    static final String ANDROIDX_NULLABLE = "androidx.annotation.Nullable";
    static final String CHECKERFRAMEWORK_NULLABLE = "org.checkerframework.checker.nullness.qual.Nullable";
    static final String FINDBUGS_NULLABLE = "edu.umd.cs.findbugs.annotations.Nullable";

    // Bitmap thresholds
    static final int INT_BITMAP_THRESHOLD = 32;
    static final int LONG_BITMAP_THRESHOLD = 64;
    static final int BITS_PER_LONG = 64;

    // Field names
    static final String PRESENCE_MASK_FIELD = "_presenceMask0_";

    // Method prefixes
    static final String SET_PREFIX = "set";
    static final String GET_PREFIX = "get";
    static final String CLEAR_PREFIX = "clear";
    static final String HAS_PREFIX = "has";

    private Constants() {
        throw new AssertionError("No instances");
    }
}
