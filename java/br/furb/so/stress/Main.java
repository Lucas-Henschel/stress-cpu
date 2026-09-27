package br.furb.so.stress;

import br.furb.so.stress.cli.Args;
import br.furb.so.stress.metrics.StressStats;
import br.furb.so.stress.jni.CpuStress;
import br.furb.so.stress.ui.ConsoleRenderer;
import br.furb.so.stress.ui.Renderer;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Main {
    private static final long INTERVALO_MS = 1000L;

    /**
     * Garante que o encerramento (parar o motor nativo e fechar o renderer)
     */
    private static final AtomicBoolean encerrado = new AtomicBoolean(false);

    public static void main(String[] argv) throws InterruptedException {
        int cpus = CpuStress.cpuCount();

        Args args;
        try {
            args = Args.parse(argv, cpus);
        } catch (IllegalArgumentException e) {
            System.err.println("Erro: " + e.getMessage());
            System.err.println();
            System.err.println(Args.USO);
            System.exit(2);
            return;
        }

        CpuStress.enableVtMode();

        Renderer renderer = new ConsoleRenderer();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> encerrar(renderer)));

        CpuStress.start(args.threads(), args.loadPct());
        renderer.start(cpus, args);

        int tamanho = CpuStress.snapshotSize(args.threads());
        long[] anterior = new long[tamanho];
        long[] atual = new long[tamanho];

        CpuStress.snapshot(anterior);

        long fimNanos = args.durationSeconds() > 0
            ? System.nanoTime() + args.durationSeconds() * 1_000_000_000L
            : Long.MAX_VALUE;

        while (System.nanoTime() < fimNanos) {
            Thread.sleep(INTERVALO_MS);
            CpuStress.snapshot(atual);
            renderer.render(StressStats.delta(anterior, atual, cargaDoSistema()));
            System.arraycopy(atual, 0, anterior, 0, tamanho);
        }

        encerrar(renderer);
        System.out.println("Teste concluido.");
    }

    private static void encerrar(Renderer renderer) {
        if (encerrado.compareAndSet(false, true)) {
            CpuStress.stop();
            renderer.close();
        }
    }

    /**
     * Carga global do sistema, em [0, 1], ou NaN se indisponivel.
     */
    private static double cargaDoSistema() {
        OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();

        if (bean instanceof com.sun.management.OperatingSystemMXBean sun) {
            return sun.getCpuLoad();
        }

        return Double.NaN;
    }
}
