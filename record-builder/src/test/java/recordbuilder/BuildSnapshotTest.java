package recordbuilder;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class BuildSnapshotTest {
    @RecordBuilder
    record Snapshot(List<@Nullable String> roles, Map<String, @Nullable String> attributes, int[] scores) {}

    @RecordBuilder
    record Overloaded(List<String> roles) {
        Overloaded(ArrayList<String> roles) {
            this(List.of("from overload"));
        }
    }

    @RecordBuilder
    record IdentityEntries(IdentityHashMap<List<String>, String> entries) {}

    @Test
    void reusingBuilderDoesNotChangePreviouslyBuiltRecords() {
        var scores = new int[] {1};
        var builder = BuildSnapshotTest_SnapshotBuilder.builder()
                .addRoles("reader")
                .addRoles(null)
                .putAttributes("theme", null)
                .setScores(scores);
        var first = builder.build();

        builder.addRoles("editor").putAttributes("theme", "dark");
        scores[0] = 2;
        var second = builder.build();

        assertThat(first.roles()).containsExactly("reader", null);
        assertThat(first.attributes()).containsEntry("theme", null);
        assertThat(first.scores()).containsExactly(1);
        assertThat(second.roles()).containsExactly("reader", null, "editor");
        assertThat(second.attributes()).containsExactlyEntriesOf(Map.of("theme", "dark"));
        assertThat(second.scores()).containsExactly(2);

        second.roles().clear();
        second.attributes().clear();
        second.scores()[0] = 3;
        assertThat(builder.getRoles()).containsExactly("reader", null, "editor");
        assertThat(builder.getAttributes()).containsEntry("theme", "dark");
        assertThat(builder.getScores()).containsExactly(2);
    }

    @Test
    void buildingPreservesUnsetFields() {
        var snapshot = BuildSnapshotTest_SnapshotBuilder.builder().build();

        assertThat(snapshot.roles()).isNull();
        assertThat(snapshot.attributes()).isNull();
        assertThat(snapshot.scores()).isNull();
    }

    @Test
    void snapshotPreservesConstructorSelection() {
        var record =
                BuildSnapshotTest_OverloadedBuilder.builder().addRoles("reader").build();

        assertThat(record.roles()).containsExactly("reader");
    }

    @Test
    void mapSnapshotPreservesIdentityBasedKeys() {
        var firstKey = new ArrayList<String>();
        var secondKey = new ArrayList<String>();
        var builder = BuildSnapshotTest_IdentityEntriesBuilder.builder()
                .putEntries(firstKey, "first")
                .putEntries(secondKey, "second");
        var snapshot = builder.build();
        builder.putEntries(firstKey, "changed");

        assertThat(snapshot.entries()).hasSize(2);
        assertThat(snapshot.entries().get(firstKey)).isEqualTo("first");
        assertThat(snapshot.entries().get(secondKey)).isEqualTo("second");
    }
}
