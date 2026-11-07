package br.furb.so.stress.gui.controller;

import br.furb.so.stress.gui.model.StressConfiguration;
import br.furb.so.stress.jni.CpuStress;
import br.furb.so.stress.metrics.StressStats;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

/** Serializes all JNI lifecycle calls away from the Swing event-dispatch thread. */
public final class StressExecutionController implements AutoCloseable {
    private static final long DEFAULT_SAMPLE_MILLIS = 500;

    interface Bridge {
        boolean start(int[] cores, int[] loads);
        void stop();
        int snapshot(long[] buffer);
    }

    public record RunStart(boolean affinityFixed) {
    }

    private final Bridge bridge;
    private final Consumer<StressStats> onStats;
    private final Consumer<Throwable> onFailure;
    private final Runnable onStopped;
    private final DoubleSupplier systemLoad;
    private final ScheduledExecutorService control;
    private final long sampleMillis;
    private ScheduledFuture<?> sampling;
    private long[] previous;
    private long startedNanos;
    private StressConfiguration configuration;
    private boolean running;

    public StressExecutionController(Consumer<StressStats> onStats, Consumer<Throwable> onFailure) {
        this(onStats, onFailure, () -> { });
    }

    public StressExecutionController(Consumer<StressStats> onStats, Consumer<Throwable> onFailure, Runnable onStopped) {
        this(new NativeBridge(), onStats, onFailure, onStopped, StressExecutionController::systemLoad, DEFAULT_SAMPLE_MILLIS);
    }

    StressExecutionController(Bridge bridge, Consumer<StressStats> onStats, Consumer<Throwable> onFailure,
                              DoubleSupplier systemLoad, long sampleMillis) {
        this(bridge, onStats, onFailure, () -> { }, systemLoad, sampleMillis);
    }

    StressExecutionController(Bridge bridge, Consumer<StressStats> onStats, Consumer<Throwable> onFailure,
                              Runnable onStopped, DoubleSupplier systemLoad, long sampleMillis) {
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        this.onStats = Objects.requireNonNull(onStats, "onStats");
        this.onFailure = Objects.requireNonNull(onFailure, "onFailure");
        this.onStopped = Objects.requireNonNull(onStopped, "onStopped");
        this.systemLoad = Objects.requireNonNull(systemLoad, "systemLoad");
        if (sampleMillis < 1) throw new IllegalArgumentException("sampleMillis deve ser positivo");
        this.sampleMillis = sampleMillis;
        control = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "cpu-stress-controller");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletableFuture<RunStart> start(StressConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        var result = new CompletableFuture<RunStart>();
        if (control.isShutdown()) {
            result.completeExceptionally(new IllegalStateException("O controlador foi encerrado"));
            return result;
        }
        control.execute(() -> startOnControlThread(configuration, result));
        return result;
    }

    public CompletableFuture<Void> stop() {
        var result = new CompletableFuture<Void>();
        if (control.isShutdown()) {
            result.complete(null);
            return result;
        }
        control.execute(() -> {
            try {
                stopOnControlThread();
                result.complete(null);
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
                notifyFailure(failure);
            }
        });
        return result;
    }

    private void startOnControlThread(StressConfiguration nextConfiguration, CompletableFuture<RunStart> result) {
        if (running) {
            result.completeExceptionally(new IllegalStateException("O teste ja esta em execucao"));
            return;
        }
        try {
            int[] cores = nextConfiguration.selectedCores().stream().sorted().mapToInt(Integer::intValue).toArray();
            int[] loads = new int[cores.length];
            Arrays.fill(loads, nextConfiguration.loadPercent());
            boolean affinityFixed = bridge.start(cores, loads);
            this.configuration = nextConfiguration;
            this.previous = null;
            this.startedNanos = System.nanoTime();
            this.running = true;
            this.sampling = control.scheduleAtFixedRate(this::sampleOnControlThread, 0, sampleMillis, TimeUnit.MILLISECONDS);
            result.complete(new RunStart(affinityFixed));
        } catch (Throwable failure) {
            result.completeExceptionally(failure);
            notifyFailure(failure);
        }
    }

    private void sampleOnControlThread() {
        if (!running) return;
        try {
            long[] current = new long[2 + 2 * configuration.selectedCores().size()];
            bridge.snapshot(current);
            if (current[1] == 0) return;
            if (previous != null) onStats.accept(StressStats.delta(previous, current, systemLoad.getAsDouble()));
            previous = current;
            if (configuration.durationSeconds().isPresent()
                && System.nanoTime() - startedNanos >= configuration.durationSeconds().getAsInt() * 1_000_000_000L) {
                stopOnControlThread();
            }
        } catch (Throwable failure) {
            try {
                stopOnControlThread();
            } finally {
                notifyFailure(failure);
            }
        }
    }

    private void stopOnControlThread() {
        if (!running) return;
        running = false;
        if (sampling != null) {
            sampling.cancel(false);
            sampling = null;
        }
        bridge.stop();
        onStopped.run();
    }

    @Override
    public synchronized void close() {
        if (control.isShutdown()) return;
        stop().join();
        control.shutdown();
    }

    private void notifyFailure(Throwable failure) {
        try {
            onFailure.accept(failure);
        } catch (RuntimeException ignored) {
            // A presentation callback must not kill the controller thread.
        }
    }

    private static double systemLoad() {
        var bean = ManagementFactory.getOperatingSystemMXBean();
        return bean instanceof com.sun.management.OperatingSystemMXBean sun ? sun.getCpuLoad() : Double.NaN;
    }

    private static final class NativeBridge implements Bridge {
        public boolean start(int[] cores, int[] loads) { return CpuStress.start(cores, loads); }
        public void stop() { CpuStress.stop(); }
        public int snapshot(long[] buffer) { return CpuStress.snapshot(buffer); }
    }
}
