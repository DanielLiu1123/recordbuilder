package recordbuilder;

import com.palantir.javapoet.CodeBlock;

/**
 * Helper class for generating presence bitmap operations.
 * Uses bitmap to track which fields have been set in the builder.
 *
 * @author Freeman
 */
final class PresenceBitmapHelper {

    private PresenceBitmapHelper() {
        throw new AssertionError("No instances");
    }

    /**
     * Generates code to set a bit in the presence bitmap.
     *
     * @param bitIndex the index of the bit to set
     * @param totalFields total number of fields
     * @return CodeBlock for setting the bit
     */
    static CodeBlock generateSetBitStatement(int bitIndex, int totalFields) {
        if (totalFields <= Constants.INT_BITMAP_THRESHOLD) {
            // Use int bitmap
            return CodeBlock.of("this." + Constants.PRESENCE_MASK_FIELD + " |= $L", "(1 << " + bitIndex + ")");
        } else if (totalFields <= Constants.LONG_BITMAP_THRESHOLD) {
            // Use long bitmap
            return CodeBlock.of("this." + Constants.PRESENCE_MASK_FIELD + " |= $L", "(1L << " + bitIndex + ")");
        } else {
            // Use long array bitmap
            int arrayIndex = bitIndex / Constants.BITS_PER_LONG;
            int bitOffset = bitIndex % Constants.BITS_PER_LONG;
            return CodeBlock.of(
                    "this." + Constants.PRESENCE_MASK_FIELD + "[$L] |= $L", arrayIndex, "(1L << " + bitOffset + ")");
        }
    }

    /**
     * Generates code to check if a bit is set in the presence bitmap.
     *
     * @param bitIndex the index of the bit to check
     * @param totalFields total number of fields
     * @return CodeBlock for checking the bit
     */
    static CodeBlock generateCheckBitExpression(int bitIndex, int totalFields) {
        if (totalFields <= Constants.INT_BITMAP_THRESHOLD) {
            // Use int bitmap
            return CodeBlock.of("(this." + Constants.PRESENCE_MASK_FIELD + " & $L) != 0", "(1 << " + bitIndex + ")");
        } else if (totalFields <= Constants.LONG_BITMAP_THRESHOLD) {
            // Use long bitmap
            return CodeBlock.of("(this." + Constants.PRESENCE_MASK_FIELD + " & $L) != 0", "(1L << " + bitIndex + ")");
        } else {
            // Use long array bitmap
            int arrayIndex = bitIndex / Constants.BITS_PER_LONG;
            int bitOffset = bitIndex % Constants.BITS_PER_LONG;
            return CodeBlock.of(
                    "(this." + Constants.PRESENCE_MASK_FIELD + "[$L] & $L) != 0",
                    arrayIndex,
                    "(1L << " + bitOffset + ")");
        }
    }

    /**
     * Generates code to clear a bit in the presence bitmap.
     *
     * @param bitIndex the index of the bit to clear
     * @param totalFields total number of fields
     * @return CodeBlock for clearing the bit
     */
    static CodeBlock generateClearBitStatement(int bitIndex, int totalFields) {
        if (totalFields <= Constants.INT_BITMAP_THRESHOLD) {
            // Use int bitmap
            return CodeBlock.of("this." + Constants.PRESENCE_MASK_FIELD + " &= ~$L", "(1 << " + bitIndex + ")");
        } else if (totalFields <= Constants.LONG_BITMAP_THRESHOLD) {
            // Use long bitmap
            return CodeBlock.of("this." + Constants.PRESENCE_MASK_FIELD + " &= ~$L", "(1L << " + bitIndex + ")");
        } else {
            // Use long array bitmap
            int arrayIndex = bitIndex / Constants.BITS_PER_LONG;
            int bitOffset = bitIndex % Constants.BITS_PER_LONG;
            return CodeBlock.of(
                    "this." + Constants.PRESENCE_MASK_FIELD + "[$L] &= ~$L", arrayIndex, "(1L << " + bitOffset + ")");
        }
    }
}
