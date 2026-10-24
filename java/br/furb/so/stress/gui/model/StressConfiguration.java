package br.furb.so.stress.gui.model;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/** Configuracao imutavel da simulacao apresentada pela interface grafica. */
public record StressConfiguration(
    Set<Integer> selectedCores,
    int loadPercent,
    OptionalInt durationSeconds
) {
    public StressConfiguration {
        selectedCores = Set.copyOf(selectedCores);
        Objects.requireNonNull(durationSeconds, "durationSeconds");
        if (selectedCores.isEmpty() || selectedCores.stream().anyMatch(core -> core < 0)) {
            throw new IllegalArgumentException("Selecione ao menos um nucleo com indice nao negativo");
        }
        if (loadPercent < 1 || loadPercent > 100) {
            throw new IllegalArgumentException("A carga deve estar entre 1 e 100");
        }
        if (durationSeconds.isPresent() && durationSeconds.getAsInt() <= 0) {
            throw new IllegalArgumentException("A duracao deve ser positiva");
        }
    }
}
