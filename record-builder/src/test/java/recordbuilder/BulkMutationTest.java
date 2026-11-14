package recordbuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BulkMutationTest {
    @RecordBuilder
    record Batch(List<String> items, Map<String, String> entries) {}

    @Test
    void invalidCollectionDoesNotChangeAnUnsetField() {
        var builder = BatchBuilder.builder();
        assertThatThrownBy(() -> builder.addAllItems(Arrays.asList("valid", null)))
                .isInstanceOf(NullPointerException.class);
        assertThat(builder.getItems()).isNull();
        assertThat(builder.hasItems()).isFalse();
    }

    @Test
    void invalidCollectionPreservesExistingValuesAndPresence() {
        var builder = BatchBuilder.builder().addItems("existing");
        var items = builder.getItems();
        assertThatThrownBy(() -> builder.addAllItems(Arrays.asList("valid", null)))
                .isInstanceOf(NullPointerException.class);
        assertThat(builder.getItems()).isSameAs(items).containsExactly("existing");
        assertThat(builder.hasItems()).isTrue();
    }

    @Test
    void invalidMapDoesNotChangeAnUnsetField() {
        var builder = BatchBuilder.builder();
        var values = new LinkedHashMap<String, String>();
        values.put("valid", "value");
        values.put("invalid", null);
        assertThatThrownBy(() -> builder.putAllEntries(values)).isInstanceOf(NullPointerException.class);
        assertThat(builder.getEntries()).isNull();
        assertThat(builder.hasEntries()).isFalse();
    }

    @Test
    void invalidMapPreservesExistingValuesAndPresence() {
        var builder = BatchBuilder.builder().putEntries("existing", "original");
        var entries = builder.getEntries();
        var values = new LinkedHashMap<String, String>();
        values.put("existing", "replacement");
        values.put(null, "invalid");
        assertThatThrownBy(() -> builder.putAllEntries(values)).isInstanceOf(NullPointerException.class);
        assertThat(builder.getEntries()).isSameAs(entries).containsExactlyEntriesOf(Map.of("existing", "original"));
        assertThat(builder.hasEntries()).isTrue();
    }

    @Test
    void failingIterableDoesNotPartiallyWrite() {
        var builder = BatchBuilder.builder();
        Iterable<String> values = () -> new Iterator<>() {
            private boolean first = true;

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public String next() {
                if (first) {
                    first = false;
                    return "valid";
                }
                throw new IllegalStateException("Cannot read the next value");
            }
        };
        assertThatThrownBy(() -> builder.addAllItems(values)).isInstanceOf(IllegalStateException.class);
        assertThat(builder.getItems()).isNull();
        assertThat(builder.hasItems()).isFalse();
    }

    @Test
    void collectionCanBeAddedToItself() {
        var builder = BatchBuilder.builder().addItems("one").addItems("two");
        var items = builder.getItems();
        builder.addAllItems(items);
        assertThat(builder.getItems()).isSameAs(items).containsExactly("one", "two", "one", "two");
    }

    @Test
    void nullableElementsAndEntriesRemainSupported() {
        var builder = EverythingBuilder.builder();
        builder.addAllListNullableString(Arrays.asList("value", null));
        var values = new LinkedHashMap<String, Integer>();
        values.put("key", null);
        values.put(null, 1);
        builder.putAllMapNullableStringNullableInteger(values);
        assertThat(builder.getListNullableString()).containsExactly("value", null);
        assertThat(builder.getMapNullableStringNullableInteger()).containsExactlyInAnyOrderEntriesOf(values);
        assertThat(builder.hasListNullableString()).isTrue();
        assertThat(builder.hasMapNullableStringNullableInteger()).isTrue();
    }

    @Test
    void emptyInputsSetPresenceAndSuccessfulUpdatesKeepExistingReferences() {
        var builder = BatchBuilder.builder().addAllItems(List.of()).putAllEntries(Map.of());
        assertThat(builder.hasItems()).isTrue();
        assertThat(builder.hasEntries()).isTrue();
        var items = builder.getItems();
        var entries = builder.getEntries();
        builder.addAllItems(List.of("value")).putAllEntries(Map.of("key", "value"));
        assertThat(items).isSameAs(builder.getItems()).containsExactly("value");
        assertThat(entries).isSameAs(builder.getEntries()).containsExactlyEntriesOf(Map.of("key", "value"));
    }
}
