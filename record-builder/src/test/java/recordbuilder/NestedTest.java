package recordbuilder;

/**
 *
 *
 * @author Freeman
 */
class NestedTest {
    @RecordBuilder
    record Level1(Level2 level2) {
        @RecordBuilder
        record Level2(Level3 level3) {
            @RecordBuilder
            record Level3() {}
        }
    }
}
