package br.furb.so.stress.gui.controller;

import br.furb.so.stress.gui.model.StressConfiguration;
import br.furb.so.stress.gui.model.StressMode;
import br.furb.so.stress.metrics.StressStats;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class StressExecutionControllerTest {
    public static void main(String[] args) throws Exception {
        startsSortedCoresAndStopsOnItsControlThread();
        ignoresInactiveSnapshots();
        stopsOnceAfterConfiguredDuration();
        startsOnlyEvenOrOddCoresAsStaticList();
        circularRotatesOneCoreAtATimeWithoutOverlap();
        circularWithSingleCoreKeepsRunning();
        circularStopCancelsRotationAndStopsOnce();
        System.out.println("StressExecutionControllerTest passed");
    }

    private static void startsSortedCoresAndStopsOnItsControlThread() throws Exception {
        var bridge = new FakeBridge(false);
        var metrics = new ArrayList<StressStats>();
        try (var controller = new StressExecutionController(bridge, metrics::add, failure -> {
            throw new AssertionError(failure);
        }, () -> 0.1, 10)) {
            controller.start(configuration(OptionalInt.empty())).join();
            await(() -> metrics.size() >= 1, "Expected a measured delta");
            controller.stop().join();
        }

        assert java.util.Arrays.equals(bridge.cores, new int[] {1, 3});
        assert java.util.Arrays.equals(bridge.loads, new int[] {50, 50});
        assert bridge.startCalls.get() == 1;
        assert bridge.stopCalls.get() == 1;
        assert new java.util.HashSet<>(bridge.controlThreads).size() == 1
            : "JNI calls must share one control thread";
    }

    private static void ignoresInactiveSnapshots() throws Exception {
        var bridge = new FakeBridge(true);
        var metrics = new ArrayList<StressStats>();
        try (var controller = new StressExecutionController(bridge, metrics::add, failure -> {
            throw new AssertionError(failure);
        }, () -> 0.1, 10)) {
            controller.start(configuration(OptionalInt.empty())).join();
            Thread.sleep(80);
            controller.stop().join();
        }
        assert metrics.isEmpty() : "Inactive snapshots must not clear the last real frame";
    }

    private static void stopsOnceAfterConfiguredDuration() throws Exception {
        var bridge = new FakeBridge(false);
        try (var controller = new StressExecutionController(bridge, stats -> { }, failure -> {
            throw new AssertionError(failure);
        }, () -> 0.1, 10)) {
            controller.start(configuration(OptionalInt.of(1))).join();
            await(() -> bridge.stopCalls.get() == 1, "Duration did not stop the run");
            Thread.sleep(80);
        }
        assert bridge.stopCalls.get() == 1 : "Duration must request exactly one stop";
    }

    private static void startsOnlyEvenOrOddCoresAsStaticList() throws Exception {
        for (var mode : List.of(StressMode.PARES, StressMode.IMPARES)) {
            var bridge = new FakeBridge(false);
            var active = new CopyOnWriteArrayList<Set<Integer>>();
            try (var controller = controller(bridge, active, 10, 20)) {
                controller.start(new StressConfiguration(Set.of(5, 0, 3, 2, 1, 4), 40, OptionalInt.empty(), mode)).join();
                Thread.sleep(80);
                controller.stop().join();
            }
            int[] expected = mode == StressMode.PARES ? new int[] {0, 2, 4} : new int[] {1, 3, 5};
            assert bridge.startedCores.size() == 1 : "Static modes start exactly once";
            assert java.util.Arrays.equals(bridge.startedCores.get(0), expected) : mode + " cores";
            assert java.util.Arrays.equals(bridge.loads, new int[] {40, 40, 40});
            assert active.get(0).equals(Arrays.stream(expected).boxed().collect(java.util.stream.Collectors.toSet()));
        }
    }

    private static void circularRotatesOneCoreAtATimeWithoutOverlap() throws Exception {
        var bridge = new FakeBridge(false);
        var active = new CopyOnWriteArrayList<Set<Integer>>();
        var stopped = new AtomicInteger();
        try (var controller = new StressExecutionController(bridge, stats -> { }, failure -> {
            throw new AssertionError(failure);
        }, stopped::incrementAndGet, active::add, () -> 0.1, 10, 30)) {
            controller.start(new StressConfiguration(Set.of(4, 0, 2), 50, OptionalInt.empty(), StressMode.CIRCULAR)).join();
            await(() -> active.size() >= 5, "Expected the rotation to wrap around");
            assert stopped.get() == 0 : "Rotation must not report the run as stopped";
            controller.stop().join();
        }
        assert active.subList(0, 5).equals(List.of(Set.of(0), Set.of(2), Set.of(4), Set.of(0), Set.of(2)))
            : "Unexpected rotation sequence: " + active;
        for (int[] cores : bridge.startedCores) {
            assert cores.length == 1 : "Circular starts one core at a time";
        }
        assert bridge.maxConcurrentRuns == 1 : "Two native runs overlapped";
        assert bridge.startCalls.get() == bridge.stopCalls.get() : "Every start needs a stop";
        assert stopped.get() == 1 : "Only the final stop reports onStopped";
        assert new java.util.HashSet<>(bridge.controlThreads).size() == 1;
    }

    private static void circularWithSingleCoreKeepsRunning() throws Exception {
        var bridge = new FakeBridge(false);
        var active = new CopyOnWriteArrayList<Set<Integer>>();
        var metrics = new ArrayList<StressStats>();
        try (var controller = new StressExecutionController(bridge, metrics::add, failure -> {
            throw new AssertionError(failure);
        }, () -> { }, active::add, () -> 0.1, 10, 20)) {
            controller.start(new StressConfiguration(Set.of(3), 50, OptionalInt.empty(), StressMode.CIRCULAR)).join();
            Thread.sleep(150);
            await(() -> metrics.size() >= 1, "Single core circular must keep measuring");
            controller.stop().join();
        }
        assert bridge.startCalls.get() == 1 && bridge.stopCalls.get() == 1;
        assert active.stream().allMatch(set -> set.equals(Set.of(3)));
    }

    private static void circularStopCancelsRotationAndStopsOnce() throws Exception {
        var bridge = new FakeBridge(false);
        var active = new CopyOnWriteArrayList<Set<Integer>>();
        try (var controller = new StressExecutionController(bridge, stats -> { }, failure -> {
            throw new AssertionError(failure);
        }, () -> { }, active::add, () -> 0.1, 10, 30)) {
            controller.start(new StressConfiguration(Set.of(0, 1), 50, OptionalInt.empty(), StressMode.CIRCULAR)).join();
            await(() -> active.size() >= 2, "Expected one rotation");
            controller.stop().join();
            int starts = bridge.startCalls.get();
            int stops = bridge.stopCalls.get();
            Thread.sleep(120);
            assert bridge.startCalls.get() == starts : "Rotation must be cancelled after stop";
            assert bridge.stopCalls.get() == stops;
            assert starts == stops;
        }
        assert bridge.stopCalls.get() == bridge.startCalls.get() : "close must not stop twice";
    }

    private static StressExecutionController controller(FakeBridge bridge, List<Set<Integer>> active,
                                                        long sampleMillis, long circularMillis) {
        return new StressExecutionController(bridge, stats -> { }, failure -> {
            throw new AssertionError(failure);
        }, () -> { }, active::add, () -> 0.1, sampleMillis, circularMillis);
    }

    private static StressConfiguration configuration(OptionalInt duration) {
        return new StressConfiguration(Set.of(3, 1), 50, duration);
    }

    private static void await(Check check, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!check.value() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assert check.value() : message;
    }

    @FunctionalInterface
    private interface Check { boolean value(); }

    private static final class FakeBridge implements StressExecutionController.Bridge {
        private final boolean inactive;
        private final AtomicInteger startCalls = new AtomicInteger();
        private final AtomicInteger stopCalls = new AtomicInteger();
        private final List<String> controlThreads = new ArrayList<>();
        private final List<int[]> startedCores = new CopyOnWriteArrayList<>();
        private int running;
        private int maxConcurrentRuns;
        private int[] cores;
        private int[] loads;
        private long elapsed;

        FakeBridge(boolean inactive) {
            this.inactive = inactive;
        }

        public boolean start(int[] cores, int[] loads) {
            recordThread();
            this.cores = cores.clone();
            this.loads = loads.clone();
            startedCores.add(cores.clone());
            maxConcurrentRuns = Math.max(maxConcurrentRuns, ++running);
            startCalls.incrementAndGet();
            return true;
        }

        public void stop() {
            recordThread();
            running--;
            stopCalls.incrementAndGet();
        }

        public int snapshot(long[] buffer) {
            recordThread();
            if (inactive) {
                buffer[0] = 0;
                buffer[1] = 0;
                return 2;
            }
            elapsed += 20_000_000L;
            buffer[0] = elapsed;
            int threads = cores.length;
            buffer[1] = threads;
            for (int i = 0; i < threads; i++) {
                buffer[2 + 2 * i] = elapsed / 2;
                buffer[3 + 2 * i] = elapsed;
            }
            return 2 + 2 * threads;
        }

        private void recordThread() {
            controlThreads.add(Thread.currentThread().getName());
        }
    }
}
