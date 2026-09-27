package br.furb.so.stress.ui;

import br.furb.so.stress.cli.Args;
import br.furb.so.stress.metrics.StressStats;

public final class ConsoleRenderer implements Renderer {
    private static final int LARGURA_BARRA = 20;
    private static final String ESC = "\033[";

    private int linhasDesenhadas = 0;
    private int cpuCount;
    private Args args;

    @Override
    public void start(int cpuCount, Args args) {
        this.cpuCount = cpuCount;
        this.args = args;
        System.out.println();
    }

    @Override
    public void render(StressStats s) {
        if (s.threadCount() == 0) {
            return;
        }

        StringBuilder sb = new StringBuilder();

        if (linhasDesenhadas > 0) {
            sb.append(ESC).append(linhasDesenhadas).append('A');
        }

        int linhas = 0;
        linhas += linha(sb, String.format(
            "CPU Stress Test — %d nucleos | %d threads | carga alvo: %d%%",
            cpuCount, args.threads(), args.loadPct())
        );

        String sistema = Double.isNaN(s.systemLoad())
            ? "--"
            : String.format("%.1f%%", s.systemLoad() * 100.0);
        linhas += linha(
            sb, String.format("Tempo: %s  |  Sistema: %s",
            formatarTempo(s.elapsedMillis()), sistema)
        );
        linhas += linha(sb, "");

        for (int i = 0; i < s.threadCount(); i++) {
            linhas += linha(
                sb, String.format(" #%-3d [%s] %5.1f%%  %7.2f Mit/s",
                i,
                barra(s.utilization()[i]),
                s.utilization()[i],
                s.iterationsPerSec()[i] / 1e6)
            );
        }

        linhas += linha(sb, "");
        linhas += linha(sb, "Ctrl+C para encerrar");

        linhasDesenhadas = linhas;
        System.out.print(sb);
        System.out.flush();
    }

    @Override
    public void close() {
        System.out.println();
    }

    private static int linha(StringBuilder sb, String texto) {
        sb.append(texto).append(ESC).append('K').append('\n');
        return 1;
    }

    private static String barra(double pct) {
        int cheios = (int) Math.round(pct / 100.0 * LARGURA_BARRA);
        cheios = Math.max(0, Math.min(LARGURA_BARRA, cheios));
        return "█".repeat(cheios) + "░".repeat(LARGURA_BARRA - cheios);
    }

    private static String formatarTempo(long millis) {
        long total = millis / 1000L;
        return String.format("%02d:%02d", total / 60L, total % 60L);
    }
}
