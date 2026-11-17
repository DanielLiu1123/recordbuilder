package recordbuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Comparator;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class SortedComparatorTest {
    record Score(int points) {}

    @RecordBuilder
    record Leaderboard(SortedSet<Score> scores, SortedMap<Score, String> labels) {}

    @RecordBuilder
    record EmptySorted(TreeSet<Integer> scores, TreeMap<Integer, String> labels) {}

    @Test
    void prototypeCopyKeepsComparatorsForNonComparableElementsAndKeys() {
        Comparator<Score> order = Comparator.comparingInt(Score::points).reversed();
        var lower = new Score(10);
        var higher = new Score(20);
        var scores = new TreeSet<>(order);
        scores.add(lower);
        scores.add(higher);
        var labels = new TreeMap<Score, String>(order);
        labels.put(lower, "lower");
        labels.put(higher, "higher");

        var builder = SortedComparatorTest_LeaderboardBuilder.builder(new Leaderboard(scores, labels));
        var copy = builder.build();
        assertThat(copy.scores().comparator()).isSameAs(order);
        assertThat(copy.labels().comparator()).isSameAs(order);
        assertThat(copy.scores()).containsExactly(higher, lower);
        assertThat(copy.labels().keySet()).containsExactly(higher, lower);

        var highest = new Score(30);
        builder.addScores(highest).putLabels(highest, "highest");
        assertThat(builder.build().scores().first()).isEqualTo(highest);
        assertThat(builder.build().labels().firstKey()).isEqualTo(highest);
        assertThat(copy.scores()).containsExactly(higher, lower);
        assertThat(copy.labels().keySet()).containsExactly(higher, lower);
    }

    @Test
    void mergeAdoptsComparatorsFromEmptySourcesAndKeepsThemOnLaterMerges() {
        Comparator<Integer> reverse = Comparator.reverseOrder();
        var builder = SortedComparatorTest_EmptySortedBuilder.builder()
                .merge(new EmptySorted(new TreeSet<>(reverse), new TreeMap<>(reverse)));
        assertThat(builder.hasScores()).isTrue();
        assertThat(builder.hasLabels()).isTrue();

        var scores = new TreeSet<Integer>();
        scores.add(1);
        scores.add(2);
        var labels = new TreeMap<Integer, String>();
        labels.put(1, "one");
        labels.put(2, "two");
        var merged = builder.merge(new EmptySorted(scores, labels)).build();

        assertThat(merged.scores().comparator()).isSameAs(reverse);
        assertThat(merged.labels().comparator()).isSameAs(reverse);
        assertThat(merged.scores()).containsExactly(2, 1);
        assertThat(merged.labels().keySet()).containsExactly(2, 1);
    }

    @Test
    void copyingStillRejectsInvalidNullElementsAndValues() {
        Comparator<Score> order = Comparator.nullsLast(Comparator.comparingInt(Score::points));
        var scores = new TreeSet<>(order);
        scores.add(null);
        var labels = new TreeMap<Score, String>(order);
        labels.put(new Score(1), null);
        labels.put(null, "later entry");
        var builder = SortedComparatorTest_LeaderboardBuilder.builder();

        assertThatThrownBy(() -> builder.merge(new Leaderboard(scores, null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("value cannot be null");
        assertThatThrownBy(() -> builder.merge(new Leaderboard(null, labels)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("value cannot be null");
        assertThat(builder.hasScores()).isFalse();
        assertThat(builder.hasLabels()).isFalse();
        assertThat(builder.getScores()).isNull();
        assertThat(builder.getLabels()).isNull();

        labels.remove(new Score(1));
        assertThatThrownBy(() -> builder.merge(new Leaderboard(null, labels)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("key cannot be null");
        assertThat(builder.hasLabels()).isFalse();
        assertThat(builder.getLabels()).isNull();
    }
}
