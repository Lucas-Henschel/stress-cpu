package br.furb.so.stress.gui.model;

import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Sequencia de passos (nucleos + carga) que um modo de estresse executa ao longo do tempo.
 * Os modos estaticos tem um unico passo; os dinamicos trocam de passo a cada intervalo.
 * Nao e thread-safe: o controlador usa a instancia apenas na sua thread de controle.
 */
public final class StressSchedule {
    /** Um estado do motor nativo: quais nucleos rodam e com qual carga. */
    public record Step(List<Integer> cores, int loadPercent) {
        public Step {
            cores = List.copyOf(cores);
        }

        public int[] coreArray() {
            return cores.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    private final StressMode mode;
    private final List<Integer> cores;
    private final int highLoad;
    private final int lowLoad;
    private final RandomGenerator random;
    private int index;
    private boolean lowPhase;
    private Step current;

    public StressSchedule(StressConfiguration configuration, RandomGenerator random) {
        Objects.requireNonNull(configuration, "configuration");
        this.random = Objects.requireNonNull(random, "random");
        this.mode = configuration.mode();
        this.cores = CoreSelection.resolve(configuration.selectedCores(), mode);
        this.highLoad = configuration.loadPercent();
        this.lowLoad = Math.max(1, highLoad / 4);
        this.index = mode == StressMode.ALEATORIO ? random.nextInt(cores.size()) : 0;
        this.current = build();
    }

    public Step current() {
        return current;
    }

    /** Indica se ainda havera troca de passo; falso para modos estaticos ou rampa ja completa. */
    public boolean isDynamic() {
        return switch (mode) {
            case TODOS, PARES, IMPARES -> false;
            case CIRCULAR, ALEATORIO -> cores.size() > 1;
            case ESCALONADO -> index < cores.size() - 1;
            case OSCILANTE -> lowLoad != highLoad;
        };
    }

    /** Avanca para o proximo passo e o retorna; sem efeito quando o modo nao e dinamico. */
    public Step advance() {
        if (!isDynamic()) {
            return current;
        }
        switch (mode) {
            case CIRCULAR -> index = (index + 1) % cores.size();
            case ESCALONADO -> index++;
            case OSCILANTE -> lowPhase = !lowPhase;
            case ALEATORIO -> {
                // Sorteia entre os demais nucleos para garantir troca a cada passo.
                int next = random.nextInt(cores.size() - 1);
                index = next >= index ? next + 1 : next;
            }
            default -> throw new IllegalStateException("Modo estatico nao avanca: " + mode);
        }
        current = build();
        return current;
    }

    private Step build() {
        return switch (mode) {
            case TODOS, PARES, IMPARES -> new Step(cores, highLoad);
            case CIRCULAR, ALEATORIO -> new Step(List.of(cores.get(index)), highLoad);
            case ESCALONADO -> new Step(cores.subList(0, index + 1), highLoad);
            case OSCILANTE -> new Step(cores, lowPhase ? lowLoad : highLoad);
        };
    }
}
