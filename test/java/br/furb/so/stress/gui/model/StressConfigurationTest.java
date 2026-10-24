package br.furb.so.stress.gui.model;

import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;

public final class StressConfigurationTest {
    public static void main(String[] args) {
        acceptsLoadBoundariesAndOptionalDuration();
        preservesImmutableCoreSelection();
        rejectsInvalidConfiguration();
        validatesMetricBounds();
        System.out.println("StressConfigurationTest passed");
    }

    private static void acceptsLoadBoundariesAndOptionalDuration() {
        var minimum = new StressConfiguration(Set.of(0), 1, OptionalInt.empty());
        assert minimum.selectedCores().equals(Set.of(0));
        assert minimum.loadPercent() == 1;
        assert minimum.durationSeconds().isEmpty();

        var maximum = new StressConfiguration(Set.of(0, 1), 100, OptionalInt.of(60));
        assert maximum.selectedCores().equals(Set.of(0, 1));
        assert maximum.loadPercent() == 100;
        assert maximum.durationSeconds().getAsInt() == 60;
    }

    private static void preservesImmutableCoreSelection() {
        var selection = new HashSet<>(Set.of(0, 1));
        var configuration = new StressConfiguration(selection, 50, OptionalInt.empty());
        selection.clear();
        assert configuration.selectedCores().equals(Set.of(0, 1)) : "Must copy caller's selection";
        try {
            configuration.selectedCores().clear();
            throw new AssertionError("Selection must be immutable");
        } catch (UnsupportedOperationException expected) {
            // The exposed selection cannot change after construction.
        }
    }

    private static void rejectsInvalidConfiguration() {
        rejects(() -> new StressConfiguration(Set.of(), 50, OptionalInt.empty()));
        rejects(() -> new StressConfiguration(Set.of(0), 0, OptionalInt.empty()));
        rejects(() -> new StressConfiguration(Set.of(0), 101, OptionalInt.empty()));
        rejects(() -> new StressConfiguration(Set.of(0), 50, OptionalInt.of(0)));
        rejects(() -> new StressConfiguration(Set.of(0), 50, OptionalInt.of(-1)));
        rejects(() -> new StressConfiguration(Set.of(-1), 50, OptionalInt.empty()));
    }

    private static void validatesMetricBounds() {
        assert new CoreMetric(0, 0).utilizationPercent() == 0;
        assert new CoreMetric(1, 100).coreIndex() == 1;
        rejects(() -> new CoreMetric(-1, 50));
        rejects(() -> new CoreMetric(0, -0.1));
        rejects(() -> new CoreMetric(0, 100.1));
        rejects(() -> new CoreMetric(0, Double.NaN));
        rejects(() -> new CoreMetric(0, Double.POSITIVE_INFINITY));
    }

    private static void rejects(Runnable construction) {
        try {
            construction.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Invalid GUI inputs are rejected before running a simulation.
        }
    }
}
