package br.furb.so.stress.gui.model;

import br.furb.so.stress.metrics.StressStats;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

public final class RealStressMetricsTest {
    public static void main(String[] args) {
        mapsWorkersToSortedSelectedCores();
        ignoresAnInactiveSnapshot();
        rejectsIncompatibleWorkerCount();
        System.out.println("RealStressMetricsTest passed");
    }

    private static void mapsWorkersToSortedSelectedCores() {
        var configuration = new StressConfiguration(Set.of(3, 1), 50, OptionalInt.empty());
        var stats = new StressStats(500, 2, new double[] {42.5, 78.0}, new double[] {0, 0}, 0.1);

        assert RealStressMetrics.from(configuration, stats).equals(List.of(
            new CoreMetric(1, 42.5), new CoreMetric(3, 78.0)
        ));
    }

    private static void ignoresAnInactiveSnapshot() {
        var configuration = new StressConfiguration(Set.of(0), 50, OptionalInt.empty());
        var stats = new StressStats(0, 0, new double[0], new double[0], Double.NaN);

        assert RealStressMetrics.from(configuration, stats).isEmpty();
    }

    private static void rejectsIncompatibleWorkerCount() {
        var configuration = new StressConfiguration(Set.of(0, 1), 50, OptionalInt.empty());
        var stats = new StressStats(500, 1, new double[] {50}, new double[] {0}, 0.1);
        try {
            RealStressMetrics.from(configuration, stats);
            throw new AssertionError("Expected worker-count validation");
        } catch (IllegalArgumentException expected) {
            // A valid run has exactly one worker for each selected core.
        }
    }
}
