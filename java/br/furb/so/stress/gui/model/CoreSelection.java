package br.furb.so.stress.gui.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Resolve os nucleos efetivos de uma selecao conforme o modo de estresse. */
public final class CoreSelection {
    private CoreSelection() {
    }

    /**
     * Retorna os indices ordenados e sem duplicatas que o modo mantem.
     *
     * @throws IllegalArgumentException se nenhum nucleo restar
     */
    public static List<Integer> resolve(Set<Integer> selectedCores, StressMode mode) {
        Objects.requireNonNull(selectedCores, "selectedCores");
        Objects.requireNonNull(mode, "mode");
        List<Integer> cores = selectedCores.stream()
            .distinct()
            .sorted()
            .filter(core -> switch (mode) {
                case TODOS, CIRCULAR, ESCALONADO, OSCILANTE, ALEATORIO -> true;
                case PARES -> core % 2 == 0;
                case IMPARES -> core % 2 != 0;
            })
            .toList();
        if (cores.isEmpty()) {
            throw new IllegalArgumentException("Nenhum nucleo selecionado e compativel com o modo " + mode);
        }
        return cores;
    }
}
