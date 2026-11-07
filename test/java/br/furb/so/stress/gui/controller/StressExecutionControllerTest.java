package br.furb.so.stress.gui.controller;

import br.furb.so.stress.gui.model.StressConfiguration;
import br.furb.so.stress.metrics.StressStats;
import java.util.ArrayList;
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
            startCalls.incrementAndGet();
            return true;
        }

        public void stop() {
            recordThread();
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
            buffer[1] = 2;
            buffer[2] = elapsed / 2;
            buffer[3] = elapsed;
            buffer[4] = elapsed / 2;
            buffer[5] = elapsed;
            return 6;
        }

        private void recordThread() {
            controlThreads.add(Thread.currentThread().getName());
        }
    }
}
