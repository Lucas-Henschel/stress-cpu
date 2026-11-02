package br.furb.so.stress.gui.model;

/** Valor por nucleo para exibicao; a fonte simulada nao mede a CPU real. */
public record CoreMetric(int coreIndex, double utilizationPercent) {
    public CoreMetric {
        if (coreIndex < 0) {
            throw new IllegalArgumentException("O indice do nucleo deve ser nao negativo");
        }
        if (!Double.isFinite(utilizationPercent) || utilizationPercent < 0 || utilizationPercent > 100) {
            throw new IllegalArgumentException("A utilizacao deve estar entre 0 e 100");
        }
    }
}
