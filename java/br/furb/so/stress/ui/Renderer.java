package br.furb.so.stress.ui;

import br.furb.so.stress.cli.Args;
import br.furb.so.stress.metrics.StressStats;

/**
 * Apresentacao das metricas. Esta interface e o ponto de extensao para a
 * GUI Swing: um SwingRenderer entra aqui sem alterar C, JNI ou Main.
 */
public interface Renderer {
    /**
     * Chamado uma vez, antes da primeira atualizacao.
     *
     * @param afinidadeFixada true se cada thread foi fixada no nucleo de
     *        {@code args.cores()}; false se nao houve --cores ou se o SO nao
     *        oferece afinidade rigida
     */
    void start(int cpuCount, Args args, boolean afinidadeFixada);

    /** Chamado a cada janela de amostragem (1s). */
    void render(StressStats stats);

    /** Chamado uma vez no encerramento. */
    void close();
}
