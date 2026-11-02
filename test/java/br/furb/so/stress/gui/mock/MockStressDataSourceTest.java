package br.furb.so.stress.gui.mock;

import br.furb.so.stress.gui.model.CoreMetric;
import br.furb.so.stress.gui.model.StressConfiguration;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

public final class MockStressDataSourceTest {
    public static void main(String[] args) {
        containsExactlySelectedCoresNearTarget();
        followsConfigurationChanges();
        producesRepeatableSmoothUpdates();
        System.out.println("MockStressDataSourceTest passed");
    }

    private static void containsExactlySelectedCoresNearTarget() {
        for (int load : new int[] {1, 50, 100}) {
            var configuration = new StressConfiguration(Set.of(0, 2), load, OptionalInt.empty());
            var source = new MockStressDataSource();
            for (int sample = 0; sample < 100; sample++) {
                assertSample(source.next(configuration), Set.of(0, 2), load);
            }
        }
    }

    private static void followsConfigurationChanges() {
        var source = new MockStressDataSource();
        source.next(new StressConfiguration(Set.of(0, 2), 20, OptionalInt.empty()));
        var changed = new StressConfiguration(Set.of(1), 80, OptionalInt.of(5));
        assertSample(source.next(changed), Set.of(1), 80);
    }

    private static void producesRepeatableSmoothUpdates() {
        var configuration = new StressConfiguration(Set.of(0), 50, OptionalInt.empty());
        var firstSource = new MockStressDataSource();
        var secondSource = new MockStressDataSource();
        var previous = firstSource.next(configuration);
        assert previous.equals(secondSource.next(configuration));
        boolean varied = false;
        for (int sample = 0; sample < 20; sample++) {
            var current = firstSource.next(configuration);
            assert current.equals(secondSource.next(configuration)) : "Simulation must be repeatable";
            double change = Math.abs(current.get(0).utilizationPercent() - previous.get(0).utilizationPercent());
            assert change <= 2 : "Successive updates should vary gradually";
            varied |= change > 0.001;
            previous = current;
        }
        assert varied : "Simulation should not return a constant sample";
    }

    private static void assertSample(List<CoreMetric> metrics, Set<Integer> expectedCores, int target) {
        assert metrics.size() == expectedCores.size() : "One metric per selected core";
        var actualCores = new HashSet<Integer>();
        for (var metric : metrics) {
            assert actualCores.add(metric.coreIndex()) : "Duplicate core";
            assert metric.utilizationPercent() >= 0 && metric.utilizationPercent() <= 100;
            assert Math.abs(metric.utilizationPercent() - target) <= 5 : "Simulation must stay near target";
        }
        assert actualCores.equals(expectedCores) : "Only selected cores should appear";
    }
}
