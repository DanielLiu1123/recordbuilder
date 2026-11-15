package recordbuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class BulkMutationTest {
    @RecordBuilder
    record Batch(List<String> items, Map<String, String> entries) {}

    @RecordBuilder
    record ConcurrentBatch(CopyOnWriteArrayList<String> items) {}

    @RecordBuilder
    record Maps(
            LinkedHashMap<String, Integer> ordered,
            java.util.IdentityHashMap<String, Integer> identity,
            Map<Object, String> throwing) {}

    @Test
    void initialMapsPreserveOrderIdentityAndInputIndependence() {
        var first = new String("same");
        var second = new String("same");
        var identity = new java.util.IdentityHashMap<String, Integer>();
        identity.put(first, 1);
        identity.put(second, 2);
        var ordered = new LinkedHashMap<String, Integer>();
        ordered.put("z", 1);
        ordered.put("a", 2);
        var builder =
                BulkMutationTest_MapsBuilder.builder().putAllOrdered(ordered).putAllIdentity(identity);
        ordered.clear();
        identity.clear();
        assertThat(builder.getOrdered().keySet()).containsExactly("z", "a");
        assertThat(builder.getIdentity()).hasSize(2);
        assertThat(builder.getIdentity().get(first)).isEqualTo(1);
        assertThat(builder.getIdentity().get(second)).isEqualTo(2);
        var result = builder.getOrdered();
        builder.putAllOrdered(result).putOrdered("next", 3);
        assertThat(builder.getOrdered()).isSameAs(result);
        assertThat(result.keySet()).containsExactly("z", "a", "next");
    }

    @Test
    void initialMapInsertionFailureDoesNotPublishPartialState() {
        Object key = new Object() {
            @Override
            public int hashCode() {
                throw new IllegalStateException("hash failed");
            }
        };
        var input = new java.util.IdentityHashMap<Object, String>();
        input.put(key, "value");
        var builder = BulkMutationTest_MapsBuilder.builder();
        assertThatThrownBy(() -> builder.putAllThrowing(input)).isInstanceOf(IllegalStateException.class);
        assertThat(builder.getThrowing()).isNull();
        assertThat(builder.hasThrowing()).isFalse();
    }

    @Test
    void initialBulkPutPreservesMapCreatedByInputCallback() {
        var builder = BulkMutationTest_BatchBuilder.builder();
        var observed = new ArrayList<Map<String, String>>();
        var input = new AbstractMap<String, String>() {
            @Override
            public Set<Entry<String, String>> entrySet() {
                builder.putEntries("side", "effect");
                observed.add(builder.getEntries());
                return Set.of(Map.entry("input", "value"));
            }
        };
        builder.putAllEntries(input);
        assertThat(builder.getEntries())
                .isSameAs(observed.get(0))
                .containsExactlyInAnyOrderEntriesOf(Map.of("side", "effect", "input", "value"));
    }

    @Test
    void mapIterationFailurePreservesUnsetAndExistingFields() {
        var input = new AbstractMap<String, String>() {
            @Override
            public Set<Entry<String, String>> entrySet() {
                return new java.util.AbstractSet<>() {
                    @Override
                    public int size() {
                        return 2;
                    }

                    @Override
                    public Iterator<Entry<String, String>> iterator() {
                        return new Iterator<>() {
                            private boolean first = true;

                            @Override
                            public boolean hasNext() {
                                return true;
                            }

                            @Override
                            public Entry<String, String> next() {
                                if (first) {
                                    first = false;
                                    return Map.entry("valid", "value");
                                }
                                throw new IllegalStateException("iteration failed");
                            }
                        };
                    }
                };
            }
        };
        var builder = BulkMutationTest_BatchBuilder.builder();
        assertThatThrownBy(() -> builder.putAllEntries(input)).isInstanceOf(IllegalStateException.class);
        assertThat(builder.getEntries()).isNull();
        assertThat(builder.hasEntries()).isFalse();
        builder.putEntries("existing", "original");
        var entries = builder.getEntries();
        assertThatThrownBy(() -> builder.putAllEntries(input)).isInstanceOf(IllegalStateException.class);
        assertThat(builder.getEntries()).isSameAs(entries).containsExactlyEntriesOf(Map.of("existing", "original"));
        assertThat(builder.hasEntries()).isTrue();
    }

    @Test
    void copyOnWriteBulkAppendPreservesReferencesAndValidatesBeforeWriting() {
        var builder = BulkMutationTest_ConcurrentBatchBuilder.builder().addItems("existing");
        var items = builder.getItems();
        assertThatThrownBy(() -> builder.addAllItems(Arrays.asList("valid", null)))
                .isInstanceOf(NullPointerException.class);
        assertThat(items).containsExactly("existing");
        builder.addAllItems(items);
        assertThat(builder.getItems()).isSameAs(items).containsExactly("existing", "existing");
        assertThat(builder.hasItems()).isTrue();
    }

    @Test
    void initialBulkListRemainsIndependentOfInputAndSupportsFurtherMutation() {
        var input = new ArrayList<>(List.of("first"));
        var builder = BulkMutationTest_BatchBuilder.builder().addAllItems(input);
        var items = builder.getItems();
        input.clear();
        builder.addItems("second").addAllItems(List.of("third"));
        assertThat(builder.getItems()).isSameAs(items).containsExactly("first", "second", "third");
    }

    @Test
    void invalidCollectionDoesNotChangeAnUnsetField() {
        var builder = BulkMutationTest_BatchBuilder.builder();
        assertThatThrownBy(() -> builder.addAllItems(Arrays.asList("valid", null)))
                .isInstanceOf(NullPointerException.class);
        assertThat(builder.getItems()).isNull();
        assertThat(builder.hasItems()).isFalse();
    }

    @Test
    void invalidCollectionPreservesExistingValuesAndPresence() {
        var builder = BulkMutationTest_BatchBuilder.builder().addItems("existing");
        var items = builder.getItems();
        assertThatThrownBy(() -> builder.addAllItems(Arrays.asList("valid", null)))
                .isInstanceOf(NullPointerException.class);
        assertThat(builder.getItems()).isSameAs(items).containsExactly("existing");
        assertThat(builder.hasItems()).isTrue();
    }

    @Test
    void invalidMapDoesNotChangeAnUnsetField() {
        var builder = BulkMutationTest_BatchBuilder.builder();
        var values = new LinkedHashMap<String, String>();
        values.put("valid", "value");
        values.put("invalid", null);
        assertThatThrownBy(() -> builder.putAllEntries(values)).isInstanceOf(NullPointerException.class);
        assertThat(builder.getEntries()).isNull();
        assertThat(builder.hasEntries()).isFalse();
    }

    @Test
    void invalidMapPreservesExistingValuesAndPresence() {
        var builder = BulkMutationTest_BatchBuilder.builder().putEntries("existing", "original");
        var entries = builder.getEntries();
        var values = new LinkedHashMap<String, String>();
        values.put("existing", "replacement");
        values.put(null, "invalid");
        assertThatThrownBy(() -> builder.putAllEntries(values)).isInstanceOf(NullPointerException.class);
        assertThat(builder.getEntries()).isSameAs(entries).containsExactlyEntriesOf(Map.of("existing", "original"));
        assertThat(builder.hasEntries()).isTrue();
    }

    @Test
    void bulkPutStoresTheSameEntryValueItValidated() {
        var entry = new AbstractMap.SimpleEntry<>("key", "observed") {
            @Override
            public String getValue() {
                String value = super.getValue();
                // Simulate a live entry changing after its value is read.
                setValue(null);
                return value;
            }
        };
        var values = new AbstractMap<String, String>() {
            @Override
            public Set<Entry<String, String>> entrySet() {
                return Set.of(entry);
            }
        };

        var builder = BulkMutationTest_BatchBuilder.builder().putAllEntries(values);
        assertThat(builder.getEntries()).containsExactlyEntriesOf(Map.of("key", "observed"));
    }

    @Test
    void failingIterableDoesNotPartiallyWrite() {
        var builder = BulkMutationTest_BatchBuilder.builder();
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
        var builder = BulkMutationTest_BatchBuilder.builder().addItems("one").addItems("two");
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
        var builder =
                BulkMutationTest_BatchBuilder.builder().addAllItems(List.of()).putAllEntries(Map.of());
        assertThat(builder.hasItems()).isTrue();
        assertThat(builder.hasEntries()).isTrue();
        var items = builder.getItems();
        var entries = builder.getEntries();
        builder.addAllItems(List.of("value")).putAllEntries(Map.of("key", "value"));
        assertThat(items).isSameAs(builder.getItems()).containsExactly("value");
        assertThat(entries).isSameAs(builder.getEntries()).containsExactlyEntriesOf(Map.of("key", "value"));
    }
}
