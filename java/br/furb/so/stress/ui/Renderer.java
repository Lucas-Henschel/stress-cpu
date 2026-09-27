package br.furb.so.stress.ui;

import br.furb.so.stress.cli.Args;
import br.furb.so.stress.metrics.StressStats;

/**
 * Apresentacao das metricas. Esta interface e o ponto de extensao para a
 * GUI Swing: um SwingRenderer entra aqui sem alterar C, JNI ou Main.
 */
public interface Renderer {
    /** Chamado uma vez, antes da primeira atualizacao. */
    void start(int cpuCount, Args args);

    /** Chamado a cada janela de amostragem (1s). */
    void render(StressStats stats);

    /** Chamado uma vez no encerramento. */
    void close();
}
