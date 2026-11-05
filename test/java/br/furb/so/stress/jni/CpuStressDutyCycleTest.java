package br.furb.so.stress.jni;

import br.furb.so.stress.metrics.StressStats;

/** Runs the real native engine and checks that it follows the requested load. */
public final class CpuStressDutyCycleTest {
    public static void main(String[] args) throws Exception {
        reachesLoadsSmallerThanOldWorkBatch();
        doesNotOvershootMidRangeLoad();
        System.out.println("CpuStressDutyCycleTest passed");
    }

    private static void reachesLoadsSmallerThanOldWorkBatch() throws Exception {
        double measured = measure(1);
        assert measured >= 0.5 && measured <= 2 : "Load 1% measured " + measured + "%";
    }

    // Other processes can only take CPU away from the worker, so the upper
    // bound is strict and the lower bound leaves room for a busy machine.
    private static void doesNotOvershootMidRangeLoad() throws Exception {
        double measured = measure(50);
        assert measured >= 40 && measured <= 51 : "Load 50% measured " + measured + "%";
    }

    private static double measure(int load) throws Exception {
        long[] before = new long[CpuStress.snapshotSize(1)];
        long[] after = new long[before.length];
        CpuStress.start(null, new int[] {load});
        try {
            Thread.sleep(300);
            CpuStress.snapshot(before);
            Thread.sleep(1_500);
            CpuStress.snapshot(after);
        } finally {
            CpuStress.stop();
        }
        return StressStats.delta(before, after, Double.NaN).utilization()[0];
    }
}
